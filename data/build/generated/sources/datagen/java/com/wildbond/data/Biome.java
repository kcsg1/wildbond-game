// 생성된 파일 — 손으로 고치지 않는다.
// 원천: data/tables/enums.csv
// 생성기: tools/datagen (docs/architecture.md §8.1)

package com.wildbond.data;

/** enums.csv 에서 생성. */
public enum Biome {
  GRASSLAND("grassland"),
  FOREST("forest"),
  SNOW("snow"),
  DESERT("desert"),
  VOLCANO("volcano"),
  COAST("coast");

  private final String csvValue;

  Biome(String csvValue) {
    this.csvValue = csvValue;
  }

  /** CSV 에 적히는 문자열. */
  public String csvValue() {
    return csvValue;
  }

  /** CSV 문자열로 상수를 찾는다. */
  public static Biome fromCsv(String value) {
    for (Biome candidate : values()) {
      if (candidate.csvValue.equals(value)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("Biome 값이 아니다: " + value);
  }
}
