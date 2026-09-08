package com.wildbond.sim.events;

/** 레벨이 올랐다 (docs/architecture.md §3.2). {@code level} 은 오른 뒤의 레벨. */
public record LevelUp(int entityId, int level) implements SimEvent {}
