package com.wildbond.sim.events;

import java.util.ArrayList;
import java.util.List;

/**
 * 틱 안에서 쌓인 이벤트를 EventFlushSystem 이 틱 끝에 한 번에 구독자에게 넘긴다 (docs/architecture.md §4.1). 렌더는 이것을 구독하지
 * 않고도 매 틱 상태를 읽을 수 있으므로, 이동 이벤트는 구독자가 있을 때만 쌓는다 (500 엔티티 벤치에서 불필요한 할당을 피하기 위함).
 */
public final class EventBus {

  public interface Listener {
    void onEvent(SimEvent event);
  }

  private final List<SimEvent> buffer = new ArrayList<>();
  private final List<Listener> listeners = new ArrayList<>();

  public void subscribe(Listener listener) {
    listeners.add(listener);
  }

  public boolean hasSubscribers() {
    return !listeners.isEmpty();
  }

  public void enqueue(SimEvent event) {
    buffer.add(event);
  }

  /** EventFlushSystem 전용 — 등록 순서대로 구독자에게 전달한 뒤 비운다. */
  public void flush() {
    for (int i = 0; i < buffer.size(); i++) {
      SimEvent event = buffer.get(i);
      for (int j = 0; j < listeners.size(); j++) {
        listeners.get(j).onEvent(event);
      }
    }
    buffer.clear();
  }
}
