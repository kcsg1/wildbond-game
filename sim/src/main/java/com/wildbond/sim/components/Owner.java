package com.wildbond.sim.components;

import com.artemis.Component;

/** 이 엔티티를 소유한 엔티티의 안정적 id (docs/architecture.md §4.1). 포획 성공 시 CaptureSystem 이 붙인다. */
public final class Owner extends Component {
  public int ownerStableId;
}
