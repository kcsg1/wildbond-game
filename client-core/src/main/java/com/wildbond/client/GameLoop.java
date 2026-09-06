package com.wildbond.client;

import com.wildbond.sim.Command;
import com.wildbond.sim.Sim;
import com.wildbond.sim.Ticks;
import java.util.List;

/**
 * 고정 틱 누적기 (docs/architecture.md §5.2). sim 은 메인 스레드에서 동기 호출한다 — 스레드 경계 없음. 반환값은 남은 누적 시간의
 * 비율(0..1)로, 렌더가 prev→cur 를 보간할 때 쓴다.
 */
public final class GameLoop {

  private static final float MAX_FRAME_SECONDS = 0.25f;
  private static final int STATS_WINDOW = 128;

  private final Sim sim;
  private final InputMapper inputMapper;
  private final ViewState viewState;

  private float accumulator;
  private int tick;

  private final float[] tickMillisRing = new float[STATS_WINDOW];
  private int ringIndex;
  private int ringFilled;

  public GameLoop(Sim sim, InputMapper inputMapper, ViewState viewState) {
    this.sim = sim;
    this.inputMapper = inputMapper;
    this.viewState = viewState;
  }

  /** deltaTime(초)을 누적해 필요한 만큼 틱을 진행한다. 보간용 alpha 를 돌려준다. */
  public float advance(float deltaTime) {
    accumulator += Math.min(deltaTime, MAX_FRAME_SECONDS);
    while (accumulator >= Ticks.DT_SECONDS) {
      List<Command> commands = inputMapper.drain();

      long startNanos = System.nanoTime();
      sim.step(tick, commands);
      tick++;
      recordTickMillis((System.nanoTime() - startNanos) / 1_000_000f);

      viewState.capture(sim.view());
      accumulator -= Ticks.DT_SECONDS;
    }
    return accumulator / Ticks.DT_SECONDS;
  }

  private void recordTickMillis(float millis) {
    tickMillisRing[ringIndex] = millis;
    ringIndex = (ringIndex + 1) % STATS_WINDOW;
    if (ringFilled < STATS_WINDOW) {
      ringFilled++;
    }
  }

  /** 최근(최대 {@value #STATS_WINDOW}틱) 평균 틱 시간(ms). */
  public float averageTickMillis() {
    if (ringFilled == 0) {
      return 0f;
    }
    float sum = 0f;
    for (int i = 0; i < ringFilled; i++) {
      sum += tickMillisRing[i];
    }
    return sum / ringFilled;
  }

  /** 최근(최대 {@value #STATS_WINDOW}틱) 최대 틱 시간(ms). */
  public float maxTickMillis() {
    float max = 0f;
    for (int i = 0; i < ringFilled; i++) {
      max = Math.max(max, tickMillisRing[i]);
    }
    return max;
  }

  public int tick() {
    return tick;
  }

  public long stateHash() {
    return sim.view().stateHash();
  }
}
