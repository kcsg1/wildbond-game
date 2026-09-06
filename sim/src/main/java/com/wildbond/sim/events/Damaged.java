package com.wildbond.sim.events;

/** CombatSystem 이 데미지를 적용할 때마다 발행한다(§4.1). x,y 는 피격 시점 대상 위치 — 렌더가 떠오르는 숫자를 그 자리에 띄운다. */
public record Damaged(int entityId, int amount, float x, float y, boolean critical)
    implements SimEvent {}
