package com.wildbond.sim.systems;

import com.artemis.BaseSystem;
import com.wildbond.sim.events.EventBus;

/** §4.1 시스템 마지막 — 틱 동안 쌓인 이벤트를 이 시점에서만 구독자에게 흘려보낸다. */
public final class EventFlushSystem extends BaseSystem {

  private final EventBus eventBus;

  public EventFlushSystem(EventBus eventBus) {
    this.eventBus = eventBus;
  }

  @Override
  protected void processSystem() {
    eventBus.flush();
  }
}
