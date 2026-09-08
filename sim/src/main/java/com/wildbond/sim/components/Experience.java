package com.wildbond.sim.components;

import com.artemis.Component;

/** 누적 경험치 (docs/architecture.md §3.2 경험치·레벨). 레벨은 {@link Stats#level} 에 있다. */
public final class Experience extends Component {
  public int exp;
}
