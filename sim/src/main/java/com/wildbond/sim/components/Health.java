package com.wildbond.sim.components;

import com.artemis.Component;

/** 현재/최대 체력. docs/architecture.md §4.1. */
public final class Health extends Component {
  public int current;
  public int max;
}
