// 생성된 파일 — 손으로 고치지 않는다.
// 원천: data/tables/enums.csv
// 생성기: tools/datagen (docs/architecture.md §8.1)

package com.wildbond.data;

/** enums.csv 에서 생성. */
public enum WorkType {
  KINDLING("kindling"),
  WATERING("watering"),
  PLANTING("planting"),
  ELECTRICITY("electricity"),
  HANDIWORK("handiwork"),
  GATHERING("gathering"),
  LUMBERING("lumbering"),
  MINING("mining"),
  COOLING("cooling"),
  TRANSPORTING("transporting");

  private final String csvValue;

  WorkType(String csvValue) {
    this.csvValue = csvValue;
  }

  /** CSV 에 적히는 문자열. */
  public String csvValue() {
    return csvValue;
  }

  /** CSV 문자열로 상수를 찾는다. */
  public static WorkType fromCsv(String value) {
    for (WorkType candidate : values()) {
      if (candidate.csvValue.equals(value)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("WorkType 값이 아니다: " + value);
  }
}
