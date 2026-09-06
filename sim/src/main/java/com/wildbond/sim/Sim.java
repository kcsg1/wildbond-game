package com.wildbond.sim;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.wildbond.data.GameData;
import com.wildbond.sim.events.EventBus;
import com.wildbond.sim.systems.CombatSystem;
import com.wildbond.sim.systems.CommandApplySystem;
import com.wildbond.sim.systems.EntityIndex;
import com.wildbond.sim.systems.EntityQueries;
import com.wildbond.sim.systems.EventFlushSystem;
import com.wildbond.sim.systems.MovementSystem;
import java.util.List;

/**
 * sim 의 유일한 진입점 (docs/architecture.md §4). {@link #step} 하나로만 상태가 바뀐다. M0 단계 3 은 CommandApplySystem
 * → MovementSystem → CombatSystem(빈 껍데기) → EventFlushSystem 만 등록한다 — 나머지 §4.1 시스템은 이후 단계에서 추가된다.
 */
public final class Sim implements SimView {

  private static final long HASH_SEED = 0x9E3779B97F4A7C15L;
  private static final long HASH_MULTIPLIER = 0xFF51AFD7ED558CCDL;

  private final GameData gameData;
  private final long seed;
  private final World world;
  private final EntityQueries queries;
  private final CommandApplySystem commandApplySystem;
  private final CombatSystem combatSystem;
  private final EventBus eventBus;
  private final Rng rng;

  private int tick;

  public Sim(GameData gameData, TileMap tileMap, long seed) {
    this.gameData = gameData;
    this.seed = seed;
    this.rng = new Rng(seed);

    EntityIndex index = new EntityIndex();
    this.eventBus = new EventBus();
    this.commandApplySystem = new CommandApplySystem(index, eventBus);
    MovementSystem movementSystem = new MovementSystem(index, tileMap, eventBus);
    this.combatSystem = new CombatSystem(index, gameData, tileMap, eventBus, rng);
    EventFlushSystem eventFlushSystem = new EventFlushSystem(eventBus);

    WorldConfiguration config =
        new WorldConfigurationBuilder()
            .with(commandApplySystem, movementSystem, combatSystem, eventFlushSystem)
            .build();
    this.world = new World(config);
    this.queries = new EntityQueries(world, index);
  }

  /** 유일한 상태 변경 진입점 — 고정 틱 50ms (§4.3). */
  public void step(int tick, List<Command> commands) {
    this.tick = tick;
    commandApplySystem.enqueue(commands);
    combatSystem.enqueue(commands);
    world.setDelta(Ticks.DT_SECONDS);
    world.process();
  }

  public SimView view() {
    return this;
  }

  /** sim 이 참조하는 정적 데이터 — 이후 단계(전투·포획·거점)의 시스템이 쓴다. */
  public GameData gameData() {
    return gameData;
  }

  /**
   * 도메인 이벤트 구독 (§4.1 EventFlushSystem "렌더·오디오가 구독"). 렌더 쪽(client-core)이 타격 이펙트·사망 연출을 만들 때 쓴다 — sim
   * 상태를 직접 건드리지 않는 읽기 전용 알림이라 SimView/Command 와 별개의 통로로 열어 둔다.
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
  public EntityKind kind(int stableId) {
    return queries.kind(stableId);
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
