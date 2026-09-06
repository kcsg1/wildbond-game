package com.wildbond.sim;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TicksTest {

  @Test
  void fixedTickIs20Hz() {
    assertThat(Ticks.TICK_MILLIS).isEqualTo(50);
    assertThat(Ticks.TICKS_PER_SECOND).isEqualTo(20);
    assertThat(Ticks.DT_SECONDS).isEqualTo(0.05f);
  }

  @Test
  void simDependsOnData() {
    assertThat(SimModule.DEPENDS_ON).isEqualTo("data");
  }
}
