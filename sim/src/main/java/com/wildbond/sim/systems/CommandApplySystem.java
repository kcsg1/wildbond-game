package com.wildbond.sim.systems;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntityEdit;
import com.wildbond.data.Element;
import com.wildbond.sim.Command;
import com.wildbond.sim.Dir8;
import com.wildbond.sim.components.Collider;
import com.wildbond.sim.components.Dead;
import com.wildbond.sim.components.DummyTag;
import com.wildbond.sim.components.ElementComponent;
import com.wildbond.sim.components.EntityIdComponent;
import com.wildbond.sim.components.Health;
import com.wildbond.sim.components.PlayerTag;
import com.wildbond.sim.components.Position;
import com.wildbond.sim.components.Skills;
import com.wildbond.sim.components.Stats;
import com.wildbond.sim.components.Velocity;
import com.wildbond.sim.events.EntitySpawned;
import com.wildbond.sim.events.EventBus;
import java.util.List;

/**
 * §4.1 시스템 1번 — 명령 큐를 Velocity/엔티티 생성으로 바꾼다. UseSkill 은 CombatSystem(시스템 5번, enqueue 로 같은 명령 목록을 따로
 * 받는다)이 처리하므로 여기서는 무시한다.
 */
public final class CommandApplySystem extends BaseSystem {

  private final EntityIndex index;
  private final EventBus eventBus;

  private List<Command> pending = List.of();

  private ComponentMapper<Velocity> mVelocity;
  private ComponentMapper<EntityIdComponent> mEntityId;
  private ComponentMapper<Dead> mDead;

  public CommandApplySystem(EntityIndex index, EventBus eventBus) {
    this.index = index;
    this.eventBus = eventBus;
  }

  /** Sim.step() 이 world.process() 전에 호출한다. */
  public void enqueue(List<Command> commands) {
    this.pending = commands;
  }

  @Override
  protected void initialize() {
    mVelocity = world.getMapper(Velocity.class);
    mEntityId = world.getMapper(EntityIdComponent.class);
    mDead = world.getMapper(Dead.class);
  }

  @Override
  protected void processSystem() {
    for (int i = 0; i < pending.size(); i++) {
      Command command = pending.get(i);
      switch (command) {
        case Command.MoveInput move -> applyMove(move);
        case Command.SpawnPlayer spawn -> spawnPlayer(spawn);
        case Command.SpawnDummy spawn -> spawnDummy(spawn);
        case Command.UseSkill useSkill -> {
          // CombatSystem 이 자기 몫으로 따로 받은 같은 명령 목록에서 처리한다.
        }
      }
    }
    pending = List.of();
  }

  private void applyMove(Command.MoveInput move) {
    int artemisId = index.artemisIdOf(move.entityId());
    if (mDead.has(artemisId)) {
      return;
    }
    Velocity velocity = mVelocity.get(artemisId);
    Dir8 dir = move.dir();
    float speed =
        SimConstants.WALK_SPEED_PX_S * (move.run() ? SimConstants.RUN_SPEED_MULTIPLIER : 1f);
    float scale = dir.diagonal() ? SimConstants.DIAGONAL_SCALE : 1f;
    velocity.vx = dir.dx() * speed * scale;
    velocity.vy = dir.dy() * speed * scale;
  }

  private void spawnPlayer(Command.SpawnPlayer spawn) {
    int artemisId = world.create();
    EntityEdit edit = world.edit(artemisId);

    Position position = edit.create(Position.class);
    position.x = spawn.x();
    position.y = spawn.y();

    edit.create(Velocity.class);

    Collider collider = edit.create(Collider.class);
    collider.width = SimConstants.PLAYER_WIDTH_PX;
    collider.height = SimConstants.PLAYER_HEIGHT_PX;
    collider.layer = 0;

    Health health = edit.create(Health.class);
    health.current = SimConstants.PLAYER_MAX_HP;
    health.max = SimConstants.PLAYER_MAX_HP;

    edit.create(PlayerTag.class);

    Stats stats = edit.create(Stats.class);
    stats.atk = CombatConstants.PLAYER_ATK;
    stats.def = CombatConstants.PLAYER_DEF;
    stats.level = CombatConstants.PLAYER_LEVEL;

    ElementComponent element = edit.create(ElementComponent.class);
    element.value = Element.NONE;

    Skills skills = edit.create(Skills.class);
    skills.skillIds = CombatConstants.PLAYER_SKILL_IDS.clone();
    skills.cooldownRemainingTicks = new int[CombatConstants.PLAYER_SKILL_IDS.length];

    int stableId = index.assign(artemisId);
    EntityIdComponent idComponent = edit.create(EntityIdComponent.class);
    idComponent.value = stableId;

    eventBus.enqueue(new EntitySpawned(stableId, spawn.x(), spawn.y()));
  }

  /** 단계 6 임시 허수아비(EntityKind.DUMMY) — 스킬을 모르므로 공격하지 않는다. */
  private void spawnDummy(Command.SpawnDummy spawn) {
    int artemisId = world.create();
    EntityEdit edit = world.edit(artemisId);

    Position position = edit.create(Position.class);
    position.x = spawn.x();
    position.y = spawn.y();

    Collider collider = edit.create(Collider.class);
    collider.width = SimConstants.PLAYER_WIDTH_PX;
    collider.height = SimConstants.PLAYER_HEIGHT_PX;
    collider.layer = 0;

    Health health = edit.create(Health.class);
    health.current = spawn.maxHp();
    health.max = spawn.maxHp();

    Stats stats = edit.create(Stats.class);
    stats.atk = spawn.atk();
    stats.def = spawn.def();
    stats.level = spawn.level();

    ElementComponent element = edit.create(ElementComponent.class);
    element.value = spawn.element();

    edit.create(DummyTag.class);

    int stableId = index.assign(artemisId);
    EntityIdComponent idComponent = edit.create(EntityIdComponent.class);
    idComponent.value = stableId;

    eventBus.enqueue(new EntitySpawned(stableId, spawn.x(), spawn.y()));
  }
}
