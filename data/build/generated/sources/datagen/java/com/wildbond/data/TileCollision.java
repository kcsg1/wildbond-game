// 생성된 파일 — 손으로 고치지 않는다.
// 원천: data/tables/enums.csv
// 생성기: tools/datagen (docs/architecture.md §8.1)

package com.wildbond.data;

/** enums.csv 에서 생성. */
public enum TileCollision {
  NONE("none"),
  SOLID("solid"),
  WATER("water"),
  CLIFF("cliff");

  private final String csvValue;

  TileCollision(String csvValue) {
    this.csvValue = csvValue;
  }

  /** CSV 에 적히는 문자열. */
  public String csvValue() {
    return csvValue;
  }

  /** CSV 문자열로 상수를 찾는다. */
  public static TileCollision fromCsv(String value) {
    for (TileCollision candidate : values()) {
      if (candidate.csvValue.equals(value)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("TileCollision 값이 아니다: " + value);
  }
}
