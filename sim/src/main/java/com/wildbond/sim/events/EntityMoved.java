package com.wildbond.sim.events;

/** 위치가 실제로 바뀐 틱에만 나간다 (막혀서 그대로면 나가지 않는다). */
public record EntityMoved(int entityId, float x, float y) implements SimEvent {}
