package com.wildbond.sim.events;

/**
 * 포획 성공 (docs/architecture.md §3.2). {@code shakes} 는 결과와 무관하게 sim 이 정한 흔들림 횟수(1~3)이고, 클라이언트는 그
 * 횟수만큼 연출만 한다.
 */
public record PalCaptured(
    int palEntityId, int ownerEntityId, int partySlot, int shakes, float x, float y)
    implements SimEvent {}
