package com.wildbond.sim.systems;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntityEdit;
import com.wildbond.data.GameData;
import com.wildbond.data.Item;
import com.wildbond.data.PalSpecies;
import com.wildbond.sim.Angle;
import com.wildbond.sim.Command;
import com.wildbond.sim.Rng;
import com.wildbond.sim.SimView;
import com.wildbond.sim.Ticks;
import com.wildbond.sim.components.Brain;
import com.wildbond.sim.components.Collider;
import com.wildbond.sim.components.Dead;
import com.wildbond.sim.components.EntityIdComponent;
import com.wildbond.sim.components.Health;
import com.wildbond.sim.components.Owner;
import com.wildbond.sim.components.PalData;
import com.wildbond.sim.components.PalState;
import com.wildbond.sim.components.Party;
import com.wildbond.sim.components.PathComponent;
import com.wildbond.sim.components.Position;
import com.wildbond.sim.components.Sphere;
import com.wildbond.sim.components.Stats;
import com.wildbond.sim.events.EventBus;
import com.wildbond.sim.events.PalCaptureFailed;
import com.wildbond.sim.events.PalCaptured;
import java.util.Arrays;
import java.util.List;

/**
 * §4.1 시스템 6번 — 포획구 투척과 포획 판정 (docs/architecture.md §3.2).
 *
 * <p>포획구는 "가상 높이 z 를 가진 포물선 투사체"다. 지면 좌표({@code Position})는 직선으로 나아가고 z 는 포물선을 그리며, z=0 으로 낙하한 타일이
 * 야생 팰의 AABB 와 겹치면 §3.2 공식을 굴린다. 흔들림 횟수(1~3)는 결과와 무관하게 여기서 정하고 클라이언트는 연출만 한다.
 *
 * <p>전투 투사체와 마찬가지로 MovementSystem 을 타지 않는다 — 지면 이동·낙하·판정을 이 시스템이 전담한다.
 */
public final class CaptureSystem extends BaseSystem {

  private final EntityIndex index;
  private final GameData gameData;
  private final EventBus eventBus;
  private final Rng rng;
  private final SimClock clock;

  private List<Command> pending = List.of();

  private ComponentMapper<Position> mPosition;
  private ComponentMapper<Collider> mCollider;
  private ComponentMapper<Health> mHealth;
  private ComponentMapper<Stats> mStats;
  private ComponentMapper<PalData> mPal;
  private ComponentMapper<Owner> mOwner;
  private ComponentMapper<Party> mParty;
  private ComponentMapper<Brain> mBrain;
  private ComponentMapper<PathComponent> mPath;
  private ComponentMapper<Dead> mDead;
  private ComponentMapper<Sphere> mSphere;
  private ComponentMapper<EntityIdComponent> mEntityId;

  private int[] sphereIds = new int[4];
  private int sphereCount;

  public CaptureSystem(
      EntityIndex index, GameData gameData, EventBus eventBus, Rng rng, SimClock clock) {
    this.index = index;
    this.gameData = gameData;
    this.eventBus = eventBus;
    this.rng = rng;
    this.clock = clock;
  }

  /** Sim.step() 이 world.process() 전에 호출한다 (CombatSystem 과 같은 관례). */
  public void enqueue(List<Command> commands) {
    this.pending = commands;
  }

  @Override
  protected void initialize() {
    mPosition = world.getMapper(Position.class);
    mCollider = world.getMapper(Collider.class);
    mHealth = world.getMapper(Health.class);
    mStats = world.getMapper(Stats.class);
    mPal = world.getMapper(PalData.class);
    mOwner = world.getMapper(Owner.class);
    mParty = world.getMapper(Party.class);
    mBrain = world.getMapper(Brain.class);
    mPath = world.getMapper(PathComponent.class);
    mDead = world.getMapper(Dead.class);
    mSphere = world.getMapper(Sphere.class);
    mEntityId = world.getMapper(EntityIdComponent.class);
  }

  @Override
  protected void processSystem() {
    moveSpheres();
    for (int i = 0; i < pending.size(); i++) {
      if (pending.get(i) instanceof Command.ThrowSphere throwSphere) {
        handleThrow(throwSphere);
      }
    }
    pending = List.of();
  }

  // --------------------------------------------------------------- 투척

