package com.wildbond.sim;

/**
 * sim 의 읽기 전용 뷰 (docs/architecture.md §4.1, §6 규칙 3). 렌더·오디오는 이 인터페이스만 참조하고, 틱이 끝난 뒤의 스냅샷 값만 돌려받는다
 * — 컴포넌트·Artemis 타입은 노출하지 않는다.
 */
public interface SimView {

  /** 현재 살아있는 엔티티 수. */
  int entityCount();

  /** 0..entityCount()-1 을 EntityId 오름차순으로 순회하기 위한 접근자 (§4.3). */
  int stableIdAt(int index);

  float x(int stableId);

  float y(int stableId);

  /** Health 컴포넌트가 없으면 -1. */
  int health(int stableId);

  EntityKind kind(int stableId);

  /** 마지막으로 처리한 틱 번호. */
  int tick();

  /** 리플레이 결정성 검증용 64bit 혼합 해시 (§4.3). */
  long stateHash();
}
