package com.wildbond.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 속성 상성표 (docs/architecture.md §3.2). */
class ElementChartTest {

  @Test
  void axisMatchesElementEnum() {
    assertThat(ElementChart.size()).isEqualTo(Element.values().length);
  }

  @Test
  void chartIsNotSymmetric() {
    // 상성은 방향에 따라 다르다 — 대칭이면 표가 잘못된 것이다.
    assertThat(ElementChart.multiplier(Element.FIRE, Element.GRASS)).isEqualTo(2.0f);
    assertThat(ElementChart.multiplier(Element.GRASS, Element.FIRE)).isEqualTo(0.5f);

    boolean symmetric = true;
    for (Element attacker : Element.values()) {
      for (Element defender : Element.values()) {
        if (ElementChart.multiplier(attacker, defender)
            != ElementChart.multiplier(defender, attacker)) {
          symmetric = false;
        }
      }
    }
    assertThat(symmetric).as("상성표는 대칭이 아니어야 한다").isFalse();
  }

  @Test
  void multipliersAreOneOfThreeSteps() {
    for (Element attacker : Element.values()) {
      for (Element defender : Element.values()) {
        assertThat(ElementChart.multiplier(attacker, defender))
            .as("%s -> %s", attacker, defender)
            .isIn(0.5f, 1.0f, 2.0f);
      }
    }
  }

  @Test
  void neutralElementIsAlwaysNeutral() {
    for (Element defender : Element.values()) {
      assertThat(ElementChart.multiplier(Element.NONE, defender)).isEqualTo(1.0f);
    }
  }
}
