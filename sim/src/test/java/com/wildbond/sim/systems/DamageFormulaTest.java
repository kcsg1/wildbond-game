package com.wildbond.sim.systems;

import static org.assertj.core.api.Assertions.assertThat;

import com.wildbond.data.Element;
import org.junit.jupiter.api.Test;

/** docs/architecture.md §3.2 데미지 공식 — RNG·ECS 없이 순수 계산만 검증한다. */
class DamageFormulaTest {

  @Test
  void elementMultiplierAppliesDoubleAndHalf() {
    // fire -> grass = 2.0, fire -> water = 0.5 (data/tables/ElementChart.csv). level=0, def=0, no
    // crit, random=1.0 이라 나머지 항을 전부 1로 만들어 배수만 남긴다.
    int superEffective =
        DamageFormula.compute(100, 0, 100, Element.FIRE, Element.GRASS, 0, false, 1f);
    int notVeryEffective =
        DamageFormula.compute(100, 0, 100, Element.FIRE, Element.WATER, 0, false, 1f);

    assertThat(superEffective).isEqualTo(200);
    assertThat(notVeryEffective).isEqualTo(50);
    assertThat(superEffective).isEqualTo(4 * notVeryEffective);
  }

  @Test
  void defense200HalvesDamage() {
    int noDefense = DamageFormula.compute(100, 0, 100, Element.NONE, Element.NONE, 0, false, 1f);
    int defense200 = DamageFormula.compute(100, 0, 100, Element.NONE, Element.NONE, 200, false, 1f);

    assertThat(noDefense).isEqualTo(100);
    assertThat(defense200).isEqualTo(50);
  }

  @Test
  void criticalAppliesConfiguredMultiplier() {
    int normal = DamageFormula.compute(100, 0, 100, Element.NONE, Element.NONE, 0, false, 1f);
    int critical = DamageFormula.compute(100, 0, 100, Element.NONE, Element.NONE, 0, true, 1f);

    assertThat(critical).isEqualTo((int) (normal * CombatConstants.CRIT_MULTIPLIER));
  }
}
