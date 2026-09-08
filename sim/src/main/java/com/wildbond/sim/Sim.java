package com.wildbond.sim;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.wildbond.data.GameData;
import com.wildbond.data.chunk.Chunk;
import com.wildbond.data.chunk.ChunkCoord;
import com.wildbond.sim.events.EventBus;
import com.wildbond.sim.systems.AiSystem;
import com.wildbond.sim.systems.CombatSystem;
import com.wildbond.sim.systems.CommandApplySystem;
import com.wildbond.sim.systems.DropSystem;
import com.wildbond.sim.systems.EntityIndex;
import com.wildbond.sim.systems.EntityQueries;
import com.wildbond.sim.systems.EventFlushSystem;
import com.wildbond.sim.systems.MovementSystem;
import com.wildbond.sim.systems.PathFollowSystem;
import com.wildbond.sim.systems.Pathfinder;
import com.wildbond.sim.systems.ProgressSystem;
import com.wildbond.sim.systems.SimClock;
import com.wildbond.sim.systems.SpawnSystem;
import java.util.List;
import java.util.function.Function;

/**
 * sim 의 유일한 진입점 (docs/architecture.md §4). {@link #step} 하나로만 상태가 바뀐다.
 *
 * <p>시스템 실행 순서는 §4.1 표를 그대로 따른다 — CommandApply(1) → AI(2) → PathFollow(3) → Movement(4) → Combat(5)
 * → Drop(6) → Progress(7) → Spawn(8) → EventFlush(9).
 */
public final class Sim implements SimView {

  private static final long HASH_SEED = 0x9E3779B97F4A7C15L;
  private static final long HASH_MULTIPLIER = 0xFF51AFD7ED558CCDL;

  /** 존을 지정하지 않으면 표의 모든 종이 청크당 3마리, 플레이어에서 24타일 밖에 나온다 (테스트·벤치 기본값). */
  private static final SpawnRules DEFAULT_SPAWN_RULES = SpawnRules.of(new int[0], 3, 24);

  private final GameData gameData;
  private final long seed;
  private final World world;
  private final EntityQueries queries;
  private final CommandApplySystem commandApplySystem;
  private final CombatSystem combatSystem;
  private final EventBus eventBus;
  private final Rng rng;
  private final SimClock clock = new SimClock();

  private int tick;

  /** 존 설정 없이 만드는 편의 생성자 — 테스트·벤치가 쓴다. */
  public Sim(GameData gameData, TileMap tileMap, long seed) {
    this(gameData, tileMap, seed, DEFAULT_SPAWN_RULES, null);
  }

  public Sim(
      GameData gameData,
      TileMap tileMap,
      long seed,
      SpawnRules spawnRules,
      Function<ChunkCoord, Chunk> chunkLoader) {
    this.gameData = gameData;
    this.seed = seed;
    this.rng = new Rng(seed);

    EntityIndex index = new EntityIndex();
    this.eventBus = new EventBus();
    Pathfinder pathfinder = new Pathfinder(tileMap);

    this.commandApplySystem = new CommandApplySystem(index, eventBus, gameData);
    this.combatSystem = new CombatSystem(index, gameData, tileMap, eventBus, rng, clock);
    AiSystem aiSystem =
        new AiSystem(index, tileMap, gameData, rng, clock, pathfinder, combatSystem);
    PathFollowSystem pathFollowSystem = new PathFollowSystem(index);
    MovementSystem movementSystem = new MovementSystem(index, tileMap, eventBus);
    DropSystem dropSystem = new DropSystem(index, gameData, eventBus, rng);
    ProgressSystem progressSystem = new ProgressSystem(index, gameData, eventBus);
    SpawnSystem spawnSystem =
        new SpawnSystem(index, tileMap, gameData, rng, eventBus, clock, spawnRules, chunkLoader);
    EventFlushSystem eventFlushSystem = new EventFlushSystem(eventBus);

    WorldConfiguration config =
        new WorldConfigurationBuilder()
            .with(
                commandApplySystem,
                aiSystem,
                pathFollowSystem,
                movementSystem,
                combatSystem,
                dropSystem,
                progressSystem,
                spawnSystem,
                eventFlushSystem)
            .build();
    this.world = new World(config);
    this.queries = new EntityQueries(world, index);
  }

