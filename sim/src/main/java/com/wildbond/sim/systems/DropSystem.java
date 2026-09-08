package com.wildbond.sim.systems;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntityEdit;
import com.wildbond.data.GameData;
import com.wildbond.data.Item;
import com.wildbond.data.ItemCategory;
import com.wildbond.data.LootTable;
import com.wildbond.sim.Rng;
import com.wildbond.sim.Ticks;
import com.wildbond.sim.components.Dead;
import com.wildbond.sim.components.DroppedItem;
import com.wildbond.sim.components.EntityIdComponent;
import com.wildbond.sim.components.Inventory;
import com.wildbond.sim.components.MonsterData;
import com.wildbond.sim.components.PlayerTag;
import com.wildbond.sim.components.Position;
import com.wildbond.sim.components.Wallet;
import com.wildbond.sim.events.CoinsPicked;
import com.wildbond.sim.events.EventBus;
import com.wildbond.sim.events.ItemDropped;
import com.wildbond.sim.events.ItemPicked;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * §4.1 시스템 6번 — 전리품 (docs/architecture.md §3.2). 몬스터가 죽으면 전리품표에서 한 줄을 뽑아 그 자리에 떨어뜨리고, 플레이어가 다가가면
 * 줍는다: 동전은 소지금, 나머지는 인벤토리.
 *
 * <p>떨어뜨리는 시점은 "죽자마자"다. CombatSystem 은 {@link Dead} 를 붙이고 5초 뒤에 엔티티를 지우는데, 그 사이 시체 위에 드롭이 놓여 있어야
 * 쓰러지는 연출과 보상이 이어져 보인다.
 */
public final class DropSystem extends BaseSystem {

  public static final int DROP_LIFETIME_TICKS = 60 * Ticks.TICKS_PER_SECOND;
  public static final float PICKUP_RADIUS_PX = 28f;

  private final EntityIndex index;
  private final GameData gameData;
  private final EventBus eventBus;
  private final Rng rng;

  /** 몬스터 id → 전리품 후보(id 오름차순). 순회하지 않고 조회만 하므로 HashMap 이어도 결정성에 문제없다(§4.3). */
  private final Map<Integer, LootTable[]> lootByMonster = new HashMap<>();

  private ComponentMapper<Position> mPosition;
  private ComponentMapper<Dead> mDead;
  private ComponentMapper<MonsterData> mMonster;
  private ComponentMapper<DroppedItem> mDrop;
  private ComponentMapper<PlayerTag> mPlayer;
  private ComponentMapper<Wallet> mWallet;
  private ComponentMapper<Inventory> mInventory;
  private ComponentMapper<EntityIdComponent> mEntityId;

  public DropSystem(EntityIndex index, GameData gameData, EventBus eventBus, Rng rng) {
    this.index = index;
    this.gameData = gameData;
    this.eventBus = eventBus;
    this.rng = rng;

    // monster id, 그 안에서 row id 순으로 정렬해 같은 몬스터의 행이 이어지게 한 뒤 구간마다 배열로 자른다 — Map 순회 없이 결정적으로.
    List<LootTable> sorted = new ArrayList<>(gameData.allLootTable());
    sorted.sort(
        (a, b) ->
            a.monster() != b.monster()
                ? Integer.compare(a.monster(), b.monster())
                : Integer.compare(a.id(), b.id()));
    int start = 0;
    while (start < sorted.size()) {
      int monsterId = sorted.get(start).monster();
      int end = start;
      while (end < sorted.size() && sorted.get(end).monster() == monsterId) {
        end++;
      }
      lootByMonster.put(monsterId, sorted.subList(start, end).toArray(new LootTable[0]));
      start = end;
    }
  }

  @Override
  protected void initialize() {
    mPosition = world.getMapper(Position.class);
    mDead = world.getMapper(Dead.class);
    mMonster = world.getMapper(MonsterData.class);
    mDrop = world.getMapper(DroppedItem.class);
    mPlayer = world.getMapper(PlayerTag.class);
    mWallet = world.getMapper(Wallet.class);
    mInventory = world.getMapper(Inventory.class);
    mEntityId = world.getMapper(EntityIdComponent.class);
  }

