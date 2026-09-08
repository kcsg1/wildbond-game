package com.wildbond.sim.events;

/** 포획 실패 — 팰은 던진 사람을 위협으로 삼아 Combat 상태가 된다 (docs/m0-prompts.md 단계7). */
public record PalCaptureFailed(int palEntityId, int throwerEntityId, int shakes, float x, float y)
    implements SimEvent {}
