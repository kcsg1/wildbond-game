package com.wildbond.sim.components;

import com.artemis.Component;

/**
 * 이 엔티티가 마지막으로 때린 대상 — 파티 팰의 "주인이 공격한 대상을 함께 친다"(docs/m0-prompts.md 단계7) 규칙이 읽는다. CombatSystem 이
 * 데미지를 적용할 때 공격자 쪽에 기록한다.
 */
public final class CombatMemory extends Component {
  public int lastTargetStableId = -1;
  public int lastTargetTick = -1;
}