  /** 유일한 상태 변경 진입점 — 고정 틱 50ms (§4.3). */
  public void step(int tick, List<Command> commands) {
    this.tick = tick;
    clock.set(tick);
    commandApplySystem.enqueue(commands);
    combatSystem.enqueue(commands);
    world.setDelta(Ticks.DT_SECONDS);
    world.process();
  }

  public SimView view() {
    return this;
  }

  /** sim 이 참조하는 정적 데이터. */
  public GameData gameData() {
    return gameData;
  }

  /**
   * 도메인 이벤트 구독 (§4.1 EventFlushSystem "렌더·오디오가 구독"). 렌더 쪽(client-core)이 타격 이펙트·레벨업 연출을 만들 때 쓴다 —
   * sim 상태를 직접 건드리지 않는 읽기 전용 알림이라 SimView/Command 와 별개의 통로로 열어 둔다.
   */
  public void subscribe(EventBus.Listener listener) {
    eventBus.subscribe(listener);
  }

  @Override
  public int entityCount() {
    return queries.entityCount();
  }

  @Override
  public int stableIdAt(int index) {
    return queries.stableIdAt(index);
  }

  @Override
  public float x(int stableId) {
    return queries.x(stableId);
  }

  @Override
  public float y(int stableId) {
    return queries.y(stableId);
  }

  @Override
  public int health(int stableId) {
    return queries.health(stableId);
  }

  @Override
  public int maxHealth(int stableId) {
    return queries.maxHealth(stableId);
  }

  @Override
  public EntityKind kind(int stableId) {
    return queries.kind(stableId);
  }

  @Override
  public int speciesId(int stableId) {
    return queries.speciesId(stableId);
  }

  @Override
  public int level(int stableId) {
    return queries.level(stableId);
  }

  @Override
  public int mana(int stableId) {
    return queries.mana(stableId);
  }

  @Override
  public int maxMana(int stableId) {
    return queries.maxMana(stableId);
  }

  @Override
  public int coins(int stableId) {
    return queries.coins(stableId);
  }

  @Override
  public int experience(int stableId) {
    return queries.experience(stableId);
  }

  @Override
  public int expToNextLevel(int stableId) {
    return queries.expToNextLevel(stableId);
  }

  @Override
  public int inventoryItemId(int stableId, int slot) {
    return queries.inventoryItemId(stableId, slot);
  }

  @Override
  public int inventoryCount(int stableId, int slot) {
    return queries.inventoryCount(stableId, slot);
  }

  @Override
  public int dropItemId(int stableId) {
    return queries.dropItemId(stableId);
  }

  @Override
  public int dropAmount(int stableId) {
    return queries.dropAmount(stableId);
  }

  @Override
  public float deathProgress(int stableId) {
    return queries.deathProgress(stableId);
  }

  @Override
  public int tick() {
    return tick;
  }

  @Override
  public long stateHash() {
    long h = mix(HASH_SEED, seed);
    h = mix(h, tick);
    int n = queries.entityCount();
    for (int i = 0; i < n; i++) {
      int stableId = queries.stableIdAt(i);
      h = mix(h, stableId);
      h = mix(h, Float.floatToIntBits(queries.x(stableId)));
      h = mix(h, Float.floatToIntBits(queries.y(stableId)));
      h = mix(h, queries.health(stableId));
    }
    return h;
  }

  private static long mix(long h, long v) {
    h ^= v;
    h *= HASH_MULTIPLIER;
    h ^= (h >>> 33);
    return h;
  }
}
