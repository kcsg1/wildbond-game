package com.wildbond.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.wildbond.data.TileCollision;
import com.wildbond.sim.systems.SimConstants;
import java.util.List;
import org.junit.jupiter.api.Test;

class MovementCollisionTest {

  private static final int WIDTH = 20;
  private static final int HEIGHT = 20;

  @Test
  void doesNotPassThroughSolidWall() {
    byte[] grid = new byte[WIDTH * HEIGHT];
    int wallTx = 6;
    for (int ty = 0; ty < HEIGHT; ty++) {
      grid[ty * WIDTH + wallTx] = (byte) TileCollision.SOLID.ordinal();
    }
    ArrayTileMap map = new ArrayTileMap(WIDTH, HEIGHT, grid);
    Sim sim = TestSupport.newSim(map, 1L);

    float startX = 5 * SimConstants.TILE_SIZE_PX + 16f;
    float startY = 5 * SimConstants.TILE_SIZE_PX + 16f;
    sim.step(0, List.of(new Command.SpawnPlayer(startX, startY)));
    int stableId = sim.view().stableIdAt(0);

    for (int t = 1; t <= 10; t++) {
      sim.step(t, List.of(new Command.MoveInput(stableId, Dir8.E, false)));
    }

    float finalX = sim.view().x(stableId);
    assertThat(finalX + SimConstants.PLAYER_WIDTH_PX / 2f)
        .as("플레이어 오른쪽 모서리가 벽(tx=%d) 왼쪽 경계를 넘지 않는다", wallTx)
        .isLessThanOrEqualTo(wallTx * (float) SimConstants.TILE_SIZE_PX);
  }

  @Test
  void slidesAlongOneAxisAtCorner() {
    byte[] grid = new byte[WIDTH * HEIGHT];
    int wallTx = 6;
    int wallTy = 6;
    grid[wallTy * WIDTH + wallTx] = (byte) TileCollision.SOLID.ordinal();
    ArrayTileMap map = new ArrayTileMap(WIDTH, HEIGHT, grid);
    Sim sim = TestSupport.newSim(map, 1L);

    // 대각선으로 벽 타일의 북서쪽 모서리를 향해 접근한다.
    float startX = 5 * SimConstants.TILE_SIZE_PX + 16f;
    float startY = startX;
    sim.step(0, List.of(new Command.SpawnPlayer(startX, startY)));
    int stableId = sim.view().stableIdAt(0);

    for (int t = 1; t <= 8; t++) {
      sim.step(t, List.of(new Command.MoveInput(stableId, Dir8.SE, false)));
    }

    SimView view = sim.view();
    float finalX = view.x(stableId);
    float finalY = view.y(stableId);

    float rowBoundary =
        wallTy * (float) SimConstants.TILE_SIZE_PX - SimConstants.PLAYER_HEIGHT_PX / 2f;
    assertThat(finalY).as("Y축은 코너에 막혀 벽 타일 행으로 넘어가지 못한다").isLessThan(rowBoundary + 0.01f);
    assertThat(finalX).as("X축은 막히지 않아 계속 미끄러졌다").isGreaterThan(startX + 10f);
    assertThat(finalY - startY)
        .as("한 축만 미끄러졌다 — Y축 이동량이 X축 이동량보다 뚜렷하게 작다")
        .isLessThan(finalX - startX);
  }
}
