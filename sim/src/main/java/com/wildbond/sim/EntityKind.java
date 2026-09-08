package com.wildbond.sim;

/** SimView 가 노출하는 엔티티 종류. */
public enum EntityKind {
  PLAYER,
  /** 몬스터 — 종은 {@link SimView#speciesId} 로 구분한다. */
  MONSTER,
  /** 바닥에 떨어진 전리품. 플레이어가 다가가면 사라진다. */
  DROP,
  DUMMY,
  UNKNOWN
}
