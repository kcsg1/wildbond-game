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
      cur.put(id, snapshot);
    }
  }

  /** 현재 틱의 스냅샷들 — EntityRenderer 가 이걸 순회하며 Y-정렬해 그린다. */
  public Collection<Snapshot> current() {
    return cur.values();
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
