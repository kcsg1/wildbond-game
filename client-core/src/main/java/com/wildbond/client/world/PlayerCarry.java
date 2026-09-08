package com.wildbond.client.world;

import com.wildbond.sim.SimView;

/**
 * 존을 넘어갈 때(그리고 죽어서 마을로 돌아올 때) 들고 가는 플레이어 상태 (docs/architecture.md D-16 "존을 넘어 유지되는 것은 player 상태뿐").
 *
 * <p>존 전환은 sim 을 새로 만드는 것이라 몬스터·드롭 같은 존 로컬 상태는 버려진다. 그 경계를 명시적으로 드러내려고 넘길 값만 이 레코드에 모았다. 새 sim 에는
 * {@code Command.RestorePlayer} 로 실어 나른다 — sim 상태를 밖에서 직접 고치지 않는다(§6).
 *
 * @param hp -1 이면 "레벨에 맞는 최대치로" (새 게임·부활)
 * @param mp -1 이면 최대치
 */
public record PlayerCarry(
    int level, int exp, int hp, int mp, int coins, int[] itemIds, int[] counts) {

  /** 새 게임 — 1레벨, 빈 인벤토리, 만땅. */
  public static PlayerCarry initial() {
    return new PlayerCarry(
        1, 0, -1, -1, 0, new int[SimView.INVENTORY_SLOTS], new int[SimView.INVENTORY_SLOTS]);
  }

  /** 살아 있는 플레이어의 지금 상태를 읽는다. */
  public static PlayerCarry from(SimView view, int playerId) {
    int[] itemIds = new int[SimView.INVENTORY_SLOTS];
    int[] counts = new int[SimView.INVENTORY_SLOTS];
    for (int slot = 0; slot < SimView.INVENTORY_SLOTS; slot++) {
      itemIds[slot] = view.inventoryItemId(playerId, slot);
      counts[slot] = view.inventoryCount(playerId, slot);
    }
    return new PlayerCarry(
        Math.max(1, view.level(playerId)),
        Math.max(0, view.experience(playerId)),
        view.health(playerId),
        view.mana(playerId),
        Math.max(0, view.coins(playerId)),
        itemIds,
        counts);
  }

  /** 죽어서 마을로 돌아올 때 — 진행 상태는 그대로, HP/MP 만 만땅으로 (§3.2 "사망 페널티 없음"). */
  public PlayerCarry revived() {
    return new PlayerCarry(level, exp, -1, -1, coins, itemIds, counts);
  }
}
