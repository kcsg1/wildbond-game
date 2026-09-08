package com.wildbond.sim.systems;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntityEdit;
import com.wildbond.sim.EntityKind;
import com.wildbond.sim.Rng;
import com.wildbond.sim.Ticks;
import com.wildbond.sim.components.Dead;
import com.wildbond.sim.components.DroppedItem;
import com.wildbond.sim.components.EntityIdComponent;
import com.wildbond.sim.components.PalData;
import com.wildbond.sim.components.PlayerTag;
import com.wildbond.sim.components.Position;
import com.wildbond.sim.components.Wallet;
import com.wildbond.sim.events.CoinsPicked;
import com.wildbond.sim.events.EventBus;
import com.wildbond.sim.events.ItemDropped;

/**
 * 전리품 — 팰이 쓰러지면 그 자리에 동전을 떨어뜨리고, 플레이어가 다가가면 줍는다 (docs/architecture.md §3.1 DroppedItem).
 *
 * <p>떨어뜨리는 시점은 "죽자마자"다. CombatSystem 은 {@link Dead} 를 붙이고 5초 뒤에 엔티티를 지우는데, 그 사이 시체 위에 드롭이 놓여 있어야
 * 쓰러지는 연출과 보상이 이어져 보인다. 금액은 종의 경험치 산출값에 비례시켜 loot 스트림으로 흔든다.
 */
public final class DropSystem extends BaseSystem {

  private final EntityIndex index;
  private final com.wildbond.data.GameData gameData;
  private final EventBus eventBus;
  private final Rng rng;

  private ComponentMapper<Position> mPosition;
  private ComponentMapper<Dead> mDead;
  private ComponentMapper<PalData> mPal;
  private ComponentMapper<DroppedItem> mDrop;
  private ComponentMapper<PlayerTag> mPlayer;
  private ComponentMapper<Wallet> mWallet;
  private ComponentMapper<EntityIdComponent> mEntityId;

  /** 이미 전리품을 내놓은 시체 — 5초 유예 동안 매 틱 떨어뜨리지 않도록 표시해 둔다. */
  private int[] looted = new int[16];

  private int lootedCount;

  public DropSystem(
      EntityIndex index, com.wildbond.data.GameData gameData, EventBus eventBus, Rng rng) {
    this.index = index;
    this.gameData = gameData;
    this.eventBus = eventBus;
    this.rng = rng;
  }

  @Override
  protected void initialize() {
    mPosition = world.getMapper(Position.class);
    mDead = world.getMapper(Dead.class);
    mPal = world.getMapper(PalData.class);
    mDrop = world.getMapper(DroppedItem.class);
    mPlayer = world.getMapper(PlayerTag.class);
    mWallet = world.getMapper(Wallet.class);
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
      if (!mDead.has(artemisId) || !mPal.has(artemisId)) {
        continue;
      }
      int stableId = index.stableIdAt(i);
      if (alreadyLooted(stableId)) {
        continue;
      }
      markLooted(stableId);

      PalData pal = mPal.get(artemisId);
      int expYield = gameData.palSpecies(pal.speciesId).expYield();
      int base = Math.max(1, expYield / 10 + pal.level);
      int amount = base + rng.nextInt(Rng.Stream.LOOT, Math.max(1, base));
      Position at = mPosition.get(artemisId);
      spawnCoins(at.x, at.y, amount);
    }
  }

  private void spawnCoins(float x, float y, int amount) {
    int artemisId = world.create();
    EntityEdit edit = world.edit(artemisId);

    Position position = edit.create(Position.class);
    position.x = x;
    position.y = y;

    DroppedItem drop = edit.create(DroppedItem.class);
    drop.itemId = DropConstants.COIN_ITEM_ID;
    drop.amount = amount;
    drop.ticksRemaining = DropConstants.DROP_LIFETIME_TICKS;

    int stableId = index.assign(artemisId);
    edit.create(EntityIdComponent.class).value = stableId;

    eventBus.enqueue(new ItemDropped(stableId, amount, x, y));
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
      if (mPlayer.has(index.artemisIdAt(i))) {
        playerArtemisId = index.artemisIdAt(i);
        break;
      }
    }
    if (playerArtemisId < 0 || !mWallet.has(playerArtemisId)) {
      return;
    }
    Position player = mPosition.get(playerArtemisId);
    float radius = DropConstants.PICKUP_RADIUS_PX;

    for (int i = index.size() - 1; i >= 0; i--) {
      int artemisId = index.artemisIdAt(i);
      if (!mDrop.has(artemisId)) {
        continue;
      }
      Position at = mPosition.get(artemisId);
      float dx = at.x - player.x;
      float dy = at.y - player.y;
      if (dx * dx + dy * dy > radius * radius) {
        continue;
      }
      DroppedItem drop = mDrop.get(artemisId);
      Wallet wallet = mWallet.get(playerArtemisId);
      wallet.coins += drop.amount;
      eventBus.enqueue(
          new CoinsPicked(mEntityId.get(playerArtemisId).value, drop.amount, wallet.coins));
      removeEntity(i, artemisId);
    }
  }

  private void removeEntity(int indexPos, int artemisId) {
    int stableId = index.stableIdAt(indexPos);
    world.delete(artemisId);
    index.remove(stableId);
  }

  private boolean alreadyLooted(int stableId) {
    for (int i = 0; i < lootedCount; i++) {
      if (looted[i] == stableId) {
        return true;
      }
    }
    return false;
  }

  private void markLooted(int stableId) {
    if (lootedCount == looted.length) {
      looted = java.util.Arrays.copyOf(looted, lootedCount * 2);
    }
    looted[lootedCount++] = stableId;
  }

  /** SimView 가 드롭을 구분할 수 있게 하는 도우미 — EntityQueries 가 쓴다. */
  public static EntityKind kindOfDrop() {
    return EntityKind.DROP;
  }

  /** 전리품 튜닝 상수. */
  public static final class DropConstants {
    /** 동전은 아직 Item.csv 에 없다 — 표에 넣을 때 이 상수를 그 id 로 바꾼다. */
    public static final int COIN_ITEM_ID = 0;

    public static final int DROP_LIFETIME_TICKS = 60 * Ticks.TICKS_PER_SECOND;
    public static final float PICKUP_RADIUS_PX = 28f;

    private DropConstants() {}
  }
}
