package com.wildbond.sim.systems.bt;

import java.util.List;

/** 앞에서부터 시도해 처음으로 FAILURE 가 아닌 자식의 결과를 돌려준다 (§9.1 Root Selector). */
public final class Selector<C> implements BtNode<C> {

  private final List<BtNode<C>> children;

  public Selector(List<BtNode<C>> children) {
    this.children = List.copyOf(children);
  }

  @Override
  public BtStatus tick(C context) {
    for (int i = 0; i < children.size(); i++) {
      BtStatus status = children.get(i).tick(context);
      if (status != BtStatus.FAILURE) {
        return status;
      }
    }
    return BtStatus.FAILURE;
  }
}
