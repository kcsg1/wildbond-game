package com.wildbond.sim.bench;

import com.wildbond.data.GameData;
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
 * 헤드리스 sim 부하 테스트 — 엔티티 500개 1000틱, 평균 틱 ≤ 8ms 예산을 검증한다 (docs/architecture.md §9.4).
 *
 * <p>Gradle 태스크 {@code :sim:bench} 로 실행되고, 하네스(harness.ps1) 전체 모드가 자동으로 포함한다. 예산을 넘으면 종료 코드 1 —
 * sim/systems 코드가 아니라 이 클래스만 콘솔 출력을 허용한다(ArchitectureTest 예외).
 */
public final class SimBench {

  private static final int ENTITY_COUNT = 500;
  private static final int TICK_COUNT = 1000;
  private static final double AVG_BUDGET_MS = 8.0;

  private SimBench() {}

  public static void main(String[] args) throws IOException {
    Path tablesDir = Path.of(args.length > 0 ? args[0] : "data/tables");
    GameData gameData = GameData.load(tablesDir);
    ArrayTileMap map = new ArrayTileMap(256, 256, new byte[256 * 256]);
    Sim sim = new Sim(gameData, map, 20260904L);

    List<Command> spawnCommands = new ArrayList<>(ENTITY_COUNT);
    for (int i = 0; i < ENTITY_COUNT; i++) {
      float x = 128f + (i % 32) * 32f;
      float y = 128f + (i / 32) * 32f;
      spawnCommands.add(new Command.SpawnPlayer(x, y));
    }
    sim.step(0, spawnCommands);

    SimView view = sim.view();
    int[] stableIds = new int[view.entityCount()];
    for (int i = 0; i < stableIds.length; i++) {
      stableIds[i] = view.stableIdAt(i);
    }

    Dir8[] dirs = Dir8.values();
    long totalNanos = 0;
    long maxNanos = 0;
    List<Command> moveCommands = new ArrayList<>(ENTITY_COUNT);
    for (int tick = 1; tick <= TICK_COUNT; tick++) {
      moveCommands.clear();
      Dir8 dir = dirs[1 + (tick % 8)];
      for (int stableId : stableIds) {
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

    double avgMs = totalNanos / 1_000_000.0 / TICK_COUNT;
    double maxMs = maxNanos / 1_000_000.0;
    System.out.printf(
        "[sim:bench] entities=%d ticks=%d avg=%.3fms max=%.3fms (예산 %.1fms)%n",
        ENTITY_COUNT, TICK_COUNT, avgMs, maxMs, AVG_BUDGET_MS);

    if (avgMs > AVG_BUDGET_MS) {
      System.err.printf(
          "[sim:bench] 실패 — 평균 틱 %.3fms > 예산 %.1fms (docs/architecture.md §9.4)%n",
          avgMs, AVG_BUDGET_MS);
      System.exit(1);
    }
  }
}