  @Override
  protected void processSystem() {
    dropFromFreshCorpses();
    expireDrops();
    pickUpNearPlayer();
  }

  private void dropFromFreshCorpses() {
    int n = index.size();
    for (int i = 0; i < n; i++) {
      int artemisId = index.artemisIdAt(i);
      if (!mDead.has(artemisId) || !mMonster.has(artemisId)) {
        continue;
      }
      Dead dead = mDead.get(artemisId);
      if (dead.lootRolled) {
        continue;
      }
      dead.lootRolled = true;

      LootTable[] rows = lootByMonster.get(mMonster.get(artemisId).speciesId);
      LootTable picked = LootRoll.pick(rows, rng);
      if (picked == null) {
        continue;
      }
      Position at = mPosition.get(artemisId);
      spawnDrop(at.x, at.y, picked.item(), LootRoll.amount(picked, rng));
    }
  }

  private void spawnDrop(float x, float y, int itemId, int amount) {
    int artemisId = world.create();
    EntityEdit edit = world.edit(artemisId);

    Position position = edit.create(Position.class);
    position.x = x;
    position.y = y;

    DroppedItem drop = edit.create(DroppedItem.class);
    drop.itemId = itemId;
    drop.amount = amount;
    drop.ticksRemaining = DROP_LIFETIME_TICKS;

    int stableId = index.assign(artemisId);
    edit.create(EntityIdComponent.class).value = stableId;

    eventBus.enqueue(new ItemDropped(stableId, itemId, amount, x, y));
  }

  private void expireDrops() {
    for (int i = index.size() - 1; i >= 0; i--) {
      int artemisId = index.artemisIdAt(i);
      if (!mDrop.has(artemisId)) {
        continue;
      }
      DroppedItem drop = mDrop.get(artemisId);
      drop.ticksRemaining--;
      if (drop.ticksRemaining <= 0) {
        removeEntity(i, artemisId);
      }
    }
  }

  private void pickUpNearPlayer() {
    int playerArtemisId = -1;
    int n = index.size();
    for (int i = 0; i < n; i++) {
      int artemisId = index.artemisIdAt(i);
      if (mPlayer.has(artemisId) && !mDead.has(artemisId)) {
        playerArtemisId = artemisId;
        break;
      }
    }
    if (playerArtemisId < 0) {
      return;
    }
    Position player = mPosition.get(playerArtemisId);
    int playerStableId = mEntityId.get(playerArtemisId).value;

    for (int i = index.size() - 1; i >= 0; i--) {
      int artemisId = index.artemisIdAt(i);
      if (!mDrop.has(artemisId)) {
        continue;
      }
      Position at = mPosition.get(artemisId);
      float dx = at.x - player.x;
      float dy = at.y - player.y;
      if (dx * dx + dy * dy > PICKUP_RADIUS_PX * PICKUP_RADIUS_PX) {
        continue;
      }
      DroppedItem drop = mDrop.get(artemisId);
      Item item = gameData.item(drop.itemId);

      if (item.category() == ItemCategory.CURRENCY) {
        if (!mWallet.has(playerArtemisId)) {
          continue;
        }
        Wallet wallet = mWallet.get(playerArtemisId);
        wallet.coins += drop.amount;
        eventBus.enqueue(new CoinsPicked(playerStableId, drop.amount, wallet.coins));
        removeEntity(i, artemisId);
        continue;
      }

      if (!mInventory.has(playerArtemisId)) {
        continue;
      }
      int leftover =
          InventoryOps.add(
              mInventory.get(playerArtemisId), drop.itemId, drop.amount, item.maxStack());
      int taken = drop.amount - leftover;
      if (taken <= 0) {
        continue; // 인벤토리가 꽉 찼다 — 바닥에 그대로 둔다.
      }
      eventBus.enqueue(new ItemPicked(playerStableId, drop.itemId, taken));
      if (leftover == 0) {
        removeEntity(i, artemisId);
      } else {
        drop.amount = leftover;
      }
    }
  }

  private void removeEntity(int indexPos, int artemisId) {
    int stableId = index.stableIdAt(indexPos);
    world.delete(artemisId);
    index.remove(stableId);
  }
}
