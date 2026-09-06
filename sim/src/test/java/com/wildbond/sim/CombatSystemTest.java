package com.wildbond.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.wildbond.data.Element;
import com.wildbond.data.TileCollision;
import com.wildbond.sim.events.Damaged;
import com.wildbond.sim.events.Died;
import com.wildbond.sim.systems.CombatConstants;
import com.wildbond.sim.systems.SimConstants;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** docs/architecture.md §3.2, §4.1 CombatSystem — 쿨다운·투사체 충돌·결정성·사망 제거를 통합 시나리오로 검증한다. */
class CombatSystemTest {

  private static final int MELEE_SKILL_ID = CombatConstants.PLAYER_SKILL_IDS[0]; // skill.slash
  private static final int RANGED_SKILL_ID = CombatConstants.PLAYER_SKILL_IDS[1]; // skill.ember
  private static final int AIM_EAST = Angle.fromRadians(0f);

  @Test
  void cooldownBlocksReuseUntilItExpires() {
    Sim sim = TestSupport.newSim(TestSupport.openMap(30, 30), 1L);
    List<Damaged> damaged = subscribeDamaged(sim);

    float x = 15 * SimConstants.TILE_SIZE_PX + 16f;
    float y = 15 * SimConstants.TILE_SIZE_PX + 16f;
    sim.step(
        0,
        List.of(
            new Command.SpawnPlayer(x, y),
            new Command.SpawnDummy(x + 20f, y, Element.NONE, 10_000, 0, 0, 1)));
    int playerId = sim.view().stableIdAt(0);

    for (int t = 1; t <= 12; t++) {
      sim.step(t, List.of(new Command.UseSkill(playerId, MELEE_SKILL_ID, AIM_EAST)));
    }

    assertThat(damaged).as("쿨다운(10틱) 동안 12번 시도해도 실제로 명중하는 건 두 번뿐이다").hasSize(2);
  }

  @Test
  void projectileStopsAtSolidTileAndNeverDamagesTargetBeyondIt() {
    int width = 30;
    int height = 10;
    byte[] grid = new byte[width * height];
    int wallTx = 10;
    for (int ty = 0; ty < height; ty++) {
      grid[ty * width + wallTx] = (byte) TileCollision.SOLID.ordinal();
    }
    ArrayTileMap map = new ArrayTileMap(width, height, grid);
    Sim sim = TestSupport.newSim(map, 2L);
    List<Damaged> damaged = subscribeDamaged(sim);

    float y = 5 * SimConstants.TILE_SIZE_PX + 16f;
    float playerX = 5 * SimConstants.TILE_SIZE_PX + 16f;
    float dummyX = 20 * SimConstants.TILE_SIZE_PX + 16f; // 벽(tx=10) 너머
    sim.step(
        0,
        List.of(
            new Command.SpawnPlayer(playerX, y),
            new Command.SpawnDummy(dummyX, y, Element.NONE, 100, 0, 0, 1)));
    SimView view = sim.view();
    int playerId = view.stableIdAt(0);
    int dummyId = view.stableIdAt(1);

    sim.step(1, List.of(new Command.UseSkill(playerId, RANGED_SKILL_ID, AIM_EAST)));
    for (int t = 2; t <= 40; t++) {
      sim.step(t, List.of());
    }

    assertThat(damaged).as("투사체가 벽에 막혀 대상까지 도달하지 못한다").isEmpty();
    assertThat(view.health(dummyId)).isEqualTo(100);
  }

  @Test
  void sameSeedReproducesCriticalSequence() {
    assertThat(criticalSequence(777L)).isEqualTo(criticalSequence(777L));
  }

  @Test
  void targetDiesAndIsRemovedFiveSecondsLater() {
    Sim sim = TestSupport.newSim(TestSupport.openMap(30, 30), 3L);
    List<Died> died = new ArrayList<>();
    sim.subscribe(
        event -> {
          if (event instanceof Died d) {
            died.add(d);
          }
        });

    float x = 15 * SimConstants.TILE_SIZE_PX + 16f;
    float y = 15 * SimConstants.TILE_SIZE_PX + 16f;
    sim.step(
        0,
        List.of(
            new Command.SpawnPlayer(x, y),
            new Command.SpawnDummy(x + 20f, y, Element.NONE, 1, 0, 0, 1)));
    SimView view = sim.view();
    int playerId = view.stableIdAt(0);
    int dummyId = view.stableIdAt(1);

    sim.step(1, List.of(new Command.UseSkill(playerId, MELEE_SKILL_ID, AIM_EAST)));

    assertThat(died).containsExactly(new Died(dummyId));
    assertThat(view.entityCount()).as("5초 유예 동안은 그대로 남아있다").isEqualTo(2);

    for (int t = 2; t <= 1 + CombatConstants.DEAD_REMOVE_TICKS; t++) {
      sim.step(t, List.of());
    }

    assertThat(view.entityCount()).isEqualTo(1);
    assertThat(view.stableIdAt(0)).isEqualTo(playerId);
  }

  private static List<Boolean> criticalSequence(long seed) {
    Sim sim = TestSupport.newSim(TestSupport.openMap(30, 30), seed);
    List<Boolean> crits = new ArrayList<>();
    sim.subscribe(
        event -> {
          if (event instanceof Damaged d) {
            crits.add(d.critical());
          }
        });

    float x = 15 * SimConstants.TILE_SIZE_PX + 16f;
    float y = 15 * SimConstants.TILE_SIZE_PX + 16f;
    sim.step(
        0,
        List.of(
            new Command.SpawnPlayer(x, y),
            new Command.SpawnDummy(x + 20f, y, Element.NONE, 1_000_000, 0, 0, 1)));
    int playerId = sim.view().stableIdAt(0);

    for (int t = 1; t <= 250; t++) {
      sim.step(t, List.of(new Command.UseSkill(playerId, MELEE_SKILL_ID, AIM_EAST)));
    }
    return crits;
  }

  private static List<Damaged> subscribeDamaged(Sim sim) {
    List<Damaged> damaged = new ArrayList<>();
    sim.subscribe(
        event -> {
          if (event instanceof Damaged d) {
            damaged.add(d);
          }
        });
    return damaged;
  }
}
