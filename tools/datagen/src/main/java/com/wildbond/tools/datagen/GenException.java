package com.wildbond.tools.datagen;

/** 검증 실패. 메시지는 파일·행·열을 지목해야 한다. */
public final class GenException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public GenException(String message) {
    super(message);
  }
}
