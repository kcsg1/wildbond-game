package com.wildbond.sim.systems;

import com.wildbond.data.LootTable;
import com.wildbond.sim.Rng;

/**
 * docs/architecture.md §3.2 전리품 규칙의 순수 부분 — 후보 행 배열에서 가중치로 한 줄을 고른다. DropSystem 이 쓰고, 통계 테스트가 직접
 * 부른다.
 */
public final class LootRoll {

  private LootRoll() {}

  /**
   * @param rows 한 몬스터의 후보 행 (id 오름차순 — 결정성)
   * @return 고른 행. rows 가 비면 null
   */
  public static LootTable pick(LootTable[] rows, Rng rng) {
    if (rows == null || rows.length == 0) {
      return null;
    }
    int total = 0;
    for (LootTable row : rows) {
      total += Math.max(0, row.weight());
    }
    if (total <= 0) {
      return null;
    }
    int roll = rng.nextInt(Rng.Stream.LOOT, total);
    int acc = 0;
    for (LootTable row : rows) {
      acc += Math.max(0, row.weight());
      if (roll < acc) {
        return row;
      }
    }
    return rows[rows.length - 1];
  }

  /** min..max 사이 수량. */
  public static int amount(LootTable row, Rng rng) {
    int span = Math.max(0, row.maxCount() - row.minCount());
    return row.minCount() + (span == 0 ? 0 : rng.nextInt(Rng.Stream.LOOT, span + 1));
  }
}
