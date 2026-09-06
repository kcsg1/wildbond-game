package com.wildbond.sim.components;

import com.artemis.Component;

/**
 * 투사체 스킬(hit_shape=projectile)이 만든 엔티티. CombatSystem 이 매 틱 이동·충돌을 직접 처리한다 — 일반 엔티티처럼 MovementSystem
 * 을 타지 않는다(밀어내기·타일 슬라이딩 규칙이 투사체에는 맞지 않아서, §4.1).
 */
public final class Projectile extends Component {
  /** 쏜 엔티티의 안정적 id — Artemis 내부 id 는 삭제 후 재사용될 수 있어 직접 저장하지 않는다. */
  public int ownerStableId;

  public int skillId;
  public float dirX;
  public float dirY;
  public float speedPxS;
  public float traveledPx;
  public float maxRangePx;
  public float hitRadiusPx;
}
