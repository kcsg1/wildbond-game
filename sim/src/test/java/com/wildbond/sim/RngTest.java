package com.wildbond.sim;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RngTest {

  @Test
  void sameSeedProducesSameSequence() {
    Rng a = new Rng(7L);
    Rng b = new Rng(7L);
    for (int i = 0; i < 20; i++) {
      assertThat(a.nextLong(Rng.Stream.COMBAT)).isEqualTo(b.nextLong(Rng.Stream.COMBAT));
    }
  }

  @Test
  void differentSeedsDiverge() {
    Rng a = new Rng(1L);
    Rng b = new Rng(2L);
    assertThat(a.nextLong(Rng.Stream.COMBAT)).isNotEqualTo(b.nextLong(Rng.Stream.COMBAT));
  }

  @Test
  void streamsAreIndependent() {
    Rng rng = new Rng(9L);
    long combat = rng.nextLong(Rng.Stream.COMBAT);
    long spawn = rng.nextLong(Rng.Stream.SPAWN);
    long loot = rng.nextLong(Rng.Stream.LOOT);
    assertThat(combat).isNotEqualTo(spawn);
    assertThat(spawn).isNotEqualTo(loot);
    assertThat(combat).isNotEqualTo(loot);
  }

  @Test
  void nextFloatStaysInUnitRange() {
    Rng rng = new Rng(3L);
    for (int i = 0; i < 1000; i++) {
      float f = rng.nextFloat(Rng.Stream.LOOT);
      assertThat(f).isGreaterThanOrEqualTo(0f).isLessThan(1f);
    }
  }

  @Test
  void nextIntRespectsBound() {
    Rng rng = new Rng(5L);
    for (int i = 0; i < 1000; i++) {
      int v = rng.nextInt(Rng.Stream.SPAWN, 10);
      assertThat(v).isBetween(0, 9);
    }
  }
}
