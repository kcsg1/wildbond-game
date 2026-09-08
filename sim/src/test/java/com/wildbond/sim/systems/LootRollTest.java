package com.wildbond.sim.systems;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.wildbond.data.LootTable;
import com.wildbond.sim.Rng;
import org.junit.jupiter.api.Test;

/** docs/architecture.md §3.2 전리품 규칙 — "사슴 N마리 처치 → 동전:가죽 비율이 전리품표 가중치 ±3%p" 통계 테스트. */
class LootRollTest {

  private static final int DEER = 1;
  private static final int COIN = 1;
  private static final int DEER_HIDE = 2;
  private static final int DEER_MEAT = 3;

  /** data/tables/LootTable.csv 의 사슴 행과 같은 가중치 — 동전 60 / 가죽 30 / 고기 10. */
  private static final LootTable[] DEER_ROWS = {
    new LootTable(1, DEER, COIN, 60, 3, 8),
    new LootTable(2, DEER, DEER_HIDE, 30, 1, 1),
    new LootTable(3, DEER, DEER_MEAT, 10, 1, 2),
  };

  @Test
  void weightedPickMatchesTableRatiosWithinThreePoints() {
    Rng rng = new Rng(4242L);
    int samples = 20_000;
    int coins = 0;
    int hides = 0;
    int meats = 0;
    for (int i = 0; i < samples; i++) {
      LootTable picked = LootRoll.pick(DEER_ROWS, rng);
      switch (picked.item()) {
        case COIN -> coins++;
        case DEER_HIDE -> hides++;
        case DEER_MEAT -> meats++;
        default -> throw new AssertionError("표에 없는 아이템 " + picked.item());
      }
    }
    assertThat(coins / (double) samples).isCloseTo(0.60, within(0.03));
    assertThat(hides / (double) samples).isCloseTo(0.30, within(0.03));
    assertThat(meats / (double) samples).isCloseTo(0.10, within(0.03));
  }

  @Test
  void amountStaysInsideMinMax() {
    Rng rng = new Rng(7L);
    LootTable coinRow = DEER_ROWS[0];
    for (int i = 0; i < 1000; i++) {
      int amount = LootRoll.amount(coinRow, rng);
      assertThat(amount).isBetween(coinRow.minCount(), coinRow.maxCount());
    }
    assertThat(LootRoll.amount(DEER_ROWS[1], rng)).as("min == max 면 난수를 쓰지 않는다").isEqualTo(1);
  }

  @Test
  void emptyRowsPickNothing() {
    Rng rng = new Rng(1L);
    assertThat(LootRoll.pick(new LootTable[0], rng)).isNull();
    assertThat(LootRoll.pick(null, rng)).isNull();
  }

  @Test
  void sameSeedSameSequence() {
    Rng a = new Rng(99L);
    Rng b = new Rng(99L);
    for (int i = 0; i < 200; i++) {
      assertThat(LootRoll.pick(DEER_ROWS, a).id()).isEqualTo(LootRoll.pick(DEER_ROWS, b).id());
    }
  }
}
