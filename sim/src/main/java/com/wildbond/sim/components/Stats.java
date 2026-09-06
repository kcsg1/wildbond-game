package com.wildbond.sim.components;

import com.artemis.Component;

/** 전투 스탯. docs/architecture.md §3.2 데미지 공식, §4.1. */
public final class Stats extends Component {
  public int atk;
  public int def;
  public int level;
}
