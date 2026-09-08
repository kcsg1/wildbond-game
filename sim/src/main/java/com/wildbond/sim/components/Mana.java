package com.wildbond.sim.components;

import com.artemis.Component;

/** 현재/최대 마나 (docs/architecture.md §3.1 Player "HP/MP"). 스킬이 소모한다. */
public final class Mana extends Component {
  public int current;
  public int max;
}
