package com.wildbond.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** docs/architecture.md §4.4 — 같은 시드·같은 명령 로그는 같은 stateHash, 다른 시드는 다른 stateHash. */
class ReplayDeterminismTest {

  private static final int TICKS = 1000;
  private static final int MAP_SIZE = 64;

  @Test
  void sameSeedSameCommandsProduceSameHash() {
    assertThat(runReplay(12345L)).isEqualTo(runReplay(12345L));
  }

  @Test
  void differentSeedProducesDifferentHash() {
    assertThat(runReplay(111L)).isNotEqualTo(runReplay(222L));
  }

  /** 같은 시드로 두 번 호출하면 명령 로그도 완전히 같다 — 틱 번호만으로 결정되기 때문. */
  private long runReplay(long seed) {
    ArrayTileMap map = TestSupport.openMap(MAP_SIZE, MAP_SIZE);
    Sim sim = TestSupport.newSim(map, seed);

    sim.step(0, List.of(new Command.SpawnPlayer(1000f, 1000f)));
    int stableId = sim.view().stableIdAt(0);

    Dir8[] dirs = Dir8.values();
    for (int t = 1; t <= TICKS; t++) {
      Dir8 dir = dirs[1 + (t % 8)];
      boolean run = t % 3 == 0;
      sim.step(t, List.of(new Command.MoveInput(stableId, dir, run)));
    }

    return sim.view().stateHash();
  }
}
