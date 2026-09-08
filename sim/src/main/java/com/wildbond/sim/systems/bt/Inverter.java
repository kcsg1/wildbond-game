package com.wildbond.sim.systems.bt;

/** 자식의 SUCCESS ↔ FAILURE 를 뒤집는다. RUNNING 은 그대로 통과시킨다. */
public final class Inverter<C> implements BtNode<C> {

  private final BtNode<C> child;

  public Inverter(BtNode<C> child) {
    this.child = child;
  }

  @Override
  public BtStatus tick(C context) {
    return switch (child.tick(context)) {
      case SUCCESS -> BtStatus.FAILURE;
      case FAILURE -> BtStatus.SUCCESS;
      case RUNNING -> BtStatus.RUNNING;
    };
  }
}
