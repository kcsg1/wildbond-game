package com.wildbond.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.wildbond.data.TileCollision;
import com.wildbond.sim.events.Damaged;
import com.wildbond.sim.systems.Sensing;
import com.wildbond.sim.systems.SimConstants;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * docs/architecture.md §9.1 야생 팰 BT — 감지(시야 12타일 + solid/cliff 레이캐스트), Combat(Chase → UseSkill),
 * Idle(Wander → Wait).
 *
 * <p>맵은 30×30 으로 두고 플레이어를 한가운데 놓는다 — 어느 구석도 24타일 이상 떨어지지 않아 SpawnSystem 의 야생 스폰이 끼어들지 않는다(§9.1 "화면
 * 밖(≥24타일) 생성").
 */
class WildPalAiTest {

  private static final int SIZE = 30;
  private static final int PLAYER_TX = 15;
  private static final int PLAYER_TY = 15;
  private static final int MOSSLING = 1; // data/tables/PalSpecies.csv pal.mossling

  @Test
  void lineOfSightIsBlockedBySolidAndCliffButNotWater() {
    byte[] grid = new byte[SIZE * SIZE];
    grid[15 * SIZE + 18] = (byte) TileCollision.SOLID.ordinal();
    grid[16 * SIZE + 18] = (byte) TileCollision.CLIFF.ordinal();
    grid[17 * SIZE + 18] = (byte) TileCollision.WATER.ordinal();
    ArrayTileMap map = new ArrayTileMap(SIZE, SIZE, grid);

    assertThat(Sensing.hasLineOfSight(map, 20, 15, 15, 15)).as("solid 은 시야를 막는다").isFalse();
    assertThat(Sensing.hasLineOfSight(map, 20, 16, 15, 16)).as("cliff 도 시야를 막는다").isFalse();
    assertThat(Sensing.hasLineOfSight(map, 20, 17, 15, 17)).as("물은 시야를 막지 않는다").isTrue();
  }

  @Test
  void wildPalDoesNotDetectPlayerBehindAWall() {
    byte[] grid = new byte[SIZE * SIZE];
    for (int ty = 0; ty < SIZE; ty++) {
      grid[ty * SIZE + 18] = (byte) TileCollision.SOLID.ordinal();
    }
    ArrayTileMap map = new ArrayTileMap(SIZE, SIZE, grid);
    Sim sim = TestSupport.newSim(map, 4242L);
    List<Damaged> damaged = subscribeDamaged(sim);

    sim.step(
        0,
        List.of(
            new Command.SpawnPlayer(center(PLAYER_TX), center(PLAYER_TY)),
            new Command.SpawnPal(center(20), center(PLAYER_TY), MOSSLING, 1)));

    for (int t = 1; t <= 120; t++) {
      sim.step(t, List.of());
    }

    assertThat(damaged).as("시야 5타일 거리라도 벽 너머 플레이어는 감지·공격하지 못한다").isEmpty();
  }

  @Test
  void wildPalChasesAndAttacksPlayerInTheOpen() {
    ArrayTileMap map = TestSupport.openMap(SIZE, SIZE);
    Sim sim = TestSupport.newSim(map, 4242L);
    List<Damaged> damaged = subscribeDamaged(sim);

    sim.step(
        0,
        List.of(
            new Command.SpawnPlayer(center(PLAYER_TX), center(PLAYER_TY)),
            new Command.SpawnPal(center(20), center(PLAYER_TY), MOSSLING, 1)));
    SimView view = sim.view();
    int playerId = view.stableIdAt(0);
    int palId = view.stableIdAt(1);
    float startDistance = distance(view, playerId, palId);

    for (int t = 1; t <= 60; t++) {
      sim.step(t, List.of());
    }

    assertThat(distance(view, playerId, palId)).as("접근한다").isLessThan(startDistance);
    assertThat(damaged).as("같은 거리에서 벽이 없으면 다가와 공격한다").isNotEmpty();
    assertThat(damaged.get(0).entityId()).isEqualTo(playerId);
  }

  @Test
  void idleWildPalWandersAround() {
    ArrayTileMap map = TestSupport.openMap(SIZE, SIZE);
    Sim sim = TestSupport.newSim(map, 99L);

    // 시야(12타일) 밖에 두어 위협을 못 느끼게 한다 — Idle(Wander → Wait) 만 돈다.
    sim.step(
        0,
        List.of(
            new Command.SpawnPlayer(center(PLAYER_TX), center(PLAYER_TY)),
            new Command.SpawnPal(center(PLAYER_TX), center(28), MOSSLING, 1)));
    SimView view = sim.view();
    int palId = view.stableIdAt(1);
    float startX = view.x(palId);
    float startY = view.y(palId);

    boolean moved = false;
    for (int t = 1; t <= 200 && !moved; t++) {
      sim.step(t, List.of());
      moved = view.x(palId) != startX || view.y(palId) != startY;
    }

    assertThat(moved).as("위협이 없으면 배회한다 (§9.1 Idle)").isTrue();
    assertThat(distanceFrom(view, palId, center(PLAYER_TX), center(PLAYER_TY)))
        .as("배회 반경(12타일) 밖으로 멀리 벗어나지는 않는다")
        .isLessThan(30f * SimConstants.TILE_SIZE_PX);
  }

  private static float center(int tile) {
    return tile * (float) SimConstants.TILE_SIZE_PX + SimConstants.TILE_SIZE_PX / 2f;
  }

  private static float distance(SimView view, int a, int b) {
    return distanceFrom(view, a, view.x(b), view.y(b));
  }

  private static float distanceFrom(SimView view, int entityId, float x, float y) {
    float dx = view.x(entityId) - x;
    float dy = view.y(entityId) - y;
    return (float) StrictMath.sqrt(dx * dx + dy * dy);
  }

  private static List<Damaged> subscribeDamaged(Sim sim) {
    List<Damaged> damaged = new ArrayList<>();
    sim.subscribe(
        event -> {
          if (event instanceof Damaged d) {
            damaged.add(d);
          }
        });
    return damaged;
  }
}
