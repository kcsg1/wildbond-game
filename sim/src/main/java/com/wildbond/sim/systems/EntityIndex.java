package com.wildbond.sim.systems;

import java.util.Arrays;

/**
 * 안정적 EntityId(stableId)와 Artemis 내부 엔티티 id 를 잇는다 (docs/architecture.md §4.1, §4.3, §6).
 *
 * <p>발급 순서가 그대로 오름차순이므로(추가만 하고 제거가 없는 한) 별도 정렬 없이 {@link #stableIdAt}/ {@link #artemisIdAt} 로
 * EntityId 오름차순 순회를 만족한다. 조회는 이진 탐색이라 핫 루프에서 할당이 없다.
 */
public final class EntityIndex {

  private int[] stableIds = new int[64];
  private int[] artemisIds = new int[64];
  private int size = 0;
  private int nextStableId = 1;

  /** 새 엔티티를 등록하고 발급한 stableId 를 돌려준다. */
  public int assign(int artemisEntityId) {
    if (size == stableIds.length) {
      stableIds = Arrays.copyOf(stableIds, size * 2);
      artemisIds = Arrays.copyOf(artemisIds, size * 2);
    }
    int stableId = nextStableId++;
    stableIds[size] = stableId;
    artemisIds[size] = artemisEntityId;
    size++;
    return stableId;
  }

  public int size() {
    return size;
  }

  public int stableIdAt(int index) {
    return stableIds[index];
  }

  public int artemisIdAt(int index) {
    return artemisIds[index];
  }

  public int artemisIdOf(int stableId) {
    int idx = Arrays.binarySearch(stableIds, 0, size, stableId);
    if (idx < 0) {
      throw new IllegalArgumentException("EntityId 없음: " + stableId);
    }
    return artemisIds[idx];
  }

  /**
   * 이미 사라진 엔티티를 가리킬 수 있는 곳(명령·AI 가 들고 있던 대상 id)에서 쓴다. 없으면 -1.
   *
   * <p>명령은 sim 밖에서 만들어지고 그 사이에 대상이 죽어 제거될 수 있다 — 그런 명령은 조용히 무시하는 것이 맞다(예외로 sim 을 멈추지 않는다).
   */
  public int artemisIdOrMissing(int stableId) {
    int idx = Arrays.binarySearch(stableIds, 0, size, stableId);
    return idx < 0 ? -1 : artemisIds[idx];
  }

  /** 엔티티를 제거한다(§4.1 Dead 컴포넌트 5초 유예 후). 배열은 정렬 상태를 유지한 채 뒤 원소를 당긴다. */
  public void remove(int stableId) {
    int idx = Arrays.binarySearch(stableIds, 0, size, stableId);
    if (idx < 0) {
      throw new IllegalArgumentException("EntityId 없음: " + stableId);
    }
    int tail = size - idx - 1;
    if (tail > 0) {
      System.arraycopy(stableIds, idx + 1, stableIds, idx, tail);
      System.arraycopy(artemisIds, idx + 1, artemisIds, idx, tail);
    }
    size--;
  }
}
