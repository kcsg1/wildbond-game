package com.wildbond.sim.components;

import com.artemis.Component;

/**
 * HP 0. 시체는 {@link #ticksRemaining} 동안 남았다가 제거된다 (docs/architecture.md §3.2 사망).
 *
 * <p>죽는 순간 딱 한 번 해야 하는 일(전리품 롤, 경험치)은 여기 플래그로 막는다 — 시체가 5초 남아 있는 동안 매 틱 다시 굴리지 않게.
 */
public final class Dead extends Component {

  public int ticksRemaining;

  /** 마지막으로 때린 엔티티. 경험치를 누가 받는지 정한다. 없으면 -1. */
  public int killerStableId = -1;

  public boolean lootRolled;
  public boolean expAwarded;
}
