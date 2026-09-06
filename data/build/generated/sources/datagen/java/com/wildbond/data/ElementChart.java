// 생성된 파일 — 손으로 고치지 않는다.
// 원천: data/tables/ElementChart.csv
// 생성기: tools/datagen (docs/architecture.md §8.1)

package com.wildbond.data;

/**
 * 속성 상성표 (docs/architecture.md §3.2). 행=공격, 열=방어.
 *
 * <p>대칭이 아니다 — 상성은 방향에 따라 다르다.
 */
public final class ElementChart {

  private static final float[][] TABLE = {
    {1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f}, // none
    {1.00f, 0.50f, 0.50f, 2.00f, 1.00f, 2.00f, 1.00f, 1.00f, 1.00f}, // fire
    {1.00f, 2.00f, 0.50f, 0.50f, 1.00f, 1.00f, 2.00f, 1.00f, 1.00f}, // water
    {1.00f, 0.50f, 2.00f, 0.50f, 1.00f, 1.00f, 2.00f, 1.00f, 1.00f}, // grass
    {1.00f, 1.00f, 2.00f, 1.00f, 0.50f, 1.00f, 0.50f, 1.00f, 1.00f}, // electric
    {1.00f, 0.50f, 1.00f, 2.00f, 1.00f, 0.50f, 1.00f, 1.00f, 2.00f}, // ice
    {1.00f, 2.00f, 0.50f, 1.00f, 2.00f, 1.00f, 0.50f, 1.00f, 1.00f}, // ground
    {1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 0.50f, 1.00f}, // dark
    {1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 2.00f, 1.00f, 1.00f, 2.00f}, // dragon
  };

  private ElementChart() {}

  /** 공격 속성이 방어 속성에 주는 배수. */
  public static float multiplier(Element attacker, Element defender) {
    return TABLE[attacker.ordinal()][defender.ordinal()];
  }

  /** 축 길이. Element 상수 개수와 같다. */
  public static int size() {
    return TABLE.length;
  }
}
