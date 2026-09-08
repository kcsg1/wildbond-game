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
    private float z;
    private int hp;
    private int maxHp;
    private int speciesId;
    private int ownerId;

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

    /** 지면 위 가상 높이 — 포획구 포물선(§3.2). 그 외에는 0. */
    public float z() {
      return z;
    }

    public int hp() {
      return hp;
    }

    public int maxHp() {
      return maxHp;
    }

    /** 팰이면 PalSpecies id, 아니면 -1. */
    public int speciesId() {
      return speciesId;
    }

    /** 주인이 있으면 그 EntityId, 아니면 -1. */
    public int ownerId() {
      return ownerId;
    }
  }

  private Map<Integer, Snapshot> prev = new LinkedHashMap<>();
  private Map<Integer, Snapshot> cur = new LinkedHashMap<>();
  private final Deque<Snapshot> pool = new ArrayDeque<>();
  private final int[] partySlots = new int[SimView.PARTY_SLOTS];

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
      snapshot.z = view.renderZ(id);
      snapshot.hp = view.health(id);
      snapshot.maxHp = view.maxHealth(id);
      snapshot.speciesId = view.speciesId(id);
      snapshot.ownerId = view.ownerId(id);
      cur.put(id, snapshot);
    }

    for (int slot = 0; slot < partySlots.length; slot++) {
      partySlots[slot] = view.partyEntityId(slot);
    }
  }

  /** 현재 틱의 스냅샷들 — EntityRenderer 가 이걸 순회하며 Y-정렬해 그린다. */
  public Collection<Snapshot> current() {
    return cur.values();
  }

  /** 파티 슬롯의 팰 EntityId (비었으면 -1) — HUD 가 읽는다. */
  public int partyEntityId(int slot) {
    return slot >= 0 && slot < partySlots.length ? partySlots[slot] : -1;
  }

  public int partySlotCount() {
    return partySlots.length;
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

  public float prevZ(int id, float fallback) {
    Snapshot s = prev.get(id);
    return s != null ? s.z : fallback;
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
