package com.wildbond.sim.bench;

import com.wildbond.data.GameData;
import com.wildbond.data.TileCollision;
import com.wildbond.sim.ArrayTileMap;
import com.wildbond.sim.Command;
import com.wildbond.sim.Dir8;
import com.wildbond.sim.Sim;
import com.wildbond.sim.SimView;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 헤드리스 sim 부하 테스트 — docs/architecture.md §9.4 예산과 docs/m0-prompts.md 단계7 수용 기준을 검증한다.
 *
 * <ul>
 *   <li>혼잡: 엔티티 500개 1000틱, 평균 틱 ≤ 8ms (§9.4 합계 예산)
 *   <li>팰: 팰 24마리 1000틱, 평균 틱 ≤ 3ms (단계7 수용 기준 "팰 24마리 상황에서 sim 틱 ≤ 3ms")
 * </ul>
 *
 * <p>Gradle 태스크 {@code :sim:bench} 로 실행되고, 하네스(harness.ps1) 전체 모드가 자동으로 포함한다. 하나라도 예산을 넘으면 종료 코드 1
 * — sim/systems 코드가 아니라 이 클래스만 콘솔 출력을 허용한다(ArchitectureTest 예외).
 */
public final class SimBench {

  private static final long SEED = 20260904L;
  private static final int TICK_COUNT = 1000;

  private static final int CROWD_ENTITY_COUNT = 500;
  private static final double CROWD_BUDGET_MS = 8.0;

  private static final int PAL_COUNT = 24;
  private static final double PAL_BUDGET_MS = 3.0;

  /** 30×30 타일이면 어느 구석도 중앙의 플레이어에서 24타일 이상 떨어지지 않아 야생 스폰이 끼어들지 않는다 — 정확히 24마리를 잰다. */
  private static final int PAL_MAP_TILES = 30;

  private SimBench() {}

  /** 한 시나리오의 측정 결과 (ms) 와 끝났을 때 남아 있던 엔티티 수 — 측정 대상이 중간에 사라지지 않았는지 눈으로 확인하기 위함. */
  private record Result(double avgMs, double maxMs, int survivors) {}

  public static void main(String[] args) throws IOException {
    Path tablesDir = Path.of(args.length > 0 ? args[0] : "data/tables");
    GameData gameData = GameData.load(tablesDir);

    Result crowd = runCrowd(gameData);
    System.out.printf(
        "[sim:bench] crowd  entities=%d ticks=%d avg=%.3fms max=%.3fms alive=%d (예산 %.1fms)%n",
        CROWD_ENTITY_COUNT,
        TICK_COUNT,
        crowd.avgMs(),
        crowd.maxMs(),
        crowd.survivors(),
        CROWD_BUDGET_MS);

    Result pals = runPals(gameData);
    System.out.printf(
        "[sim:bench] pals   pals=%d ticks=%d avg=%.3fms max=%.3fms alive=%d (예산 %.1fms)%n",
        PAL_COUNT, TICK_COUNT, pals.avgMs(), pals.maxMs(), pals.survivors(), PAL_BUDGET_MS);

    boolean failed = false;
    if (crowd.avgMs() > CROWD_BUDGET_MS) {
      System.err.printf(
          "[sim:bench] 실패 — crowd 평균 틱 %.3fms > 예산 %.1fms (docs/architecture.md §9.4)%n",
          crowd.avgMs(), CROWD_BUDGET_MS);
      failed = true;
    }
    if (pals.avgMs() > PAL_BUDGET_MS) {
      System.err.printf(
          "[sim:bench] 실패 — pals 평균 틱 %.3fms > 예산 %.1fms (docs/m0-prompts.md 단계7 수용 기준)%n",
          pals.avgMs(), PAL_BUDGET_MS);
      failed = true;
    }
    if (failed) {
      System.exit(1);
    }
  }

