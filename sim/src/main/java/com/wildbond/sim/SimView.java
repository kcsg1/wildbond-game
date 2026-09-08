package com.wildbond.sim;

/**
 * sim 의 읽기 전용 뷰 (docs/architecture.md §4.1, §6 규칙 3). 렌더·오디오는 이 인터페이스만 참조하고, 틱이 끝난 뒤의 스냅샷 값만 돌려받는다
 * — 컴포넌트·Artemis 타입은 노출하지 않는다.
 */
public interface SimView {

  /** 인벤토리 슬롯 수 (§3.1 Inventory). */
  int INVENTORY_SLOTS = 20;

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

  /** 몬스터면 Monster 표의 id, 아니면 -1. */
  int speciesId(int stableId);

  /** Stats 가 있으면 레벨, 아니면 -1. */
  int level(int stableId);

  /** Mana 컴포넌트가 없으면 -1 (§3.1 Player "HP/MP"). */
  int mana(int stableId);

  int maxMana(int stableId);

  /** 소지금. Wallet 컴포넌트가 없으면 -1. */
  int coins(int stableId);

  /** 누적 경험치. Experience 가 없으면 -1. */
  int experience(int stableId);

  /** 다음 레벨까지 필요한 누적 경험치 (§3.2). Experience 가 없으면 -1. */
  int expToNextLevel(int stableId);

  /** 인벤토리 slot 번째 아이템 id. 비었거나 Inventory 가 없으면 0. */
  int inventoryItemId(int stableId, int slot);

  int inventoryCount(int stableId, int slot);

  /** 떨어진 전리품이면 아이템 id, 아니면 -1. */
  int dropItemId(int stableId);

  /** 떨어진 전리품이면 수량, 아니면 -1. */
  int dropAmount(int stableId);

  /** 쓰러지는 연출 진행도 0..1. 살아 있으면 0 — 렌더가 기울이거나 흐리게 하는 데 쓴다. */
  float deathProgress(int stableId);

  /** 마지막으로 처리한 틱 번호. */
  int tick();

  /** 리플레이 결정성 검증용 64bit 혼합 해시 (§4.3). */
  long stateHash();
}
