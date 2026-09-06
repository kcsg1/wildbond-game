package com.wildbond.sim.components;

import com.artemis.Component;

/** AABB 충돌체. width/height 는 Position 중심 기준 전체 폭·높이(px). docs/architecture.md §4.1. */
public final class Collider extends Component {
  public float width;
  public float height;
  public int layer;
}
