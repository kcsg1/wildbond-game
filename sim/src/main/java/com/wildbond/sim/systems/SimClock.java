package com.wildbond.sim.systems;

/**
 * 시스템들이 공유하는 현재 틱 번호. Artemis 의 {@code World} 는 틱 카운터를 갖지 않고, AI 감지 주기(§9.1 0.25s)·경로 재요청 간격·전투
 * 기억처럼 "몇 틱째인가"가 필요한 곳이 여럿이라 Sim 이 한 곳에 써 주고 시스템들이 읽는다.
 */
public final class SimClock {

  private int tick;

  public void set(int tick) {
    this.tick = tick;
  }

  public int tick() {
    return tick;
  }
}
