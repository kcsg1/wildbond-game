package com.wildbond.sim;

import com.wildbond.data.DataModule;

/**
 * sim 모듈의 자리표시자.
 *
 * <p>단계 3 에서 ECS 월드와 {@code Sim.step(tick, commands)} 진입점이 이 패키지에 들어온다. docs/architecture.md §4 참고.
 */
public final class SimModule {

  /** 모듈 이름. 로그·진단용. */
  public static final String NAME = "sim";

  /** 의존 방향 확인용 — sim 은 data 를 참조한다 (§5.1). */
  public static final String DEPENDS_ON = DataModule.NAME;

  private SimModule() {}
}
