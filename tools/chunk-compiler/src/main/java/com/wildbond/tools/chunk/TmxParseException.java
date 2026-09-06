package com.wildbond.tools.chunk;

/** .tmx 파싱 실패. 메시지는 어떤 요소/속성이 문제인지 지목해야 한다. */
public final class TmxParseException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public TmxParseException(String message) {
    super(message);
  }

  public TmxParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
