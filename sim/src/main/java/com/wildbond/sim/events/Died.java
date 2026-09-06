package com.wildbond.sim.events;

/** HP 가 0 이 된 엔티티에 CombatSystem 이 발행한다. 실제 월드 제거는 5초 뒤(§4.1) — 이 이벤트는 그 시작 신호다. */
public record Died(int entityId) implements SimEvent {}
