package com.wildbond.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.wildbond.sim.systems.SimConstants;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimSpawnTest {

  @Test
  void spawnPlayerCreatesEntityWithExpectedState() {
    Sim sim = TestSupport.newSim(TestSupport.openMap(10, 10), 1L);

    sim.step(0, List.of(new Command.SpawnPlayer(64f, 64f)));

    SimView view = sim.view();
    assertThat(view.entityCount()).isEqualTo(1);
    int stableId = view.stableIdAt(0);
    assertThat(view.x(stableId)).isEqualTo(64f);
    assertThat(view.y(stableId)).isEqualTo(64f);
    assertThat(view.kind(stableId)).isEqualTo(EntityKind.PLAYER);
    assertThat(view.health(stableId)).isEqualTo(SimConstants.PLAYER_MAX_HP);
    assertThat(view.tick()).isEqualTo(0);
  }

  /**
   * 맵을 16×16 으로 잡아 SpawnSystem 의 야생 스폰(§9.1 "플레이어에서 24타일 이상")이 끼어들 자리를 없앤다 — 이 테스트가 보려는 것은 id 발급
   * 순서뿐이다.
   */
  @Test
  void stableIdsAreAssignedInAscendingSpawnOrder() {
    Sim sim = TestSupport.newSim(TestSupport.openMap(16, 16), 1L);

    sim.step(
        0,
        List.of(
            new Command.SpawnPlayer(32f, 32f),
            new Command.SpawnPlayer(64f, 64f),
            new Command.SpawnPlayer(96f, 96f)));

    SimView view = sim.view();
    assertThat(view.entityCount()).isEqualTo(3);
    assertThat(view.stableIdAt(0)).isLessThan(view.stableIdAt(1));
    assertThat(view.stableIdAt(1)).isLessThan(view.stableIdAt(2));
  }
}
