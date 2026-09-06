package com.wildbond.sim.events;

public record EntitySpawned(int entityId, float x, float y) implements SimEvent {}