  private void handleThrow(Command.ThrowSphere command) {
    int throwerArtemisId = index.artemisIdOrMissing(command.entityId());
    if (throwerArtemisId < 0 || mDead.has(throwerArtemisId) || !mPosition.has(throwerArtemisId)) {
      return;
    }
    Item item = gameData.item(command.sphereItemId());
    if (item.sphereMultiplier() == null) {
      return; // 포획구가 아닌 아이템 — 무시한다.
    }
    float distancePx = Math.max(1f, command.distancePx());

    Position thrower = mPosition.get(throwerArtemisId);
    float theta = Angle.toRadians(command.aimAngle());

    int artemisId = world.create();
    EntityEdit edit = world.edit(artemisId);

    Position position = edit.create(Position.class);
    position.x = thrower.x;
    position.y = thrower.y;

    Sphere sphere = edit.create(Sphere.class);
    sphere.throwerStableId = command.entityId();
    sphere.sphereItemId = command.sphereItemId();
    sphere.dirX = (float) StrictMath.cos(theta);
    sphere.dirY = (float) StrictMath.sin(theta);
    sphere.speedPxS = CaptureConstants.SPHERE_SPEED_PX_S;
    sphere.traveledPx = 0f;
    sphere.totalPx = distancePx;
    sphere.apexPx = CaptureConstants.SPHERE_APEX_PX;
    sphere.z = 0f;

    int stableId = index.assign(artemisId);
    edit.create(EntityIdComponent.class).value = stableId;

    addSphere(artemisId);
  }

  // --------------------------------------------------------------- 비행·낙하

  private void moveSpheres() {
    float dt = Ticks.DT_SECONDS;
    for (int i = 0; i < sphereCount; i++) {
      int artemisId = sphereIds[i];
      Sphere sphere = mSphere.get(artemisId);
      Position position = mPosition.get(artemisId);

      float step = sphere.speedPxS * dt;
      float remaining = sphere.totalPx - sphere.traveledPx;
      if (step >= remaining) {
        position.x += sphere.dirX * remaining;
        position.y += sphere.dirY * remaining;
        sphere.traveledPx = sphere.totalPx;
        sphere.z = 0f;
        resolveLanding(sphere, position);
        removeSphere(artemisId, i);
        i--;
        continue;
      }
      position.x += sphere.dirX * step;
      position.y += sphere.dirY * step;
      sphere.traveledPx += step;

      // 포물선: 진행 비율 t 에 대해 z = 4 × apex × t × (1 - t) — 시작·착지에서 0, 중간에서 apex.
      float t = sphere.traveledPx / sphere.totalPx;
      sphere.z = 4f * sphere.apexPx * t * (1f - t);
    }
  }

  /** 낙하 타일과 야생 팰 AABB 가 겹치면 §3.2 공식을 굴린다. */
  private void resolveLanding(Sphere sphere, Position landing) {
    int tileX = AiContext.tileOf(landing.x);
    int tileY = AiContext.tileOf(landing.y);
    float tileMinX = tileX * (float) SimConstants.TILE_SIZE_PX;
    float tileMinY = tileY * (float) SimConstants.TILE_SIZE_PX;
    float tileMaxX = tileMinX + SimConstants.TILE_SIZE_PX;
    float tileMaxY = tileMinY + SimConstants.TILE_SIZE_PX;

    int targetArtemisId = -1;
    int targetStableId = -1;
    int n = index.size();
    for (int i = 0; i < n; i++) {
      int candidate = index.artemisIdAt(i);
      if (!isCapturable(candidate)) {
        continue;
      }
      Position position = mPosition.get(candidate);
      Collider collider = mCollider.get(candidate);
      float halfW = collider.width * 0.5f;
      float halfH = collider.height * 0.5f;
      boolean overlaps =
          position.x + halfW > tileMinX
              && position.x - halfW < tileMaxX
              && position.y + halfH > tileMinY
              && position.y - halfH < tileMaxY;
      if (overlaps) {
        targetArtemisId = candidate;
        targetStableId = index.stableIdAt(i);
        break; // EntityId 오름차순 첫 번째 — 결정적 (§4.3)
      }
    }
    if (targetArtemisId < 0) {
      return;
    }

    int shakes =
        CaptureConstants.MIN_SHAKES
            + rng.nextInt(
                Rng.Stream.LOOT, CaptureConstants.MAX_SHAKES - CaptureConstants.MIN_SHAKES + 1);
    float chance = captureChance(sphere, targetArtemisId);
    boolean success = rng.nextFloat(Rng.Stream.LOOT) < chance;

    if (!success) {
      failCapture(sphere, targetArtemisId, targetStableId, shakes, landing);
      return;
    }
    int slot = firstFreePartySlot(sphere.throwerStableId);
    if (slot < 0) {
      failCapture(sphere, targetArtemisId, targetStableId, shakes, landing); // 파티가 꽉 찼다.
      return;
    }
    succeedCapture(sphere, targetArtemisId, targetStableId, slot, shakes, landing);
  }

