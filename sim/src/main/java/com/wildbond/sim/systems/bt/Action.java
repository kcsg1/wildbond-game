package com.wildbond.sim.systems.bt;

/** 실제로 상태를 바꾸는 잎 노드 (속도 설정·경로 요청·스킬 발동 등). */
public final class Action<C> implements BtNode<C> {

  /** 컨텍스트를 조작하고 결과를 돌려주는 동작. */
  @FunctionalInterface
  public interface Behavior<C> {
    BtStatus run(C context);
  }

  private final Behavior<C> behavior;

  public Action(Behavior<C> behavior) {
    this.behavior = behavior;
  }

  @Override
  public BtStatus tick(C context) {
    return behavior.run(context);
  }
}
