package com.wildbond.sim;

/** SimView 가 노출하는 엔티티 종류. */
public enum EntityKind {
  PLAYER,
  /** 야생·파티 팰 모두. 주인 여부는 {@link SimView#ownerId} 로 구분한다. */
  PAL,
  /** 날아가는 포획구 — 렌더는 그림자와 가상 높이({@link SimView#renderZ})로 그린다. */
  SPHERE,
  DUMMY,
  UNKNOWN
}
