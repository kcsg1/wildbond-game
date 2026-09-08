package com.wildbond.sim.systems;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.wildbond.data.GameData;
import com.wildbond.data.HitShape;
import com.wildbond.data.Skill;
import com.wildbond.data.Temperament;
import com.wildbond.sim.Angle;
import com.wildbond.sim.Rng;
import com.wildbond.sim.TileMap;
import com.wildbond.sim.components.Brain;
import com.wildbond.sim.components.CombatMemory;
import com.wildbond.sim.components.Dead;
import com.wildbond.sim.components.Health;
import com.wildbond.sim.components.Owner;
import com.wildbond.sim.components.PalData;
import com.wildbond.sim.components.PalState;
import com.wildbond.sim.components.PathComponent;
import com.wildbond.sim.components.PlayerTag;
import com.wildbond.sim.components.Position;
import com.wildbond.sim.components.Skills;
import com.wildbond.sim.components.Velocity;
import com.wildbond.sim.systems.bt.BtStatus;

/**
 * BT 노드가 보는 세상 — 현재 팰 하나를 가리키고, 조건/동작이 필요한 질의와 조작을 모두 메서드로 제공한다 (docs/architecture.md §9.1, §9.3).
 *
 * <p>인스턴스는 AiSystem 이 하나만 만들어 엔티티마다 {@link #bind} 로 다시 겨눈다 — BT 노드도 트리도 매 틱 새로 만들지 않으므로 AI 경로에서 할당이
 * 없다(§4.3).
 */
public final class AiContext {

  private final EntityIndex index;
  private final TileMap tileMap;
  private final GameData gameData;
  private final Rng rng;
  private final SimClock clock;
  private final Pathfinder pathfinder;
  private final CombatSystem combatSystem;

  private final ComponentMapper<Position> mPosition;
  private final ComponentMapper<Velocity> mVelocity;
  private final ComponentMapper<Health> mHealth;
  private final ComponentMapper<Brain> mBrain;
  private final ComponentMapper<PathComponent> mPath;
  private final ComponentMapper<PalData> mPal;
  private final ComponentMapper<Owner> mOwner;
  private final ComponentMapper<Skills> mSkills;
  private final ComponentMapper<Dead> mDead;
  private final ComponentMapper<PlayerTag> mPlayer;
  private final ComponentMapper<CombatMemory> mCombatMemory;

  private int artemisId;
  private int stableId;

  public AiContext(
      World world,
      EntityIndex index,
      TileMap tileMap,
      GameData gameData,
      Rng rng,
      SimClock clock,
      Pathfinder pathfinder,
      CombatSystem combatSystem) {
    this.index = index;
    this.tileMap = tileMap;
    this.gameData = gameData;
    this.rng = rng;
    this.clock = clock;
    this.pathfinder = pathfinder;
    this.combatSystem = combatSystem;

    this.mPosition = world.getMapper(Position.class);
    this.mVelocity = world.getMapper(Velocity.class);
    this.mHealth = world.getMapper(Health.class);
    this.mBrain = world.getMapper(Brain.class);
    this.mPath = world.getMapper(PathComponent.class);
    this.mPal = world.getMapper(PalData.class);
    this.mOwner = world.getMapper(Owner.class);
    this.mSkills = world.getMapper(Skills.class);
    this.mDead = world.getMapper(Dead.class);
    this.mPlayer = world.getMapper(PlayerTag.class);
    this.mCombatMemory = world.getMapper(CombatMemory.class);
  }

  void bind(int artemisEntityId, int entityStableId) {
    this.artemisId = artemisEntityId;
    this.stableId = entityStableId;
  }

  boolean isOwned() {
    return mOwner.has(artemisId);
  }

  Brain brain() {
    return mBrain.get(artemisId);
  }

  // ------------------------------------------------------------------ 감지

  /** §9.1 "0.25s 간격" 감지 — 위협을 갱신한다. */
  void sense() {
    Brain brain = brain();
    if (isOwned()) {
      brain.threatStableId = ownerTarget();
      return;
    }
    if (isThreatStillValid(brain.threatStableId, PalConstants.CHASE_GIVE_UP_TILES)) {
      return; // 추격 중인 대상은 시야가 잠깐 끊겨도 유지한다.
    }
    brain.threatStableId = findNearestVisibleThreat();
  }

