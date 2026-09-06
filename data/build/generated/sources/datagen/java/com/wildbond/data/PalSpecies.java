// 생성된 파일 — 손으로 고치지 않는다.
// 원천: data/tables/PalSpecies.csv
// 생성기: tools/datagen (docs/architecture.md §8.1)

package com.wildbond.data;

/** PalSpecies.csv 한 행. */
public record PalSpecies(
    int id,
    String nameKey,
    Element element1,
    Element element2,
    Temperament temperament,
    int baseHp,
    int baseAtk,
    int baseDef,
    int[] work,
    Integer partnerSkill,
    int[] skills,
    int footprint,
    boolean rideable,
    float rideSpeed,
    float captureRate,
    int expYield,
    String spriteAtlas) {}
