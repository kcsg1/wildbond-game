package com.wildbond.sim.components;

/** {@link Brain} 이 들고 있는 행동 상태 (docs/architecture.md §9.1). 표시용이지 판정에 쓰지 않는다. */
public enum MonsterState {
  IDLE,
  WANDER,
  WAIT,
  COMBAT,
  FLEE
}
