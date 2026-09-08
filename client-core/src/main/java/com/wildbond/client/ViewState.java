package com.wildbond.client;

import com.wildbond.sim.EntityKind;
import com.wildbond.sim.SimView;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 틱 끝마다 SimView 에서 엔티티 상태를 prev/cur 두 벌로 복사한다 (docs/architecture.md §5.2, §6 규칙 3). 렌더러는 이것만 읽고 sim
 * 을 직접 참조하지 않는다. {@link Snapshot} 인스턴스는 틱마다 새로 만들지 않고 prev 로 밀려난 것을 재사용한다.
 */
public final class ViewState {

  /** 한 엔티티의 틱 끝 스냅샷. 필드는 클래스 안에서만 바뀐다. */
  public static final class Snapshot {
    private int id;
    private EntityKind kind;
    private float x;
    private float y;
    private int hp;
    private int maxHp;
    private int speciesId;
    private int level;
    private int mp;
    private int maxMp;
    private int coins;
    private int exp;
    private int expToNext;
    private int dropItemId;
    private int dropAmount;
    private float deathProgress;

    /** 플레이어에게만 채운다 — 다른 엔티티는 null 로 둬서 스냅샷마다 배열을 들고 다니지 않게. */
    private int[] inventoryItemIds;

    private int[] inventoryCounts;

    public int id() {
      return id;
    }

    public EntityKind kind() {
      return kind;
    }

    public float x() {
      return x;
    }

    public float y() {
      return y;
    }

    public int hp() {
      return hp;
    }

    public int maxHp() {
      return maxHp;
    }

    /** 몬스터면 Monster 표의 id, 아니면 -1. */
    public int speciesId() {
      return speciesId;
    }

    /** Stats 가 있으면 레벨, 아니면 -1. */
    public int level() {
      return level;
    }

    public int mp() {
      return mp;
    }

    public int maxMp() {
      return maxMp;
    }

    /** 소지금 (플레이어만). 없으면 -1. */
    public int coins() {
      return coins;
    }

    /** 누적 경험치 (플레이어만). 없으면 -1. */
    public int exp() {
      return exp;
    }

    /** 다음 레벨까지 필요한 경험치. 없으면 -1. */
    public int expToNext() {
      return expToNext;
    }

    /** 떨어진 전리품이면 아이템 id, 아니면 -1. */
    public int dropItemId() {
      return dropItemId;
    }

    /** 떨어진 전리품이면 수량, 아니면 -1. */
    public int dropAmount() {
      return dropAmount;
    }

    /** 쓰러지는 연출 진행도 0..1. */
    public float deathProgress() {
      return deathProgress;
    }

    /** 인벤토리 slot 의 아이템 id. 비었거나 인벤토리가 없으면 0. */
    public int inventoryItemId(int slot) {
      return inventoryItemIds == null || slot < 0 || slot >= inventoryItemIds.length
          ? 0
          : inventoryItemIds[slot];
    }

    public int inventoryCount(int slot) {
      return inventoryCounts == null || slot < 0 || slot >= inventoryCounts.length
          ? 0
          : inventoryCounts[slot];
    }
  }

  private Map<Integer, Snapshot> prev = new LinkedHashMap<>();
  private Map<Integer, Snapshot> cur = new LinkedHashMap<>();
  private final Deque<Snapshot> pool = new ArrayDeque<>();

  public void capture(SimView view) {
    pool.addAll(prev.values());
    prev.clear();

    Map<Integer, Snapshot> swap = prev;
    prev = cur;
    cur = swap;

    int n = view.entityCount();
    for (int i = 0; i < n; i++) {
      int id = view.stableIdAt(i);
      Snapshot snapshot = pool.isEmpty() ? new Snapshot() : pool.poll();
      snapshot.id = id;
      snapshot.kind = view.kind(id);
      snapshot.x = view.x(id);
      snapshot.y = view.y(id);
      snapshot.hp = view.health(id);
      snapshot.maxHp = view.maxHealth(id);
      snapshot.speciesId = view.speciesId(id);
      snapshot.level = view.level(id);
      snapshot.mp = view.mana(id);
      snapshot.maxMp = view.maxMana(id);
      snapshot.coins = view.coins(id);
      snapshot.exp = view.experience(id);
      snapshot.expToNext = view.expToNextLevel(id);
      snapshot.dropItemId = view.dropItemId(id);
      snapshot.dropAmount = view.dropAmount(id);
      snapshot.deathProgress = view.deathProgress(id);
      captureInventory(view, id, snapshot);
      cur.put(id, snapshot);
    }
  }

  private static void captureInventory(SimView view, int id, Snapshot snapshot) {
    if (snapshot.kind != EntityKind.PLAYER) {
      snapshot.inventoryItemIds = null;
      snapshot.inventoryCounts = null;
      return;
    }
    if (snapshot.inventoryItemIds == null) {
      snapshot.inventoryItemIds = new int[SimView.INVENTORY_SLOTS];
      snapshot.inventoryCounts = new int[SimView.INVENTORY_SLOTS];
    }
    for (int slot = 0; slot < SimView.INVENTORY_SLOTS; slot++) {
      snapshot.inventoryItemIds[slot] = view.inventoryItemId(id, slot);
      snapshot.inventoryCounts[slot] = view.inventoryCount(id, slot);
    }
  }

  /** 현재 틱의 스냅샷들 — EntityRenderer 가 이걸 순회하며 Y-정렬해 그린다. */
  public Collection<Snapshot> current() {
    return cur.values();
  }

  /** id 의 현재 틱 스냅샷. 없으면 null. */
  public Snapshot snapshot(int id) {
    return cur.get(id);
  }

  /** id 가 직전 틱에도 있었으면 그 x, 없으면(막 스폰됨) fallback. */
  public float prevX(int id, float fallback) {
    Snapshot s = prev.get(id);
    return s != null ? s.x : fallback;
  }

  public float prevY(int id, float fallback) {
    Snapshot s = prev.get(id);
    return s != null ? s.y : fallback;
  }

  /** 현재(cur) 틱의 x — 카메라가 플레이어를 따라가는 등, 보간과 무관하게 최신 값이 필요할 때 쓴다. */
  public float curX(int id, float fallback) {
    Snapshot s = cur.get(id);
    return s != null ? s.x : fallback;
  }

  public float curY(int id, float fallback) {
    Snapshot s = cur.get(id);
    return s != null ? s.y : fallback;
  }
}
