package com.wildbond.sim.systems;

import com.artemis.EntityEdit;
import com.artemis.World;
import com.wildbond.data.GameData;
import com.wildbond.data.PalSpecies;
import com.wildbond.sim.Rng;
import com.wildbond.sim.components.Brain;
import com.wildbond.sim.components.Collider;
import com.wildbond.sim.components.ElementComponent;
import com.wildbond.sim.components.EntityIdComponent;
import com.wildbond.sim.components.Health;
import com.wildbond.sim.components.PalData;
import com.wildbond.sim.components.PalState;
import com.wildbond.sim.components.PathComponent;
import com.wildbond.sim.components.Position;
import com.wildbond.sim.components.Skills;
import com.wildbond.sim.components.SpawnOrigin;
import com.wildbond.sim.components.Stats;
import com.wildbond.sim.components.Velocity;
import com.wildbond.sim.events.EntitySpawned;
import com.wildbond.sim.events.EventBus;

/**
 * 팰 엔티티 하나를 만드는 곳 — SpawnSystem(야생 스폰)과 CommandApplySystem({@code Command.SpawnPal}, 테스트·벤치용)이 같은
 * 조립 절차를 쓰도록 한 곳에 모았다.
 *
 * <p>스탯은 {@link PalSpecies} 기본값에 레벨 성장과 개체값을 얹는다 (§3.1 "종 + 레벨, 개체값").
 */
public final class PalFactory {

  private final World world;
  private final EntityIndex index;
  private final GameData gameData;
  private final EventBus eventBus;

  public PalFactory(World world, EntityIndex index, GameData gameData, EventBus eventBus) {
    this.world = world;
    this.index = index;
    this.gameData = gameData;
    this.eventBus = eventBus;
  }

  /** 개체값을 spawn 스트림에서 굴려 팰을 만든다 (야생 스폰 경로). */
  public int spawn(float x, float y, int speciesId, int level, Rng rng, int chunkX, int chunkY) {
    int[] iv = new int[3];
    for (int i = 0; i < iv.length; i++) {
      iv[i] = rng.nextInt(Rng.Stream.SPAWN, PalConstants.MAX_IV + 1);
    }
    return spawn(x, y, speciesId, level, iv, chunkX, chunkY);
  }

  /** 개체값을 직접 지정해 팰을 만든다 (명령·테스트 경로 — 난수를 소비하지 않는다). */
  public int spawn(float x, float y, int speciesId, int level, int[] iv, int chunkX, int chunkY) {
    PalSpecies species = gameData.palSpecies(speciesId);

    int artemisId = world.create();
    EntityEdit edit = world.edit(artemisId);

    Position position = edit.create(Position.class);
    position.x = x;
    position.y = y;

    edit.create(Velocity.class);

    float sizePx = species.footprint() * (float) SimConstants.TILE_SIZE_PX;
    Collider collider = edit.create(Collider.class);
    collider.width = sizePx;
    collider.height = sizePx;
    collider.layer = 0;

    float growth = 1f + PalConstants.STAT_GROWTH_PER_LEVEL * (level - 1);
    int maxHp = (int) StrictMath.floor(species.baseHp() * growth) + iv[0];
    Health health = edit.create(Health.class);
    health.current = maxHp;
    health.max = maxHp;

    Stats stats = edit.create(Stats.class);
    stats.atk = (int) StrictMath.floor(species.baseAtk() * growth) + iv[1];
    stats.def = (int) StrictMath.floor(species.baseDef() * growth) + iv[2];
    stats.level = level;

    ElementComponent element = edit.create(ElementComponent.class);
    element.value = species.element1();

    Skills skills = edit.create(Skills.class);
    skills.skillIds = species.skills().clone();
    skills.cooldownRemainingTicks = new int[skills.skillIds.length];

    PalData palData = edit.create(PalData.class);
    palData.speciesId = speciesId;
    palData.level = level;
    palData.san = PalConstants.PAL_BASE_SAN;
    palData.iv = iv.clone();

    Brain brain = edit.create(Brain.class);
    brain.state = PalState.IDLE;
    brain.threatStableId = -1;
    brain.speedPxS = PalConstants.WANDER_SPEED_PX_S;

    edit.create(PathComponent.class).clear();

    SpawnOrigin origin = edit.create(SpawnOrigin.class);
    origin.chunkX = chunkX;
    origin.chunkY = chunkY;

    int stableId = index.assign(artemisId);
    edit.create(EntityIdComponent.class).value = stableId;

    eventBus.enqueue(new EntitySpawned(stableId, x, y));
    return stableId;
  }
}
