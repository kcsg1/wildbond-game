package com.wildbond.sim.events;

/** 플레이어가 아이템을 주워 인벤토리에 넣었다 (동전은 {@link CoinsPicked}). */
public record ItemPicked(int entityId, int itemId, int amount) implements SimEvent {}
