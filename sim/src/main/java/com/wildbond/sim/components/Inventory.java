package com.wildbond.sim.components;

import com.artemis.Component;

/**
 * 인벤토리 슬롯 (docs/architecture.md §3.1 Inventory). 고정 슬롯 배열 — 핫 루프 할당 금지 규칙(§4.3)에 맞고, 세이브 포맷(§8.3)도
 * 그대로 직렬화된다. 동전은 여기 넣지 않고 {@link Wallet} 이 센다.
 *
 * <p>슬롯이 비면 itemId 가 0 이다. 스택 규칙은 systems.InventoryOps 가 처리한다(컴포넌트는 데이터만, §4.1).
 */
public final class Inventory extends Component {

  public static final int SLOTS = 20;

  public final int[] itemIds = new int[SLOTS];
  public final int[] counts = new int[SLOTS];
}
