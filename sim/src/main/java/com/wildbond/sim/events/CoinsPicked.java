package com.wildbond.sim.events;

/** 플레이어가 동전을 주웠다. {@code total} 은 주운 뒤의 소지금이다. */
public record CoinsPicked(int entityId, int amount, int total) implements SimEvent {}
