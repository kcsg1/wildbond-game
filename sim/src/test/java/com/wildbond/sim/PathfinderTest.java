package com.wildbond.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.wildbond.data.TileCollision;
import com.wildbond.sim.systems.Pathfinder;
import org.junit.jupiter.api.Test;

/** docs/architecture.md §9.3 — 8방향 A* / JPS, 코너 컷 금지, 경로 길이 상한. */
class PathfinderTest {

  private static final int WIDTH = 40;
  private static final int HEIGHT = 40;

  @Test
  void aStarRoutesAroundThePond() {
    byte[] grid = new byte[WIDTH * HEIGHT];
    // 세로로 긴 연못 — 직선으로는 갈 수 없고 위아래로 돌아가야 한다.
    int pondTx = 20;
    for (int ty = 5; ty <= 30; ty++) {
      grid[ty * WIDTH + pondTx] = (byte) TileCollision.WATER.ordinal();
      grid[ty * WIDTH + pondTx + 1] = (byte) TileCollision.WATER.ordinal();
    }
    ArrayTileMap map = new ArrayTileMap(WIDTH, HEIGHT, grid);
    Pathfinder pathfinder = new Pathfinder(map);

    assertThat(pathfinder.findPathAStar(15, 20, 25, 20)).as("연못을 돌아가는 경로가 있다").isTrue();

    for (int i = 0; i < pathfinder.pathLength(); i++) {
      assertThat(map.collision(pathfinder.pathTileX(i), pathfinder.pathTileY(i)))
          .as("경로의 어느 타일도 물이 아니다 (%d번째)", i)
          .isEqualTo(TileCollision.NONE);
    }
    assertThat(pathfinder.pathTileX(pathfinder.pathLength() - 1)).isEqualTo(25);
    assertThat(pathfinder.pathTileY(pathfinder.pathLength() - 1)).isEqualTo(20);
  }

  @Test
  void jpsFindsSameCostPathAsPlainAStar() {
    byte[] grid = new byte[WIDTH * HEIGHT];
    // 지그재그 벽 — 최단 경로가 자명하지 않아야 두 알고리즘 비교가 의미 있다.
    for (int ty = 0; ty <= 30; ty++) {
      grid[ty * WIDTH + 12] = (byte) TileCollision.SOLID.ordinal();
    }
    for (int ty = 9; ty < HEIGHT; ty++) {
      grid[ty * WIDTH + 22] = (byte) TileCollision.SOLID.ordinal();
    }
    for (int tx = 26; tx <= 33; tx++) {
      grid[15 * WIDTH + tx] = (byte) TileCollision.CLIFF.ordinal();
    }
    ArrayTileMap map = new ArrayTileMap(WIDTH, HEIGHT, grid);
    Pathfinder pathfinder = new Pathfinder(map);

    assertThat(pathfinder.findPathAStar(3, 20, 35, 20)).isTrue();
    int aStarCost = pathfinder.lastCost();

    assertThat(pathfinder.findPathJps(3, 20, 35, 20)).isTrue();
    int jpsCost = pathfinder.lastCost();

    assertThat(jpsCost).as("JPS 도 최적이므로 A* 와 같은 비용이 나와야 한다").isEqualTo(aStarCost);

    for (int i = 0; i < pathfinder.pathLength(); i++) {
      assertThat(map.collision(pathfinder.pathTileX(i), pathfinder.pathTileY(i)))
          .isEqualTo(TileCollision.NONE);
    }
  }

  @Test
  void neverCutsCorners() {
    byte[] grid = new byte[WIDTH * HEIGHT];
    // (10,10) 의 남동쪽으로 빠져나가려면 (11,10) 과 (10,11) 중 하나는 열려 있어야 하는데 둘 다 막는다.
    grid[10 * WIDTH + 11] = (byte) TileCollision.SOLID.ordinal();
    grid[11 * WIDTH + 10] = (byte) TileCollision.SOLID.ordinal();
    ArrayTileMap map = new ArrayTileMap(WIDTH, HEIGHT, grid);
    Pathfinder pathfinder = new Pathfinder(map);

    assertThat(pathfinder.findPathAStar(10, 10, 11, 11)).isTrue();

    int prevX = 10;
    int prevY = 10;
    for (int i = 0; i < pathfinder.pathLength(); i++) {
      int x = pathfinder.pathTileX(i);
      int y = pathfinder.pathTileY(i);
      int dx = x - prevX;
      int dy = y - prevY;
      if (dx != 0 && dy != 0) {
        assertThat(map.collision(x, prevY))
            .as("대각 이동 (%d,%d)->(%d,%d) 의 가로 이웃이 막혀 있으면 코너 컷이다", prevX, prevY, x, y)
            .isEqualTo(TileCollision.NONE);
        assertThat(map.collision(prevX, y)).isEqualTo(TileCollision.NONE);
      }
      prevX = x;
      prevY = y;
    }
    assertThat(pathfinder.pathLength()).as("코너를 돌아가야 하므로 대각선 한 칸(1)보다 길다").isGreaterThan(1);
  }

  @Test
  void failsWhenTheGoalIsUnreachableOrTooFar() {
    byte[] grid = new byte[WIDTH * HEIGHT];
    for (int ty = 0; ty < HEIGHT; ty++) {
      grid[ty * WIDTH + 20] = (byte) TileCollision.SOLID.ordinal();
    }
    ArrayTileMap map = new ArrayTileMap(WIDTH, HEIGHT, grid);
    Pathfinder pathfinder = new Pathfinder(map);

    assertThat(pathfinder.findPathAStar(5, 5, 30, 5)).as("맵을 가로지르는 벽 너머로는 갈 수 없다").isFalse();
    assertThat(pathfinder.findPathJps(5, 5, 30, 5)).isFalse();
    assertThat(pathfinder.findPathAStar(5, 5, 5, 5)).as("목표가 현재 타일이면 경로가 없다").isFalse();

    ArrayTileMap huge = new ArrayTileMap(400, 400, new byte[400 * 400]);
    Pathfinder far = new Pathfinder(huge);
    assertThat(far.findPathAStar(5, 5, 350, 350)).as("탐색 창을 벗어나는 먼 목표는 포기한다 (§9.3)").isFalse();
  }

  @Test
  void tickRequestBudgetIsEnforced() {
    ArrayTileMap map = new ArrayTileMap(WIDTH, HEIGHT, new byte[WIDTH * HEIGHT]);
    Pathfinder pathfinder = new Pathfinder(map);
    pathfinder.beginTick();

    int granted = 0;
    for (int i = 0; i < Pathfinder.MAX_REQUESTS_PER_TICK * 2; i++) {
      if (pathfinder.requestPath(5, 5, 15, 15)) {
        granted++;
      }
    }
    assertThat(granted).as("§9.3 틱당 요청 상한").isEqualTo(Pathfinder.MAX_REQUESTS_PER_TICK);
    assertThat(pathfinder.canRequest()).isFalse();

    pathfinder.beginTick();
    assertThat(pathfinder.canRequest()).as("다음 틱에 상한이 되돌아온다").isTrue();
  }
}
