package com.wildbond.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.wildbond.data.TileCollision;
import com.wildbond.sim.events.CoinsPicked;
import com.wildbond.sim.events.ItemDropped;
import com.wildbond.sim.events.ItemPicked;
import com.wildbond.sim.events.LevelUp;
import com.wildbond.sim.events.SimEvent;
import com.wildbond.sim.systems.CombatConstants;
import com.wildbond.sim.systems.SimConstants;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * docs/architecture.md §12 M0 사냥 루프 — 사슴을 잡으면 전리품 한 줄이 떨어지고(동전 또는 가죽/고기), 다가가면 동전은 소지금으로, 나머지는
 * 인벤토리로 들어가며, 경험치가 쌓여 레벨이 오른다.
 *
 * <p>사슴은 맞으면 도망치므로(§9.1) 한 칸짜리 막다른 복도에 가둬 놓고 잡는다 — 이 테스트가 보려는 것은 AI 가 아니라 드롭·획득·경험치다.
 */
class HuntLoopTest {

  private static final int DEER = 1;
  private static final int COIN = 1;
  private static final int MELEE = CombatConstants.PLAYER_SKILL_IDS[0];
  private static final int AIM_EAST = Angle.fromRadians(0f);
  private static final int AIM_WEST = Angle.fromRadians((float) StrictMath.PI);

  /** 폭 1, 길이 4 의 복도(tx 1..4, ty 1). 나머지는 전부 벽. */
  private static ArrayTileMap corridor() {
    int width = 6;
    int height = 3;
    byte[] grid = new byte[width * height];
    for (int ty = 0; ty < height; ty++) {
      for (int tx = 0; tx < width; tx++) {
        boolean open = ty == 1 && tx >= 1 && tx <= 4;
        grid[ty * width + tx] = (byte) (open ? TileCollision.NONE : TileCollision.SOLID).ordinal();
      }
    }
    return new ArrayTileMap(width, height, grid);
  }

  @Test
  void killingDeerDropsLootThatPlayerPicksUp() {
    Sim sim = TestSupport.newSim(corridor(), 21L);
    List<SimEvent> events = subscribeAll(sim);

    float y = tileCenter(1);
    sim.step(
        0,
        List.of(
            new Command.SpawnPlayer(tileCenter(1), y),
            new Command.SpawnMonster(tileCenter(2), y, DEER)));
    SimView view = sim.view();
    int playerId = view.stableIdAt(0);
    int deerId = view.stableIdAt(1);

    int tick = huntUntilDead(sim, playerId, deerId, 1);

    assertThat(events.stream().filter(ItemDropped.class::isInstance))
        .as("사슴이 죽으면 전리품이 정확히 한 줄 떨어진다 (§3.2)")
        .hasSize(1);
    ItemDropped dropped =
        (ItemDropped) events.stream().filter(ItemDropped.class::isInstance).findFirst().get();
    assertThat(dropped.itemId()).as("동전 / 사슴가죽 / 사슴고기 중 하나").isIn(1, 2, 3);

    // 전리품 쪽으로 걸어가 줍는다.
    for (int t = tick + 1; t <= tick + 60; t++) {
      sim.step(t, List.of(new Command.MoveInput(playerId, Dir8.E, false)));
    }

    if (dropped.itemId() == COIN) {
      assertThat(events.stream().filter(CoinsPicked.class::isInstance)).hasSize(1);
      assertThat(view.coins(playerId)).isEqualTo(dropped.amount());
    } else {
      assertThat(events.stream().filter(ItemPicked.class::isInstance)).hasSize(1);
      assertThat(view.inventoryItemId(playerId, 0)).isEqualTo(dropped.itemId());
      assertThat(view.inventoryCount(playerId, 0)).isEqualTo(dropped.amount());
    }
    assertThat(countKind(view, EntityKind.DROP)).as("주운 전리품은 사라진다").isZero();
    assertThat(view.experience(playerId)).as("사슴 exp_yield 8 을 받았다").isEqualTo(8);
  }

  @Test
  void enoughDeerKillsLevelThePlayerUp() {
    Sim sim = TestSupport.newSim(corridor(), 22L);
    List<SimEvent> events = subscribeAll(sim);

    float y = tileCenter(1);
    sim.step(0, List.of(new Command.SpawnPlayer(tileCenter(1), y)));
    SimView view = sim.view();
    int playerId = view.stableIdAt(0);
    int tick = 0;

    // 1→2 레벨에 20 exp, 사슴 한 마리에 8 — 세 마리면 넘는다.
    for (int kill = 0; kill < 3; kill++) {
      tick++;
      sim.step(tick, List.of(new Command.SpawnMonster(tileCenter(2), y, DEER)));
      int deerId = view.stableIdAt(view.entityCount() - 1);
      tick = huntUntilDead(sim, playerId, deerId, tick + 1);
      // 시체가 치워지고 전리품을 줍도록 잠깐 걸어갔다 온다.
      for (int i = 0; i < CombatConstants.DEAD_REMOVE_TICKS + 5; i++) {
        tick++;
        sim.step(
            tick, List.of(new Command.MoveInput(playerId, i % 2 == 0 ? Dir8.E : Dir8.W, false)));
      }
    }

    assertThat(view.level(playerId)).isEqualTo(2);
    assertThat(view.experience(playerId)).as("24 - 20 = 4 남는다").isEqualTo(4);
    assertThat(view.maxHealth(playerId)).isEqualTo(SimConstants.PLAYER_MAX_HP + 10);
    assertThat(view.health(playerId)).as("레벨업은 전부 회복").isEqualTo(view.maxHealth(playerId));
    assertThat(events.stream().filter(LevelUp.class::isInstance))
        .containsExactly(new LevelUp(playerId, 2));
  }

  /** 사슴이 죽을 때까지 사슴 쪽으로 따라가며 벤다 (복도라 동/서뿐이다). 죽은 틱 번호를 돌려준다. */
  private static int huntUntilDead(Sim sim, int playerId, int deerId, int fromTick) {
    SimView view = sim.view();
    for (int t = fromTick; t < fromTick + 400; t++) {
      boolean deerIsEast = view.x(deerId) >= view.x(playerId);
      sim.step(
          t,
          List.of(
              new Command.MoveInput(playerId, deerIsEast ? Dir8.E : Dir8.W, false),
              new Command.UseSkill(playerId, MELEE, deerIsEast ? AIM_EAST : AIM_WEST)));
      if (view.health(deerId) <= 0) {
        return t;
      }
    }
    throw new AssertionError("400틱 안에 사슴을 잡지 못했다");
  }

  private static int countKind(SimView view, EntityKind kind) {
    int count = 0;
    for (int i = 0; i < view.entityCount(); i++) {
      if (view.kind(view.stableIdAt(i)) == kind) {
        count++;
      }
    }
    return count;
  }

  private static float tileCenter(int tile) {
    return tile * (float) SimConstants.TILE_SIZE_PX + SimConstants.TILE_SIZE_PX / 2f;
  }

  private static List<SimEvent> subscribeAll(Sim sim) {
    List<SimEvent> out = new ArrayList<>();
    sim.subscribe(out::add);
    return out;
  }
}
