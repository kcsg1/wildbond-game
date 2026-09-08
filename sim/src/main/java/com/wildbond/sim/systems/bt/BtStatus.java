package com.wildbond.sim.systems.bt;

/** BT 노드 한 번 실행의 결과. docs/architecture.md §9.1. */
public enum BtStatus {
  SUCCESS,
  FAILURE,
  /** 이번 틱에는 아직 끝나지 않았다 — 부모는 뒤 형제로 넘어가지 않는다. */
  RUNNING
}
