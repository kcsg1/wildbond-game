package com.wildbond.sim.components;

import com.artemis.Component;

/** BT 가 틱 사이에 들고 다니는 상태와 타이머 (docs/architecture.md §4.1 {@code Brain}, §9.1). */
public final class Brain extends Component {

  public MonsterState state = MonsterState.IDLE;

  /** 현재 상태가 유지된 틱 수 — 배회가 끝나지 않을 때 강제로 끊는 데 쓴다. */
  public int stateTicks;

  /** WAIT 상태로 남은 틱 수 (§9.1 Wait 3~8s). */
  public int waitTicks;

  /** 감지된 위협(플레이어)의 안정적 id. 없으면 -1. */
  public int threatStableId = -1;

  /**
   * 이 틱이 지나면 위협을 잊는다. passive/timid 는 맞은 뒤 잠깐만 반응해야 하므로 여기에 만료를 적어 둔다(§9.1). aggressive 는 거리로만 잊으므로
   * 쓰지 않는다.
   */
  public int threatExpiresTick;

  /** 다음 감지(레이캐스트)를 수행할 틱 — §9.1 "0.25s 간격". */
  public int nextSenseTick;

  /** 다음 경로 재요청이 가능한 틱 — 틱당 요청 상한(§9.3)을 개체 단위에서도 아끼기 위함. */
  public int nextRepathTick;

  /** PathFollowSystem 이 이 속도로 웨이포인트를 따라간다. 상태에 따라 BT 가 갱신한다. */
  public float speedPxS;
}
