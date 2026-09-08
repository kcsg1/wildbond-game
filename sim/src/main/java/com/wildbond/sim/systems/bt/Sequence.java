package com.wildbond.sim.systems.bt;

import java.util.List;

/** 앞에서부터 모두 SUCCESS 여야 SUCCESS. 하나라도 FAILURE/RUNNING 이면 거기서 멈춘다 (§9.1 Idle Sequence). */
public final class Sequence<C> implements BtNode<C> {

  private final List<BtNode<C>> children;

  public Sequence(List<BtNode<C>> children) {
    this.children = List.copyOf(children);
  }

  @Override
  public BtStatus tick(C context) {
    for (int i = 0; i < children.size(); i++) {
      BtStatus status = children.get(i).tick(context);
      if (status != BtStatus.SUCCESS) {
        return status;
      }
    }
    return BtStatus.SUCCESS;
  }
}