  /** 주인이 최근에 때린 대상 (docs/m0-prompts.md 단계7 "주인이 공격한 대상 Combat"). */
  private int ownerTarget() {
    int ownerArtemisId = index.artemisIdOrMissing(mOwner.get(artemisId).ownerStableId);
    if (ownerArtemisId < 0 || !mCombatMemory.has(ownerArtemisId)) {
      return -1;
    }
    CombatMemory memory = mCombatMemory.get(ownerArtemisId);
    if (memory.lastTargetStableId < 0
        || clock.tick() - memory.lastTargetTick > PalConstants.OWNER_TARGET_MEMORY_TICKS) {
      return -1;
    }
    return isThreatStillValid(memory.lastTargetStableId, PalConstants.CHASE_GIVE_UP_TILES)
        ? memory.lastTargetStableId
        : -1;
  }

  private boolean isThreatStillValid(int threatStableId, int maxTiles) {
    if (threatStableId < 0) {
      return false;
    }
    int threatArtemisId = index.artemisIdOrMissing(threatStableId);
    if (threatArtemisId < 0 || mDead.has(threatArtemisId) || !mPosition.has(threatArtemisId)) {
      return false;
    }
    Position self = mPosition.get(artemisId);
    Position other = mPosition.get(threatArtemisId);
    float maxPx = maxTiles * (float) SimConstants.TILE_SIZE_PX;
    return distanceSquared(self, other) <= maxPx * maxPx;
  }

  /** 야생 팰의 위협 = 플레이어와 그 파티 팰. EntityId 오름차순으로 훑어 가장 가까운 하나를 고른다(§4.3). */
  private int findNearestVisibleThreat() {
    Position self = mPosition.get(artemisId);
    float sightPx = PalConstants.SIGHT_RADIUS_TILES * (float) SimConstants.TILE_SIZE_PX;
    float bestDistanceSquared = sightPx * sightPx;
    int best = -1;

    int n = index.size();
    for (int i = 0; i < n; i++) {
      int otherArtemisId = index.artemisIdAt(i);
      if (!isHostileToWild(otherArtemisId) || mDead.has(otherArtemisId)) {
        continue;
      }
      Position other = mPosition.get(otherArtemisId);
      float distanceSquared = distanceSquared(self, other);
      if (distanceSquared > bestDistanceSquared) {
        continue;
      }
      if (!Sensing.hasLineOfSight(
          tileMap, tileOf(self.x), tileOf(self.y), tileOf(other.x), tileOf(other.y))) {
        continue;
      }
      bestDistanceSquared = distanceSquared;
      best = index.stableIdAt(i);
    }
    return best;
  }

  private boolean isHostileToWild(int otherArtemisId) {
    if (!mPosition.has(otherArtemisId)) {
      return false;
    }
    return mPlayer.has(otherArtemisId) || (mPal.has(otherArtemisId) && mOwner.has(otherArtemisId));
  }

  // ------------------------------------------------------------ BT 조건

  /** §9.1 Flee: HP < 20% && temperament == timid. */
  boolean shouldFlee() {
    if (isOwned() || !mHealth.has(artemisId)) {
      return false;
    }
    Health health = mHealth.get(artemisId);
    if (health.max <= 0 || health.current > health.max * PalConstants.FLEE_HP_RATIO) {
      return false;
    }
    return temperament() == Temperament.TIMID;
  }

  boolean hasThreat() {
    return brain().threatStableId >= 0 && index.artemisIdOrMissing(brain().threatStableId) >= 0;
  }

  private Temperament temperament() {
    return gameData.palSpecies(mPal.get(artemisId).speciesId).temperament();
  }

  // ------------------------------------------------------------ BT 동작

  /** 위협 반대 방향으로 직선 도주 — 경로 탐색을 쓰지 않는다(§9.1 Flee 는 즉시성이 중요하다). */
  BtStatus flee() {
    int threatArtemisId = threatArtemisId();
    if (threatArtemisId < 0) {
      return BtStatus.FAILURE;
    }
    Brain brain = brain();
    brain.state = PalState.FLEE;
    brain.speedPxS = PalConstants.FLEE_SPEED_PX_S;
    clearPath();

    Position self = mPosition.get(artemisId);
    Position threat = mPosition.get(threatArtemisId);
    steer(self.x - threat.x, self.y - threat.y, PalConstants.FLEE_SPEED_PX_S);
    return BtStatus.RUNNING;
  }

