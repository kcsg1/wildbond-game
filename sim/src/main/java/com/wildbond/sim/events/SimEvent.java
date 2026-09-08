package com.wildbond.sim.events;

/** 도메인 이벤트 — 렌더·오디오가 EventBus 를 구독해 받는다. docs/architecture.md §4.1 EventFlushSystem. */
public sealed interface SimEvent
    permits EntitySpawned,
        EntityMoved,
        Damaged,
        Died,
        ItemDropped,
        CoinsPicked,
        ItemPicked,
        LevelUp {}
