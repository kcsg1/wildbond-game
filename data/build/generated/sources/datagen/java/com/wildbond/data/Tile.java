// 생성된 파일 — 손으로 고치지 않는다.
// 원천: data/tables/Tile.csv
// 생성기: tools/datagen (docs/architecture.md §8.1)

package com.wildbond.data;

/** Tile.csv 한 행. */
public record Tile(
    int id,
    String tileset,
    TileCollision collision,
    Biome biome,
    float temperatureOffset,
    float walkSpeedMult,
    boolean minable,
    Integer itemDrop) {}
