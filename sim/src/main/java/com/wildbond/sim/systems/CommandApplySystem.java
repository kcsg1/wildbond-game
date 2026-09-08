package com.wildbond.sim.systems;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntityEdit;
import com.wildbond.data.Element;
import com.wildbond.data.GameData;
import com.wildbond.data.chunk.ChunkFormat;
import com.wildbond.sim.Command;
import com.wildbond.sim.Dir8;
import com.wildbond.sim.components.Collider;
import com.wildbond.sim.components.Dead;
import com.wildbond.sim.components.DummyTag;
import com.wildbond.sim.components.ElementComponent;
import com.wildbond.sim.components.EntityIdComponent;
import com.wildbond.sim.components.Experience;
import com.wildbond.sim.components.Health;
import com.wildbond.sim.components.Inventory;
import com.wildbond.sim.components.Mana;
import com.wildbond.sim.components.PlayerTag;
import com.wildbond.sim.components.Position;
import com.wildbond.sim.components.Skills;
import com.wildbond.sim.components.Stats;
import com.wildbond.sim.components.Velocity;
import com.wildbond.sim.components.Wallet;
import com.wildbond.sim.events.EntitySpawned;
import com.wildbond.sim.events.EventBus;
import java.util.List;

/**
 * §4.1 시스템 1번 — 명령 큐를 Velocity/엔티티 생성으로 바꾼다. UseSkill 은 CombatSystem(5번)이 같은 명령 목록을 따로 받아 처리하므로
 * 여기서는 무시한다.
 *
 * <p>존재하지 않는 EntityId 를 가리키는 명령은 조용히 무시한다 — 명령은 sim 밖에서 만들어지고, 그 사이에 대상이 죽어 제거될 수 있다.
 */
public final class CommandApplySystem extends BaseSystem {

  private final EntityIndex index;
  private final EventBus eventBus;
  private final GameData gameData;

  private List<Command> pending = List.of();
  private MonsterFactory monsterFactory;

  private ComponentMapper<Velocity> mVelocity;
  private ComponentMapper<Dead> mDead;
  private ComponentMapper<Stats> mStats;
  private ComponentMapper<Health> mHealth;
  private ComponentMapper<Mana> mMana;
  private ComponentMapper<Wallet> mWallet;
  private ComponentMapper<Experience> mExperience;
  private ComponentMapper<Inventory> mInventory;
  private ComponentMapper<PlayerTag> mPlayer;

  public CommandApplySystem(EntityIndex index, EventBus eventBus, GameData gameData) {
    this.index = index;
    this.eventBus = eventBus;
    this.gameData = gameData;
  }

  /** Sim.step() 이 world.process() 전에 호출한다. */
  public void enqueue(List<Command> commands) {
    this.pending = commands;
  }

  @Override
  protected void initialize() {
    mVelocity = world.getMapper(Velocity.class);
    mDead = world.getMapper(Dead.class);
    mStats = world.getMapper(Stats.class);
    mHealth = world.getMapper(Health.class);
    mMana = world.getMapper(Mana.class);
    mWallet = world.getMapper(Wallet.class);
    mExperience = world.getMapper(Experience.class);
    mInventory = world.getMapper(Inventory.class);
    mPlayer = world.getMapper(PlayerTag.class);
    monsterFactory = new MonsterFactory(world, index, gameData, eventBus);
  }

  @Override
  protected void processSystem() {
    for (int i = 0; i < pending.size(); i++) {
      Command command = pending.get(i);
      switch (command) {
        case Command.MoveInput move -> applyMove(move);
        case Command.SpawnPlayer spawn -> spawnPlayer(spawn);
        case Command.RestorePlayer restore -> restorePlayer(restore);
        case Command.SpawnMonster spawn -> spawnMonster(spawn);
        case Command.SpawnDummy spawn -> spawnDummy(spawn);
        case Command.UseSkill useSkill -> {
          // CombatSystem 이 자기 몫으로 따로 받은 같은 명령 목록에서 처리한다.
        }
      }
    }
    pending = List.of();
  }

  private void spawnMonster(Command.SpawnMonster spawn) {
    int chunkX = Math.floorDiv(AiContext.tileOf(spawn.x()), ChunkFormat.SIZE);
    int chunkY = Math.floorDiv(AiContext.tileOf(spawn.y()), ChunkFormat.SIZE);
    monsterFactory.spawn(spawn.x(), spawn.y(), spawn.speciesId(), chunkX, chunkY);
  }

  private void applyMove(Command.MoveInput move) {
    int artemisId = index.artemisIdOrMissing(move.entityId());
    if (artemisId < 0 || mDead.has(artemisId) || !mVelocity.has(artemisId)) {
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

    Mana mana = edit.create(Mana.class);
    mana.current = CombatConstants.PLAYER_MAX_MP;
    mana.max = CombatConstants.PLAYER_MAX_MP;

    edit.create(Wallet.class);
    edit.create(Experience.class);
    edit.create(Inventory.class);

    int stableId = index.assign(artemisId);
    EntityIdComponent idComponent = edit.create(EntityIdComponent.class);
    idComponent.value = stableId;

    eventBus.enqueue(new EntitySpawned(stableId, spawn.x(), spawn.y()));
  }

  /** D-16 — 존을 넘어온 플레이어의 레벨·경험치·HP/MP·소지금·인벤토리를 되돌린다. 스탯은 레벨에서 다시 계산한다(§3.2). */
  private void restorePlayer(Command.RestorePlayer restore) {
    int artemisId = index.artemisIdOrMissing(restore.entityId());
    if (artemisId < 0 || !mPlayer.has(artemisId) || !mStats.has(artemisId)) {
      return;
    }
    int level = Math.max(1, Math.min(Progression.MAX_LEVEL, restore.level()));
    ProgressSystem.applyLevel(artemisId, mStats.get(artemisId), level, mHealth, mMana);
    if (mHealth.has(artemisId) && restore.hp() > 0) {
      Health health = mHealth.get(artemisId);
      health.current = Math.min(health.max, restore.hp());
    }
    if (mMana.has(artemisId) && restore.mp() > 0) {
      Mana mana = mMana.get(artemisId);
      mana.current = Math.min(mana.max, restore.mp());
    }
    if (mExperience.has(artemisId)) {
      mExperience.get(artemisId).exp = Math.max(0, restore.exp());
    }
    if (mWallet.has(artemisId)) {
      mWallet.get(artemisId).coins = Math.max(0, restore.coins());
    }
    if (mInventory.has(artemisId)) {
      InventoryOps.load(mInventory.get(artemisId), restore.itemIds(), restore.counts());
    }
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