  /** 사거리 안이고 쿨다운이 끝난 스킬이 있으면 쓴다 (§9.1 UseSkill(offCooldown, inRange)). */
  BtStatus attackThreat() {
    int threatArtemisId = threatArtemisId();
    if (threatArtemisId < 0 || !mSkills.has(artemisId)) {
      return BtStatus.FAILURE;
    }
    Position self = mPosition.get(artemisId);
    Position threat = mPosition.get(threatArtemisId);
    float dx = threat.x - self.x;
    float dy = threat.y - self.y;
    float distanceSquared = dx * dx + dy * dy;

    Skills skills = mSkills.get(artemisId);
    for (int slot = 0; slot < skills.skillIds.length; slot++) {
      if (skills.cooldownRemainingTicks[slot] > 0) {
        continue;
      }
      Skill skill = gameData.skill(skills.skillIds[slot]);
      float range = attackRangePx(skill);
      if (distanceSquared > range * range) {
        continue;
      }
      Brain brain = brain();
      brain.state = PalState.COMBAT;
      clearPath();
      stop();
      combatSystem.requestSkill(
          stableId, skill.id(), Angle.fromRadians((float) StrictMath.atan2(dy, dx)));
      return BtStatus.SUCCESS;
    }
    return BtStatus.FAILURE;
  }

  /** 실제로 맞을 수 있는 거리 — 판정 폭까지 감안하고, 여유를 조금 남겨 확실히 닿게 한다. */
  private static float attackRangePx(Skill skill) {
    float reach =
        skill.hitShape() == HitShape.PROJECTILE
            ? skill.rangePx()
            : Math.max(skill.rangePx(), skill.hitW());
    return Math.max(PalConstants.MIN_ATTACK_RANGE_PX, reach) * 0.85f;
  }

  BtStatus chaseThreat() {
    int threatArtemisId = threatArtemisId();
    if (threatArtemisId < 0) {
      return BtStatus.FAILURE;
    }
    Brain brain = brain();
    brain.state = PalState.COMBAT;
    brain.speedPxS = PalConstants.CHASE_SPEED_PX_S;

    Position self = mPosition.get(artemisId);
    Position threat = mPosition.get(threatArtemisId);
    if (Sensing.hasLineOfSight(
        tileMap, tileOf(self.x), tileOf(self.y), tileOf(threat.x), tileOf(threat.y))) {
      clearPath();
      steer(threat.x - self.x, threat.y - self.y, PalConstants.CHASE_SPEED_PX_S);
      return BtStatus.RUNNING;
    }
    followOrRequestPath(threat.x, threat.y);
    return BtStatus.RUNNING;
  }

  /** §9.1 Idle 의 앞부분 — 12타일 안의 walkable 타일로 배회한다. 배회가 끝나면 SUCCESS 로 Wait 에 넘긴다. */
  BtStatus wander() {
    Brain brain = brain();
    if (brain.state == PalState.WAIT) {
      return BtStatus.SUCCESS;
    }
    if (brain.state != PalState.WANDER) {
      if (!startWander(brain)) {
        beginWait(brain);
        return BtStatus.SUCCESS;
      }
      return BtStatus.RUNNING;
    }
    brain.stateTicks++;
    PathComponent path = mPath.get(artemisId);
    if (path.length == 0 || brain.stateTicks > PalConstants.WANDER_MAX_TICKS) {
      beginWait(brain);
      return BtStatus.SUCCESS;
    }
    return BtStatus.RUNNING;
  }

  BtStatus waitIdle() {
    Brain brain = brain();
    stop();
    if (brain.waitTicks > 0) {
      brain.waitTicks--;
      return BtStatus.RUNNING;
    }
    brain.state = PalState.IDLE;
    return BtStatus.SUCCESS;
  }

  /** docs/m0-prompts.md 단계7 "FollowOwner(3~6타일, 떨어지면 경로 요청)". */
  BtStatus followOwner() {
    int ownerArtemisId = index.artemisIdOrMissing(mOwner.get(artemisId).ownerStableId);
    if (ownerArtemisId < 0 || !mPosition.has(ownerArtemisId)) {
      return BtStatus.FAILURE;
    }
    Brain brain = brain();
    brain.state = PalState.FOLLOW;
    brain.speedPxS = PalConstants.FOLLOW_SPEED_PX_S;

    Position self = mPosition.get(artemisId);
    Position owner = mPosition.get(ownerArtemisId);
    float distancePx = (float) StrictMath.sqrt(distanceSquared(self, owner));
    float minPx = PalConstants.FOLLOW_MIN_TILES * (float) SimConstants.TILE_SIZE_PX;
    float maxPx = PalConstants.FOLLOW_MAX_TILES * (float) SimConstants.TILE_SIZE_PX;

    if (distancePx <= minPx) {
      clearPath();
      stop();
      return BtStatus.SUCCESS;
    }
    if (distancePx <= maxPx
        && Sensing.hasLineOfSight(
            tileMap, tileOf(self.x), tileOf(self.y), tileOf(owner.x), tileOf(owner.y))) {
      clearPath();
      steer(owner.x - self.x, owner.y - self.y, PalConstants.FOLLOW_SPEED_PX_S);
      return BtStatus.RUNNING;
    }
    followOrRequestPath(owner.x, owner.y);
    return BtStatus.RUNNING;
  }

