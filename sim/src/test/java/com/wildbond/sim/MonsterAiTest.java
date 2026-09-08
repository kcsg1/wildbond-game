package com.wildbond.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.wildbond.sim.events.Damaged;
import com.wildbond.sim.systems.CombatConstants;
import com.wildbond.sim.systems.SimConstants;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * docs/architecture.md §9.1 성향표 — passive 사슴은 보기만 해서는 아무 일도 없고, 맞으면 도망친다. aggressive 늑대는 시야에 들어오면
 * 쫓아와 때린다. 종 id 는 data/tables/Monster.csv (1 사슴, 2 늑대).
 */
class MonsterAiTest {

  private static final int DEER = 1;
  private static final int WOLF = 2;
  private static final int MELEE = CombatConstants.PLAYER_SKILL_IDS[0];
  private static final int AIM_EAST = Angle.fromRadians(0f);

  @Test
  void passiveDeerNeverAttacksPlayerInPlainSight() {
    Sim sim = TestSupport.newSim(TestSupport.openMap(40, 40), 11L);
    List<Damaged> damaged = subscribeDamaged(sim);

    float px = tileCenter(20);
    float py = tileCenter(20);
    sim.step(
        0, List.of(new Command.SpawnPlayer(px, py), new Command.SpawnMonster(px + 96f, py, DEER)));
    int playerId = sim.view().stableIdAt(0);

    for (int t = 1; t <= 300; t++) {
      sim.step(t, List.of());
    }

    assertThat(damaged.stream().filter(d -> d.entityId() == playerId))
        .as("사슴은 스킬이 없고 시야로 위협을 잡지 않는다 — 15초 동안 플레이어가 맞을 일이 없다")
        .isEmpty();
  }

  @Test
  void deerFleesAfterBeingHit() {
    Sim sim = TestSupport.newSim(TestSupport.openMap(40, 40), 12L);

    float px = tileCenter(10);
    float py = tileCenter(20);
    sim.step(
        0, List.of(new Command.SpawnPlayer(px, py), new Command.SpawnMonster(px + 28f, py, DEER)));
    SimView view = sim.view();
    int playerId = view.stableIdAt(0);
    int deerId = view.stableIdAt(1);
    float before = distance(view, playerId, deerId);

    sim.step(1, List.of(new Command.UseSkill(playerId, MELEE, AIM_EAST)));
    assertThat(view.health(deerId)).as("근접 공격이 사슴에 맞았다").isLessThan(view.maxHealth(deerId));

    for (int t = 2; t <= 40; t++) {
      sim.step(t, List.of());
    }

    assertThat(distance(view, playerId, deerId))
        .as("맞은 사슴은 공격자에게서 멀어진다 (§9.1 Flee)")
        .isGreaterThan(before + 3 * SimConstants.TILE_SIZE_PX);
  }

  @Test
  void aggressiveWolfChasesAndBitesPlayerOnSight() {
    Sim sim = TestSupport.newSim(TestSupport.openMap(40, 40), 13L);
    List<Damaged> damaged = subscribeDamaged(sim);

    float px = tileCenter(20);
    float py = tileCenter(20);
    sim.step(
        0,
        List.of(
            new Command.SpawnPlayer(px, py),
            new Command.SpawnMonster(px + 6 * SimConstants.TILE_SIZE_PX, py, WOLF)));
    int playerId = sim.view().stableIdAt(0);

    for (int t = 1; t <= 200; t++) {
      sim.step(t, List.of());
    }

    assertThat(damaged.stream().filter(d -> d.entityId() == playerId))
        .as("늑대는 12타일 시야 안의 플레이어를 쫓아와 문다")
        .isNotEmpty();
  }

  private static float tileCenter(int tile) {
    return tile * (float) SimConstants.TILE_SIZE_PX + SimConstants.TILE_SIZE_PX / 2f;
  }

  private static float distance(SimView view, int a, int b) {
    float dx = view.x(a) - view.x(b);
    float dy = view.y(a) - view.y(b);
    return (float) StrictMath.sqrt(dx * dx + dy * dy);
  }

  private static List<Damaged> subscribeDamaged(Sim sim) {
    List<Damaged> out = new ArrayList<>();
    sim.subscribe(
        event -> {
          if (event instanceof Damaged d) {
            out.add(d);
          }
        });
    return out;
  }
}