  private boolean isCapturable(int artemisId) {
    return mPal.has(artemisId)
        && !mOwner.has(artemisId)
        && !mDead.has(artemisId)
        && mPosition.has(artemisId)
        && mCollider.has(artemisId);
  }

  private float captureChance(Sphere sphere, int targetArtemisId) {
    PalData pal = mPal.get(targetArtemisId);
    PalSpecies species = gameData.palSpecies(pal.speciesId);
    Health health = mHealth.get(targetArtemisId);
    Item item = gameData.item(sphere.sphereItemId);

    int throwerArtemisId = index.artemisIdOrMissing(sphere.throwerStableId);
    int throwerLevel =
        throwerArtemisId >= 0 && mStats.has(throwerArtemisId)
            ? mStats.get(throwerArtemisId).level
            : 1;

    return CaptureFormula.chance(
        species.captureRate(),
        item.sphereMultiplier(),
        health.current,
        health.max,
        CaptureConstants.STATUS_MULTIPLIER_NONE,
        throwerLevel,
        pal.level,
        CaptureConstants.TECH_BONUS_NONE);
  }

  private void succeedCapture(
      Sphere sphere, int targetArtemisId, int targetStableId, int slot, int shakes, Position at) {
    EntityEdit edit = world.edit(targetArtemisId);
    edit.create(Owner.class).ownerStableId = sphere.throwerStableId;
    edit.create(Party.class).slot = slot;

    if (mBrain.has(targetArtemisId)) {
      Brain brain = mBrain.get(targetArtemisId);
      brain.state = PalState.FOLLOW;
      brain.threatStableId = -1;
      brain.waitTicks = 0;
      brain.stateTicks = 0;
      brain.nextSenseTick = clock.tick();
      brain.speedPxS = PalConstants.FOLLOW_SPEED_PX_S;
    }
    if (mPath.has(targetArtemisId)) {
      mPath.get(targetArtemisId).clear();
    }
    eventBus.enqueue(
        new PalCaptured(targetStableId, sphere.throwerStableId, slot, shakes, at.x, at.y));
  }

  private void failCapture(
      Sphere sphere, int targetArtemisId, int targetStableId, int shakes, Position at) {
    if (mBrain.has(targetArtemisId)) {
      Brain brain = mBrain.get(targetArtemisId);
      brain.state = PalState.COMBAT;
      brain.threatStableId = sphere.throwerStableId;
      brain.nextSenseTick = clock.tick() + PalConstants.SENSE_INTERVAL_TICKS;
    }
    eventBus.enqueue(
        new PalCaptureFailed(targetStableId, sphere.throwerStableId, shakes, at.x, at.y));
  }

  /** 주인의 파티에서 비어 있는 가장 앞 슬롯. 꽉 찼으면 -1. */
  private int firstFreePartySlot(int ownerStableId) {
    int usedMask = 0;
    int n = index.size();
    for (int i = 0; i < n; i++) {
      int artemisId = index.artemisIdAt(i);
      if (!mParty.has(artemisId) || !mOwner.has(artemisId)) {
        continue;
      }
      if (mOwner.get(artemisId).ownerStableId != ownerStableId) {
        continue;
      }
      int slot = mParty.get(artemisId).slot;
      if (slot >= 0 && slot < SimView.PARTY_SLOTS) {
        usedMask |= 1 << slot;
      }
    }
    for (int slot = 0; slot < SimView.PARTY_SLOTS; slot++) {
      if ((usedMask & (1 << slot)) == 0) {
        return slot;
      }
    }
    return -1;
  }

  private void addSphere(int artemisId) {
    if (sphereCount == sphereIds.length) {
      sphereIds = Arrays.copyOf(sphereIds, sphereCount * 2);
    }
    sphereIds[sphereCount++] = artemisId;
  }

  private void removeSphere(int artemisId, int slot) {
    int stableId = mEntityId.get(artemisId).value;
    world.delete(artemisId);
    index.remove(stableId);
    sphereCount--;
    sphereIds[slot] = sphereIds[sphereCount];
  }
}
