package com.wildbond.sim.systems;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.wildbond.sim.Rng;
import org.junit.jupiter.api.Test;

/**
 * docs/architecture.md §3.2 포획 공식 — 값 자체를 RNG 없이 검증하고, 성공률 통계는 §4.4 "레벨 10 팰 HP 5%에 3단계 포획구 1000회 →
 * 성공률 기대값 ±3%p" 방식으로 확인한다.
 */
class CaptureFormulaTest {

  /** data/tables/PalSpecies.csv pal.boulderox — 기본 포획률 0.2 (클램프에 걸리지 않아 통계가 의미 있다). */
  private static final float BOULDEROX_BASE_RATE = 0.2f;

  /** data/tables/Item.csv item.sphere_giga — 3단계, 배수 1.8. */
  private static final float SPHERE_TIER3_MULTIPLIER = 1.8f;

  private static final int TRIALS = 1000;

  @Test
  void hpFivePercentWithTierThreeSphereMatchesFormulaWithinThreePoints() {
    int maxHp = 200;
    int hp = maxHp * 5 / 100;
    float expected =
        CaptureFormula.chance(
            BOULDEROX_BASE_RATE,
            SPHERE_TIER3_MULTIPLIER,
            hp,
            maxHp,
            CaptureConstants.STATUS_MULTIPLIER_NONE,
            10,
            10,
            CaptureConstants.TECH_BONUS_NONE);

    assertThat(expected).as("클램프에 걸리면 통계 검증이 무의미해진다").isStrictlyBetween(0.05f, 0.9f);

    Rng rng = new Rng(20260907L);
    int successes = 0;
    for (int i = 0; i < TRIALS; i++) {
      if (rng.nextFloat(Rng.Stream.LOOT) < expected) {
        successes++;
      }
    }

    assertThat((float) successes / TRIALS)
        .as("%d회 시뮬 성공률이 공식 기대값 %.4f 의 ±3%%p 안", TRIALS, expected)
        .isCloseTo(expected, within(0.03f));
  }

  @Test
  void lowHpRaisesChanceUpToFiftyPercent() {
    assertThat(CaptureFormula.hpBonus(100, 100)).isEqualTo(1f);
    assertThat(CaptureFormula.hpBonus(0, 100))
        .as("HP 0 이면 최대 보너스 +50%%")
        .isCloseTo(1f + CaptureConstants.HP_BONUS_MAX, within(0.0001f));
    assertThat(CaptureFormula.hpBonus(50, 100)).isGreaterThan(1f).isLessThan(1.5f);
  }

  @Test
  void levelPenaltyAppliesEveryFiveLevelsAboveThePlayer() {
    assertThat(CaptureFormula.levelPenalty(10, 10)).as("레벨이 같으면 페널티 없음").isEqualTo(1f);
    assertThat(CaptureFormula.levelPenalty(10, 14)).as("5레벨 미만 차이는 페널티 없음").isEqualTo(1f);
    assertThat(CaptureFormula.levelPenalty(10, 15))
        .as("5레벨 초과 → ×0.8")
        .isCloseTo(0.8f, within(0.0001f));
    assertThat(CaptureFormula.levelPenalty(10, 20))
        .as("10레벨 초과 → ×0.8²")
        .isCloseTo(0.64f, within(0.0001f));
    assertThat(CaptureFormula.levelPenalty(10, 1)).as("팰이 더 약해도 보너스는 없다").isEqualTo(1f);
  }

  @Test
  void chanceIsClampedToDocumentedBounds() {
    float tooHigh =
        CaptureFormula.chance(1.0f, 3.0f, 0, 100, 1.4f, 50, 1, CaptureConstants.TECH_BONUS_NONE);
    assertThat(tooHigh).isEqualTo(CaptureConstants.MAX_CHANCE);

    float tooLow =
        CaptureFormula.chance(0.01f, 1.0f, 100, 100, 1.0f, 1, 60, CaptureConstants.TECH_BONUS_NONE);
    assertThat(tooLow).isEqualTo(CaptureConstants.MIN_CHANCE);
  }
}