  // ------------------------------------------------------------------ 보조

  private boolean startWander(Brain brain) {
    Position self = mPosition.get(artemisId);
    int selfTx = tileOf(self.x);
    int selfTy = tileOf(self.y);
    int radius = PalConstants.WANDER_RADIUS_TILES;

    for (int attempt = 0; attempt < PalConstants.WANDER_TARGET_ATTEMPTS; attempt++) {
      int targetTx = selfTx - radius + rng.nextInt(Rng.Stream.SPAWN, radius * 2 + 1);
      int targetTy = selfTy - radius + rng.nextInt(Rng.Stream.SPAWN, radius * 2 + 1);
      if (targetTx == selfTx && targetTy == selfTy) {
        continue;
      }
      if (!pathfinder.canRequest()) {
        return false;
      }
      if (pathfinder.requestPath(selfTx, selfTy, targetTx, targetTy)) {
        copyPathFromPathfinder();
        brain.state = PalState.WANDER;
        brain.stateTicks = 0;
        brain.speedPxS = PalConstants.WANDER_SPEED_PX_S;
        return true;
      }
    }
    return false;
  }

  private void beginWait(Brain brain) {
    brain.state = PalState.WAIT;
    brain.stateTicks = 0;
    int span = PalConstants.WAIT_MAX_TICKS - PalConstants.WAIT_MIN_TICKS + 1;
    brain.waitTicks = PalConstants.WAIT_MIN_TICKS + rng.nextInt(Rng.Stream.SPAWN, span);
    clearPath();
    stop();
  }

  /** 이미 경로가 있으면 그대로 따라가고, 없으면(그리고 재요청 간격·틱 상한이 허용하면) 새로 요청한다. */
  private void followOrRequestPath(float targetX, float targetY) {
    PathComponent path = mPath.get(artemisId);
    if (path.length > 0 && path.index < path.length) {
      return; // PathFollowSystem 이 이어서 처리한다.
    }
    Brain brain = brain();
    if (clock.tick() < brain.nextRepathTick) {
      stop();
      return;
    }
    brain.nextRepathTick = clock.tick() + PalConstants.REPATH_INTERVAL_TICKS;

    Position self = mPosition.get(artemisId);
    if (pathfinder.requestPath(tileOf(self.x), tileOf(self.y), tileOf(targetX), tileOf(targetY))) {
      copyPathFromPathfinder();
    } else {
      stop();
    }
  }

  private void copyPathFromPathfinder() {
    PathComponent path = mPath.get(artemisId);
    int length = Math.min(pathfinder.pathLength(), PathComponent.MAX_TILES);
    for (int i = 0; i < length; i++) {
      path.tileX[i] = pathfinder.pathTileX(i);
      path.tileY[i] = pathfinder.pathTileY(i);
    }
    path.length = length;
    path.index = 0;
  }

  private void clearPath() {
    mPath.get(artemisId).clear();
  }

  private void stop() {
    Velocity velocity = mVelocity.get(artemisId);
    velocity.vx = 0f;
    velocity.vy = 0f;
  }

  private void steer(float dx, float dy, float speedPxS) {
    Velocity velocity = mVelocity.get(artemisId);
    float length = (float) StrictMath.sqrt(dx * dx + dy * dy);
    if (length < 0.0001f) {
      velocity.vx = 0f;
      velocity.vy = 0f;
      return;
    }
    velocity.vx = dx / length * speedPxS;
    velocity.vy = dy / length * speedPxS;
  }

  private int threatArtemisId() {
    int threatStableId = brain().threatStableId;
    if (threatStableId < 0) {
      return -1;
    }
    int threatArtemisId = index.artemisIdOrMissing(threatStableId);
    if (threatArtemisId < 0 || !mPosition.has(threatArtemisId) || mDead.has(threatArtemisId)) {
      brain().threatStableId = -1;
      return -1;
    }
    return threatArtemisId;
  }

  private static float distanceSquared(Position a, Position b) {
    float dx = b.x - a.x;
    float dy = b.y - a.y;
    return dx * dx + dy * dy;
  }

  static int tileOf(float worldPx) {
    return (int) StrictMath.floor(worldPx / SimConstants.TILE_SIZE_PX);
  }
}
