package com.wildbond.sim.systems;

/** M0 포획 튜닝 상수 (docs/architecture.md §3.2). */
public final class CaptureConstants {

  /** §3.2 clamp(..., 0.005, 0.95). */
  public static final float MIN_CHANCE = 0.005f;

  public static final float MAX_CHANCE = 0.95f;

  /** §3.2 (1 + 0.5 × (1 - hp/maxHp)^1.5). */
  public static final float HP_BONUS_MAX = 0.5f;

  public static final float HP_BONUS_EXPONENT = 1.5f;

  /** §3.2 levelPenalty — "5레벨 초과마다 ×0.8". */
  public static final int LEVEL_PENALTY_STEP = 5;

  public static final float LEVEL_PENALTY_FACTOR = 0.8f;

  /**
   * §3.2 statusMultiplier — 수면/기절 1.4, 화상 1.1, 없음 1.0. M0 에는 상태이상을 거는 시스템이 없어 항상 "없음"이다(단계 6 에서
   * StatusEffects 컴포넌트를 미룬 것과 같은 이유 — plan.md 참고). 공식 자체는 배수를 인자로 받으므로 상태이상이 생기면 그 값만 넘기면 된다.
   */
  public static final float STATUS_MULTIPLIER_NONE = 1.0f;

  /** §3.2 captureBonusFromTech(0~10단계) — 기술 트리가 없는 M0 은 0단계. */
  public static final float TECH_BONUS_NONE = 1.0f;

  /** 흔들림 횟수 1~3 (§3.2 "결과와 무관하게 sim 이 지정하고 클라이언트는 연출만"). */
  public static final int MIN_SHAKES = 1;

  public static final int MAX_SHAKES = 3;

  /** 포획구 비행 속도와 포물선 정점 높이(가상 z). */
  public static final float SPHERE_SPEED_PX_S = 260f;

  public static final float SPHERE_APEX_PX = 56f;

  private CaptureConstants() {}
}
