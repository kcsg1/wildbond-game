package com.wildbond.sim.systems;

import com.wildbond.sim.components.Inventory;

/** {@link Inventory} 의 스택 규칙 (docs/architecture.md §3.2 전리품). 컴포넌트는 데이터만 갖고, 규칙은 여기 순수 함수로 둔다. */
public final class InventoryOps {

  private InventoryOps() {}

  /**
   * 아이템을 넣는다 — 같은 id 스택을 먼저 채우고, 그다음 빈 슬롯을 쓴다.
   *
   * @return 넣지 못하고 남은 수량 (0 이면 전부 들어갔다)
   */
  public static int add(Inventory inventory, int itemId, int amount, int maxStack) {
    int remaining = amount;
    for (int i = 0; i < Inventory.SLOTS && remaining > 0; i++) {
      if (inventory.itemIds[i] == itemId && inventory.counts[i] < maxStack) {
        int room = maxStack - inventory.counts[i];
        int put = Math.min(room, remaining);
        inventory.counts[i] += put;
        remaining -= put;
      }
    }
    for (int i = 0; i < Inventory.SLOTS && remaining > 0; i++) {
      if (inventory.itemIds[i] == 0) {
        int put = Math.min(maxStack, remaining);
        inventory.itemIds[i] = itemId;
        inventory.counts[i] = put;
        remaining -= put;
      }
    }
    return remaining;
  }

  /** 총 보유 수량. */
  public static int count(Inventory inventory, int itemId) {
    int total = 0;
    for (int i = 0; i < Inventory.SLOTS; i++) {
      if (inventory.itemIds[i] == itemId) {
        total += inventory.counts[i];
      }
    }
    return total;
  }

  /** 슬롯 배열을 통째로 덮어쓴다 (존 전환 복원용). 길이가 모자라면 나머지는 비운다. */
  public static void load(Inventory inventory, int[] itemIds, int[] counts) {
    for (int i = 0; i < Inventory.SLOTS; i++) {
      boolean has = itemIds != null && i < itemIds.length && counts != null && i < counts.length;
      inventory.itemIds[i] = has && counts[i] > 0 ? itemIds[i] : 0;
      inventory.counts[i] = has && itemIds[i] != 0 ? counts[i] : 0;
    }
  }
}
