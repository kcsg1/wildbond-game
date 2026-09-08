package com.wildbond.sim.events;

/** 바닥에 전리품이 떨어졌다 — 렌더가 반짝임·튀어오르는 연출에 쓴다. */
public record ItemDropped(int entityId, int itemId, int amount, float x, float y)
    implements SimEvent {}
