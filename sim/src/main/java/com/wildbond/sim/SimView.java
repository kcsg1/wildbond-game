package com.wildbond.sim;

/**
 * sim 의 읽기 전용 뷰 (docs/architecture.md §4.1, §6 규칙 3). 렌더·오디오는 이 인터페이스만 참조하고, 틱이 끝난 뒤의 스냅샷 값만 돌려받는다
 * — 컴포넌트·Artemis 타입은 노출하지 않는다.
 */
public interface SimView {

  /** 파티 슬롯 수 (§3.1 "파티(팰 5)"). */
  int PARTY_SLOTS = 5;

  /** 현재 살아있는 엔티티 수. */
  int entityCount();

  /** 0..entityCount()-1 을 EntityId 오름차순으로 순회하기 위한 접근자 (§4.3). */
  int stableIdAt(int index);

  float x(int stableId);

  float y(int stableId);

  /** Health 컴포넌트가 없으면 -1. */
  int health(int stableId);

  /** Health 컴포넌트가 없으면 -1. HP 바를 그리려면 필요하다. */
  int maxHealth(int stableId);

  EntityKind kind(int stableId);

  /** 팰이면 PalSpecies id, 아니면 -1. */
  int speciesId(int stableId);

  /** 팰이면 개체 레벨, 아니면 -1. */
  int level(int stableId);

  /** 주인이 있으면 그 EntityId, 없으면(야생·플레이어 등) -1. */
  int ownerId(int stableId);

  /** 지면 위로 뜬 가상 높이(포획구 포물선). 대부분의 엔티티는 0 (§3.2). */
  float renderZ(int stableId);

  /** 플레이어 파티의 slot 번째 팰 EntityId. 비었으면 -1. */
  int partyEntityId(int slot);

  /** 마지막으로 처리한 틱 번호. */
  int tick();

  /** 리플레이 결정성 검증용 64bit 혼합 해시 (§4.3). */
  long stateHash();
}
