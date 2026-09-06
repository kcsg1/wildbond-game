package com.wildbond.data;

/**
 * data 모듈의 자리표시자.
 *
 * <p>단계 2 에서 {@code tools/datagen} 이 생성한 정적 테이블 record 와 {@code GameData} 로더가 이 패키지를 채운다.
 * docs/architecture.md §8.1 참고.
 */
public final class DataModule {

  /** 모듈 이름. 로그·진단용. */
  public static final String NAME = "data";

  private DataModule() {}
}
