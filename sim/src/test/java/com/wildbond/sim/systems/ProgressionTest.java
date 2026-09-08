package com.wildbond.sim.systems;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** docs/architecture.md §3.2 경험치·레벨 공식. */
class ProgressionTest {

  @Test
  void expToNextIsTwentyTimesLevelSquared() {
    assertThat(Progression.expToNext(1)).isEqualTo(20);
    assertThat(Progression.expToNext(2)).isEqualTo(80);
    assertThat(Progression.expToNext(10)).isEqualTo(2000);
  }

  @Test
  void levelForSkipsSeveralLevelsAtOnce() {
    // 1→2 에 20, 2→3 에 80: 합 100 이면 딱 3레벨, 남는 경험치 0.
    assertThat(Progression.levelFor(1, 100)).isEqualTo(3);
    assertThat(Progression.expCarried(1, 100)).isEqualTo(0);
    assertThat(Progression.levelFor(1, 19)).isEqualTo(1);
    assertThat(Progression.levelFor(1, 25)).isEqualTo(2);
    assertThat(Progression.expCarried(1, 25)).isEqualTo(5);
  }

  @Test
  void levelCapsAtMax() {
    assertThat(Progression.levelFor(Progression.MAX_LEVEL, 1_000_000))
        .isEqualTo(Progression.MAX_LEVEL);
  }

  @Test
  void statsGrowPerLevelAsDocumented() {
    assertThat(Progression.maxHpAt(1)).isEqualTo(SimConstants.PLAYER_MAX_HP);
    assertThat(Progression.maxHpAt(2)).isEqualTo(SimConstants.PLAYER_MAX_HP + 10);
    assertThat(Progression.maxMpAt(3)).isEqualTo(CombatConstants.PLAYER_MAX_MP + 10);
    assertThat(Progression.atkAt(4)).isEqualTo(CombatConstants.PLAYER_ATK + 6);
    assertThat(Progression.defAt(5)).isEqualTo(CombatConstants.PLAYER_DEF + 4);
  }
}
