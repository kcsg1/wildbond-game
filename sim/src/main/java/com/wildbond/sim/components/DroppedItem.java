package com.wildbond.sim.components;

import com.artemis.Component;

/**
 * 바닥에 떨어진 아이템 (docs/architecture.md §3.1 DroppedItem). 지금은 동전만 쓴다 — 팰이 쓰러지면 그 자리에 떨어지고, 플레이어가 가까이
 * 가면 사라지면서 {@link Wallet} 이 늘어난다.
 */
public final class DroppedItem extends Component {
  /** data/tables/Item.csv 의 id. 동전은 아직 표에 없어 amount 만 쓴다. */
  public int itemId;

  public int amount;

  /** 남은 수명 — 0 이 되면 사라진다(바닥이 드롭으로 뒤덮이지 않게). */
  public int ticksRemaining;
}
