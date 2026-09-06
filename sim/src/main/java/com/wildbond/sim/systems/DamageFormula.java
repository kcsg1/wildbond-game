package com.wildbond.sim.systems;

import com.wildbond.data.Element;
import com.wildbond.data.ElementChart;

/**
 * docs/architecture.md §3.2 데미지 공식. 컴포넌트·Rng 접근과 분리한 순수 함수라 유닛 테스트가 쉽다 — CombatSystem 은 크리티컬 판정과 랜덤
 * 롤을 combat Rng 스트림에서 뽑은 뒤 이 함수에 넘긴다.
 */
public final class DamageFormula {

  public static int compute(
      int attackerAtk,
      int attackerLevel,
      int skillPower,
      Element skillElement,
      Element defenderElement,
      int defenderDef,
      boolean critical,
      float randomRoll) {
    float elementMul = ElementChart.multiplier(skillElement, defenderElement);
    float levelFactor = 1f + 0.02f * attackerLevel;
    float defenseFactor = 1f + defenderDef / 200f;
    float critMul = critical ? CombatConstants.CRIT_MULTIPLIER : 1f;

    float raw =
        attackerAtk
            * (skillPower / 100f)
            * elementMul
            * (levelFactor / defenseFactor)
            * critMul
            * randomRoll;
    return Math.max(0, (int) StrictMath.floor(raw));
  }

  private DamageFormula() {}
}
