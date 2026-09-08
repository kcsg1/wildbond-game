package com.wildbond.sim.components;

import com.artemis.Component;

/**
 * 쓰러지는 연출에 쓰라고 sim 이 알려 주는 진행도. {@link Dead} 와 함께 붙고, 렌더가 이 값으로 스프라이트를 기울이거나 흐리게 한다 — 규칙이 아니라 표시용이라
 * sim 은 이 값을 판정에 쓰지 않는다.
 */
public final class DeathAnim extends Component {
  public int totalTicks;
  public int elapsedTicks;
}
