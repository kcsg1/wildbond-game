package com.wildbond.sim.components;

import com.artemis.Component;

/**
 * 안정적 정수 id (세이브·이벤트·정렬 기준). Artemis 내부 엔티티 id 는 삭제 후 재사용되므로 별도로 관리한다. docs/architecture.md §4.3,
 * §6.
 */
public final class EntityIdComponent extends Component {
  public int value;
}
