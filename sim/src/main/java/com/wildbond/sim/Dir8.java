package com.wildbond.sim;

/** 8방향 이동 입력. NONE 은 정지. docs/architecture.md §4.2. */
public enum Dir8 {
  NONE(0, 0),
  N(0, -1),
  NE(1, -1),
  E(1, 0),
  SE(1, 1),
  S(0, 1),
  SW(-1, 1),
  W(-1, 0),
  NW(-1, -1);

  private final int dx;
  private final int dy;

  Dir8(int dx, int dy) {
    this.dx = dx;
    this.dy = dy;
  }

  public int dx() {
    return dx;
  }

  public int dy() {
    return dy;
  }

  /** 대각 방향이면 참 — 속도를 1/sqrt(2) 로 정규화해야 한다. */
  public boolean diagonal() {
    return dx != 0 && dy != 0;
  }
}
