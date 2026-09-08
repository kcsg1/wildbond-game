package com.wildbond.sim.components;

import com.artemis.Component;

/** 소지금 (docs/architecture.md §3.1 Player "소지금"). 바닥의 동전을 주우면 늘어난다. */
public final class Wallet extends Component {
  public int coins;
}
