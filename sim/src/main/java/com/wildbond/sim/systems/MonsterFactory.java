package com.wildbond.sim.systems;

import com.artemis.EntityEdit;
import com.artemis.World;
import com.wildbond.data.GameData;
import com.wildbond.data.Monster;
import com.wildbond.sim.components.Brain;
import com.wildbond.sim.components.Collider;
import com.wildbond.sim.components.ElementComponent;
import com.wildbond.sim.components.EntityIdComponent;
import com.wildbond.sim.components.Health;
import com.wildbond.sim.components.MonsterData;
import com.wildbond.sim.components.MonsterState;
import com.wildbond.sim.components.PathComponent;
import com.wildbond.sim.components.Position;
import com.wildbond.sim.components.Skills;
import com.wildbond.sim.components.SpawnOrigin;
import com.wildbond.sim.components.Stats;
import com.wildbond.sim.components.Velocity;
import com.wildbond.sim.events.EntitySpawned;
import com.wildbond.sim.events.EventBus;

/**
 * 몬스터 엔티티 하나를 만드는 곳 — SpawnSystem(사냥터 스폰)과 CommandApplySystem({@code Command.SpawnMonster})이 같은 조립
 * 절차를 쓰도록 한 곳에 모았다. 스탯은 {@code Monster} 표 값을 그대로 쓴다 (v0.5 에는 개체 성장이 없다).
 */
public final class MonsterFactory {

  private final World world;
  private final EntityIndex index;
  private final GameData gameData;
  private final EventBus eventBus;

  public MonsterFactory(World world, EntityIndex index, GameData gameData, EventBus eventBus) {
    this.world = world;
    this.index = index;
    this.gameData = gameData;
    this.eventBus = eventBus;
  }

  public int spawn(float x, float y, int speciesId, int chunkX, int chunkY) {
    Monster species = gameData.monster(speciesId);

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

    Health health = edit.create(Health.class);
    health.current = species.baseHp();
    health.max = species.baseHp();

    Stats stats = edit.create(Stats.class);
    stats.atk = species.baseAtk();
    stats.def = species.baseDef();
    stats.level = species.level();

    edit.create(ElementComponent.class).value = species.element();

    Skills skills = edit.create(Skills.class);
    int[] skillIds = species.skills() == null ? new int[0] : species.skills();
    skills.skillIds = skillIds.clone();
    skills.cooldownRemainingTicks = new int[skillIds.length];

    edit.create(MonsterData.class).speciesId = speciesId;

    Brain brain = edit.create(Brain.class);
    brain.state = MonsterState.IDLE;
    brain.threatStableId = -1;
    brain.speedPxS = species.speedPxS();

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
