package com.wildbond.sim.systems;

/**
 * docs/architecture.md §3.2 포획 확률 공식을 컴포넌트·Rng 접근에서 떼어낸 순수 함수. {@link DamageFormula} 와 같은 이유로 분리했다
 * — 난수 없이 정확한 값을 테스트할 수 있어야 한다.
 *
 * <pre>
 * captureChance = clamp(
 *     speciesBaseRate
 *   × sphereTier.multiplier
 *   × (1 + 0.5 × (1 - hp/maxHp)^1.5)
 *   × statusMultiplier
 *   × levelPenalty(playerLvl, palLvl)
 *   × captureBonusFromTech,
 *   0.005, 0.95)
 * </pre>
 */
public final class CaptureFormula {

  private CaptureFormula() {}

  public static float chance(
      float speciesBaseRate,
      float sphereMultiplier,
      int hp,
      int maxHp,
      float statusMultiplier,
      int playerLevel,
      int palLevel,
      float techBonus) {
    float raw =
        speciesBaseRate
            * sphereMultiplier
            * hpBonus(hp, maxHp)
            * statusMultiplier
            * levelPenalty(playerLevel, palLevel)
            * techBonus;
    return clamp(raw);
  }

  /** HP 가 낮을수록 최대 +50%. */
  public static float hpBonus(int hp, int maxHp) {
    if (maxHp <= 0) {
      return 1f + CaptureConstants.HP_BONUS_MAX;
    }
    float missingRatio = 1f - (float) hp / maxHp;
    if (missingRatio <= 0f) {
      return 1f;
    }
    return 1f
        + CaptureConstants.HP_BONUS_MAX
            * (float) StrictMath.pow(missingRatio, CaptureConstants.HP_BONUS_EXPONENT);
  }

  /**
   * 팰이 플레이어보다 높은 레벨일 때 {@value CaptureConstants#LEVEL_PENALTY_STEP} 레벨 차마다 ×{@value
   * CaptureConstants#LEVEL_PENALTY_FACTOR}. 레벨 차가 5 미만이면 페널티가 없다.
   */
  public static float levelPenalty(int playerLevel, int palLevel) {
    int excess = palLevel - playerLevel;
    if (excess < CaptureConstants.LEVEL_PENALTY_STEP) {
      return 1f;
    }
    int steps = excess / CaptureConstants.LEVEL_PENALTY_STEP;
    return (float) StrictMath.pow(CaptureConstants.LEVEL_PENALTY_FACTOR, steps);
  }

  private static float clamp(float value) {
    if (value < CaptureConstants.MIN_CHANCE) {
      return CaptureConstants.MIN_CHANCE;
    }
    return Math.min(value, CaptureConstants.MAX_CHANCE);
  }
}
