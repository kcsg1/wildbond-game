// 생성된 파일 — 손으로 고치지 않는다.
// 원천: data/tables/Skill.csv
// 생성기: tools/datagen (docs/architecture.md §8.1)

package com.wildbond.data;

/** Skill.csv 한 행. */
public record Skill(
    int id,
    String nameKey,
    Element element,
    int power,
    int cooldownTicks,
    int rangePx,
    int castTicks,
    HitShape hitShape,
    int hitW,
    int hitH,
    String vfxRef) {}
