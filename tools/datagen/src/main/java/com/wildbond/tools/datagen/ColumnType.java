package com.wildbond.tools.datagen;

import java.util.Locale;

/**
 * 타입 행 토큰 하나를 해석한 결과.
 *
 * <p>문법:
 *
 * <pre>
 *   int | float | bool | string
 *   int(min..max) | float(min..max)
 *   int[N] | int[N](min..max)
 *   enum:Name
 *   ref:Table
 *   ref:Table[]
 *   위 모든 형태에 ? 를 붙이면 빈 셀 허용(널 가능)
 * </pre>
 */
record ColumnType(
    Kind kind,
    boolean nullable,
    boolean array,
    int arrayLength,
    String refName,
    Double min,
    Double max) {

  enum Kind {
    INT,
    FLOAT,
    BOOL,
    STRING,
    ENUM,
    REF
  }

  static ColumnType parse(String rawToken, String where) {
    String token = rawToken.strip();
    if (token.isEmpty()) {
      throw new GenException(where + ": 타입이 비어 있다");
    }
    boolean nullable = token.endsWith("?");
    if (nullable) {
      token = token.substring(0, token.length() - 1).strip();
    }

    Double min = null;
    Double max = null;
    int open = token.indexOf('(');
    if (open >= 0) {
      if (!token.endsWith(")")) {
        throw new GenException(where + ": 범위 괄호가 닫히지 않았다: " + rawToken);
      }
      String range = token.substring(open + 1, token.length() - 1);
      token = token.substring(0, open);
      int dots = range.indexOf("..");
      if (dots < 0) {
        throw new GenException(where + ": 범위는 min..max 형식이어야 한다: " + rawToken);
      }
      min = Double.parseDouble(range.substring(0, dots).strip());
      max = Double.parseDouble(range.substring(dots + 2).strip());
    }

    boolean array = false;
    int arrayLength = -1;
    int bracket = token.indexOf('[');
    if (bracket >= 0) {
      if (!token.endsWith("]")) {
        throw new GenException(where + ": 배열 괄호가 닫히지 않았다: " + rawToken);
      }
      String len = token.substring(bracket + 1, token.length() - 1).strip();
      token = token.substring(0, bracket);
      array = true;
      if (!len.isEmpty()) {
        arrayLength = Integer.parseInt(len);
      }
    }

    String lower = token.toLowerCase(Locale.ROOT);
    if (lower.startsWith("enum:")) {
      return new ColumnType(
          Kind.ENUM, nullable, array, arrayLength, token.substring(5).strip(), min, max);
    }
    if (lower.startsWith("ref:")) {
      return new ColumnType(
          Kind.REF, nullable, array, arrayLength, token.substring(4).strip(), min, max);
    }
    Kind kind =
        switch (lower) {
          case "int" -> Kind.INT;
          case "float" -> Kind.FLOAT;
          case "bool" -> Kind.BOOL;
          case "string" -> Kind.STRING;
          default -> throw new GenException(where + ": 알 수 없는 타입: " + rawToken);
        };
    return new ColumnType(kind, nullable, array, arrayLength, null, min, max);
  }

  /** 생성될 Java 타입 이름. */
  String javaType() {
    String base =
        switch (kind) {
          case INT -> nullable && !array ? "Integer" : "int";
          case FLOAT -> nullable && !array ? "Float" : "float";
          case BOOL -> nullable && !array ? "Boolean" : "boolean";
          case STRING -> "String";
          case ENUM -> refName;
          case REF -> nullable && !array ? "Integer" : "int";
        };
    return array ? base + "[]" : base;
  }
}
