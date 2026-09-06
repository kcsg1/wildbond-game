package com.wildbond.sim;

/**
 * 1/1024 회전 단위 각도 변환 (docs/architecture.md §4.3, §4.2 UseSkill.aimAngle). 0 은 +x(동쪽) 방향, 값이 커질수록 +y
 * 방향(월드는 yDown 이므로 화면상 시계방향)으로 돈다. 클라이언트(마우스 조준 인코딩)와 sim(CombatSystem 판정)이 같은 단위를 쓰기 위해 여기 하나만 둔다.
 */
public final class Angle {

  public static final int UNITS_PER_TURN = 1024;

  /** 라디안 → 1/1024 단위, [0, 1024) 로 감싼다. */
  public static int fromRadians(float radians) {
    float turns = radians / (2f * (float) StrictMath.PI);
    int units = (int) StrictMath.floor(turns * UNITS_PER_TURN);
    return Math.floorMod(units, UNITS_PER_TURN);
  }

  /** 1/1024 단위 → 라디안. 입력은 [0, 1024) 로 감싸 해석한다. */
  public static float toRadians(int units) {
    return Math.floorMod(units, UNITS_PER_TURN) * (2f * (float) StrictMath.PI / UNITS_PER_TURN);
  }

  private Angle() {}
}
