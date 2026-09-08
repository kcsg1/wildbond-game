package com.wildbond.sim.components;

/**
 * {@link Brain} 이 들고 있는 행동 상태 (docs/architecture.md §9.1 야생 BT). BT 노드가 상태를 읽고 쓰지만, 상태 자체는 컴포넌트
 * 데이터이므로 components 패키지에 둔다.
 */
public enum PalState {
  /** 다음 행동을 아직 고르지 않았다. */
  IDLE,
  /** 목표 타일까지 배회 중 (§9.1 Wander). */
  WANDER,
  /** 배회 후 정지 (§9.1 Wait 3~8s). */
  WAIT,
  /** 위협을 쫓거나 때리는 중 (§9.1 Combat). */
  COMBAT,
  /** 도주 중 (§9.1 Flee). */
  FLEE,
  /** 주인 동행 (파티 팰). */
  FOLLOW
}
