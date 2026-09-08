package com.wildbond.sim.systems.bt;

/** 상태를 바꾸지 않고 참/거짓만 보는 잎 노드. 참이면 SUCCESS, 거짓이면 FAILURE. */
public final class Condition<C> implements BtNode<C> {

  /** 컨텍스트를 읽어 판정하는 술어. */
  @FunctionalInterface
  public interface Predicate<C> {
    boolean test(C context);
  }

  private final Predicate<C> predicate;

  public Condition(Predicate<C> predicate) {
    this.predicate = predicate;
  }

  @Override
  public BtStatus tick(C context) {
    return predicate.test(context) ? BtStatus.SUCCESS : BtStatus.FAILURE;
  }
}