  /** 엔티티 500개가 서로 밀어내며 움직이는 혼잡 시나리오 (단계 3 부터의 기준선). */
  private static Result runCrowd(GameData gameData) {
    ArrayTileMap map = new ArrayTileMap(256, 256, new byte[256 * 256]);
    Sim sim = new Sim(gameData, map, SEED);

    List<Command> spawnCommands = new ArrayList<>(CROWD_ENTITY_COUNT);
    for (int i = 0; i < CROWD_ENTITY_COUNT; i++) {
      float x = 128f + (i % 32) * 32f;
      float y = 128f + (i / 32) * 32f;
      spawnCommands.add(new Command.SpawnPlayer(x, y));
    }
    sim.step(0, spawnCommands);
    return measure(sim, collectIds(sim.view()));
  }

  /**
   * 팰 24마리 시나리오 — 감지(레이캐스트)·행동 트리·JPS 경로 탐색·추격이 모두 도는 상태를 잰다 (§9.4 의 팰 AI 2ms + 경로 탐색 1.5ms 가 지배적인
   * 항목이다).
   */
  private static Result runPals(GameData gameData) {
    int size = PAL_MAP_TILES;
    int centerTile = size / 2;
    byte[] grid = new byte[size * size];

    // 플레이어 둘레에 2타일 두께의 해자를 판다. 물은 이동만 막고 시야는 막지 않으므로(§9.1) 팰 24마리가 1000틱 내내
    // "보고 쫓지만 닿지 못하는" 상태로 남는다 — 플레이어가 중간에 죽어 측정이 유휴 상태로 바뀌는 것을 막는다.
    for (int ty = 0; ty < size; ty++) {
      for (int tx = 0; tx < size; tx++) {
        int chebyshev = Math.max(Math.abs(tx - centerTile), Math.abs(ty - centerTile));
        if (chebyshev == 4 || chebyshev == 5) {
          grid[ty * size + tx] = (byte) TileCollision.WATER.ordinal();
        }
      }
    }
    ArrayTileMap map = new ArrayTileMap(size, size, grid);
    Sim sim = new Sim(gameData, map, SEED);

    float center = tileCenter(centerTile);
    List<Command> spawnCommands = new ArrayList<>(PAL_COUNT + 1);
    spawnCommands.add(new Command.SpawnPlayer(center, center));
    for (int i = 0; i < PAL_COUNT; i++) {
      // 해자 바깥, 시야(12타일) 안쪽 두 겹의 고리에 흩어 놓는다.
      double angle = 2 * StrictMath.PI * i / PAL_COUNT;
      float radiusPx = (i % 2 == 0 ? 9f : 11f) * 32f;
      float x = center + (float) StrictMath.cos(angle) * radiusPx;
      float y = center + (float) StrictMath.sin(angle) * radiusPx;
      int speciesId = 1 + (i % 3);
      spawnCommands.add(new Command.SpawnPal(x, y, speciesId, 1 + (i % 5)));
    }
    sim.step(0, spawnCommands);
    return measure(sim, new int[] {sim.view().stableIdAt(0)});
  }

  private static float tileCenter(int tile) {
    return tile * 32f + 16f;
  }

  private static int[] collectIds(SimView view) {
    int[] ids = new int[view.entityCount()];
    for (int i = 0; i < ids.length; i++) {
      ids[i] = view.stableIdAt(i);
    }
    return ids;
  }

  /** movedIds 에 매 틱 MoveInput 을 보내며 1000틱을 돌린다. 사라진 id 의 명령은 sim 이 무시한다. */
  private static Result measure(Sim sim, int[] movedIds) {
    Dir8[] dirs = Dir8.values();
    long totalNanos = 0;
    long maxNanos = 0;
    List<Command> moveCommands = new ArrayList<>(movedIds.length);

    for (int tick = 1; tick <= TICK_COUNT; tick++) {
      moveCommands.clear();
      Dir8 dir = dirs[1 + (tick % 8)];
      for (int stableId : movedIds) {
        moveCommands.add(new Command.MoveInput(stableId, dir, false));
      }

      long start = System.nanoTime();
      sim.step(tick, moveCommands);
      long elapsed = System.nanoTime() - start;

      totalNanos += elapsed;
      if (elapsed > maxNanos) {
        maxNanos = elapsed;
      }
    }
    return new Result(
        totalNanos / 1_000_000.0 / TICK_COUNT, maxNanos / 1_000_000.0, sim.view().entityCount());
  }
}
