// 생성된 파일 — 손으로 고치지 않는다.
// 원천: data/tables/Item.csv
// 생성기: tools/datagen (docs/architecture.md §8.1)

package com.wildbond.data;

/** Item.csv 한 행. */
public record Item(
    int id,
    String nameKey,
    ItemCategory category,
    int maxStack,
    float weight,
    int rarity,
    Integer toolPower,
    Integer foodValue,
    Integer sphereTier,
    Float sphereMultiplier,
    String icon) {}
