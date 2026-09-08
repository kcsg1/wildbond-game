package com.wildbond.sim.systems;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.wildbond.data.GameData;
import com.wildbond.sim.Rng;
import com.wildbond.sim.TileMap;
import com.wildbond.sim.components.Brain;
import com.wildbond.sim.components.Dead;
import com.wildbond.sim.components.Owner;
import com.wildbond.sim.systems.bt.BtNode;

/**
 * §4.1 시스템 2번 AISystem — Brain 을 가진 엔티티마다 행동 트리를 한 번 돌린다 (docs/architecture.md §9.1).
 *
 * <p>순회는 EntityIndex 의 EntityId 오름차순이고(§4.3), 감지(레이캐스트)는 개체마다 0.25s 간격으로 나눠 돈다. 경로 요청은 {@link
 * Pathfinder} 의 틱당 상한(§9.3)을 공유한다 — 이 시스템이 틱 시작에 상한을 되돌린다.
 */
public final class AiSystem extends BaseSystem {

  private final EntityIndex index;
  private final TileMap tileMap;
  private final GameData gameData;
  private final Rng rng;
  private final SimClock clock;
  private final Pathfinder pathfinder;
  private final CombatSystem combatSystem;

  private AiContext context;
  private BtNode<AiContext> wildTree;
  private BtNode<AiContext> partyTree;

  private ComponentMapper<Brain> mBrain;
  private ComponentMapper<Dead> mDead;
  private ComponentMapper<Owner> mOwner;

  public AiSystem(
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
  }

  @Override
  protected void initialize() {
    mBrain = world.getMapper(Brain.class);
    mDead = world.getMapper(Dead.class);
    mOwner = world.getMapper(Owner.class);
    context = new AiContext(world, index, tileMap, gameData, rng, clock, pathfinder, combatSystem);
    wildTree = PalBehaviors.wildTree();
    partyTree = PalBehaviors.partyTree();
  }

  @Override
  protected void processSystem() {
    pathfinder.beginTick();

    int n = index.size();
    for (int i = 0; i < n; i++) {
      int artemisId = index.artemisIdAt(i);
      if (!mBrain.has(artemisId) || mDead.has(artemisId)) {
        continue;
      }
      int stableId = index.stableIdAt(i);
      context.bind(artemisId, stableId);

      Brain brain = mBrain.get(artemisId);
      if (clock.tick() >= brain.nextSenseTick) {
        // 개체마다 감지 시점을 어긋나게 해 한 틱에 레이캐스트가 몰리지 않게 한다(§9.4 AI 예산).
        brain.nextSenseTick =
            clock.tick()
                + PalConstants.SENSE_INTERVAL_TICKS
                + stableId % PalConstants.SENSE_INTERVAL_TICKS;
        context.sense();
      }

      BtNode<AiContext> tree = mOwner.has(artemisId) ? partyTree : wildTree;
      tree.tick(context);
    }
  }
}
