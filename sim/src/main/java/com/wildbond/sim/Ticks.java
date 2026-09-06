package com.wildbond.sim;

/**
 * 시뮬레이션 고정 틱 상수. docs/architecture.md §4.3.
 *
 * <p>이 값은 결정성의 기준이므로 바꿀 때 문서(§0 비기능 목표, §4.3, §9.4 예산)를 먼저 고친다.
 */
public final class Ticks {

  /** 고정 틱 간격 (밀리초). */
  public static final int TICK_MILLIS = 50;

  /** 초당 틱 수. */
  public static final int TICKS_PER_SECOND = 1000 / TICK_MILLIS;

  /** 한 틱의 길이 (초). sim 내부 계산은 모두 이 값을 쓴다. */
  public static final float DT_SECONDS = TICK_MILLIS / 1000.0f;

  private Ticks() {}
}
