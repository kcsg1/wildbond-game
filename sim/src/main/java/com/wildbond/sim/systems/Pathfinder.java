package com.wildbond.sim.systems;

import com.wildbond.data.TileCollision;
import com.wildbond.sim.TileMap;
import com.wildbond.sim.components.PathComponent;

/**
 * 타일 그리드 경로 탐색 (docs/architecture.md §9.3) — 8방향 A* 와 JPS 두 가지를 같은 비용 모델로 제공한다.
 *
 * <ul>
 *   <li>코너 컷 금지: 대각 이동은 인접한 두 직교 타일이 모두 walkable 일 때만 허용한다
 *   <li>경로 길이 상한 {@link PathComponent#MAX_TILES} (§9.3 "경로 길이 상한 200")
 *   <li>틱당 요청 상한 {@link #MAX_REQUESTS_PER_TICK} (§9.3)
 * </ul>
 *
 * <p>비용은 정수(직교 {@value #STRAIGHT_COST} / 대각 {@value #DIAGONAL_COST})로 계산한다 — float 누적 오차 없이 결정적이고,
 * A* 와 JPS 의 결과 비용을 그대로 비교할 수 있다.
 *
 * <p>탐색은 시작·목표를 감싸는 고정 크기 창(최대 {@value #WINDOW_TILES}×{@value #WINDOW_TILES}) 안에서만 한다. 배열을 미리 잡아 두고
 * 세대 스탬프로 재사용하므로 요청마다 할당이 없다(§4.3). 창을 벗어나는 먼 목표는 실패로 처리한다 — §9.3 의 경로 길이 상한과 같은 취지다.
 */
public final class Pathfinder {

  /** §9.3 "틱당 요청 상한 40". */
  public static final int MAX_REQUESTS_PER_TICK = 40;

  public static final int STRAIGHT_COST = 10;
  public static final int DIAGONAL_COST = 14;

  private static final int WINDOW_TILES = 96;
  private static final int WINDOW_CELLS = WINDOW_TILES * WINDOW_TILES;

  /**
   * 시작·목표 바운딩 박스를 이만큼 넓혀 우회 여유를 준다. 값이 작으면 긴 장애물(연못·절벽 줄기)을 돌아가는 경로가 창 밖으로 밀려 실패한다 — 감지 반경 12타일의 두
   * 배를 잡아 두었다.
   */
  private static final int WINDOW_MARGIN_TILES = 24;

  private static final int NO_PARENT = -1;

  private static final int[] DIR_X = {0, 1, 1, 1, 0, -1, -1, -1};
  private static final int[] DIR_Y = {-1, -1, 0, 1, 1, 1, 0, -1};

  private final TileMap tileMap;

  private final int[] gCost = new int[WINDOW_CELLS];
  private final int[] fCost = new int[WINDOW_CELLS];
  private final int[] parent = new int[WINDOW_CELLS];
  private final int[] openStamp = new int[WINDOW_CELLS];
  private final int[] closedStamp = new int[WINDOW_CELLS];
  private final int[] heap = new int[WINDOW_CELLS];

  private final int[] walkStamp = new int[WINDOW_CELLS];
  private final boolean[] walkCache = new boolean[WINDOW_CELLS];

  private final int[] outX = new int[PathComponent.MAX_TILES];
  private final int[] outY = new int[PathComponent.MAX_TILES];

  private int generation;
  private int heapSize;
  private int originTx;
  private int originTy;
  private int windowW;
  private int windowH;
  private int goalIndex;
  private int goalTx;
  private int goalTy;

  private int outLength;
  private int lastCost;
  private int requestsThisTick;

  public Pathfinder(TileMap tileMap) {
    this.tileMap = tileMap;
  }

  /** Sim 이 틱 시작에 호출한다 — 틱당 요청 상한(§9.3)을 되돌린다. */
  public void beginTick() {
    requestsThisTick = 0;
  }

  /** 이번 틱에 아직 경로를 요청할 수 있는가. */
  public boolean canRequest() {
    return requestsThisTick < MAX_REQUESTS_PER_TICK;
  }

  /**
   * 틱당 상한을 소비하며 경로를 찾는다 (AI 가 쓰는 진입점 — §9.3 대로 JPS 를 쓴다).
   *
   * @return 경로를 찾았으면 참. 결과는 {@link #pathLength()}/{@link #pathTileX}/{@link #pathTileY} 로 읽는다
   */
  public boolean requestPath(int startTx, int startTy, int goalTileX, int goalTileY) {
    if (!canRequest()) {
      return false;
    }
    requestsThisTick++;
    return findPathJps(startTx, startTy, goalTileX, goalTileY);
  }

  /** 8방향 A*. 상한을 소비하지 않는다 — 테스트와 JPS 비교용. */
  public boolean findPathAStar(int startTx, int startTy, int goalTileX, int goalTileY) {
    return search(startTx, startTy, goalTileX, goalTileY, false);
  }

  /** 8방향 JPS. 상한을 소비하지 않는다. */
  public boolean findPathJps(int startTx, int startTy, int goalTileX, int goalTileY) {
    return search(startTx, startTy, goalTileX, goalTileY, true);
  }

  public int pathLength() {
    return outLength;
  }

  public int pathTileX(int i) {
    return outX[i];
  }

  public int pathTileY(int i) {
    return outY[i];
  }

  /** 마지막으로 찾은 경로의 총 비용(직교 10 / 대각 14 단위). A* 와 JPS 가 같아야 한다. */
  public int lastCost() {
    return lastCost;
  }

  public boolean walkable(int tileX, int tileY) {
    if (!inWindow(tileX, tileY)) {
      return isWalkableUncached(tileX, tileY);
    }
    int idx = indexOf(tileX, tileY);
    if (walkStamp[idx] != generation) {
      walkStamp[idx] = generation;
      walkCache[idx] = isWalkableUncached(tileX, tileY);
    }
    return walkCache[idx];
  }

  private boolean isWalkableUncached(int tileX, int tileY) {
    return tileMap.collision(tileX, tileY) == TileCollision.NONE;
  }

  // ---------------------------------------------------------------- 탐색 본체

  private boolean search(int startTx, int startTy, int goalTileX, int goalTileY, boolean jps) {
    outLength = 0;
    lastCost = 0;
    if (!prepareWindow(startTx, startTy, goalTileX, goalTileY)) {
      return false;
    }
    if (!walkable(startTx, startTy) || !walkable(goalTileX, goalTileY)) {
      return false;
    }
    this.goalTx = goalTileX;
    this.goalTy = goalTileY;
    this.goalIndex = indexOf(goalTileX, goalTileY);

    int startIndex = indexOf(startTx, startTy);
    if (startIndex == goalIndex) {
      return false; // 이미 목표 타일 — 경로가 필요 없다.
    }

    heapSize = 0;
    gCost[startIndex] = 0;
    fCost[startIndex] = heuristic(startTx, startTy);
    parent[startIndex] = NO_PARENT;
    openStamp[startIndex] = generation;
    heapPush(startIndex);

    while (heapSize > 0) {
      int current = heapPop();
      if (closedStamp[current] == generation) {
        continue;
      }
      closedStamp[current] = generation;
      if (current == goalIndex) {
        return reconstruct(startIndex);
      }
      if (jps) {
        expandJps(current);
      } else {
        expandAStar(current);
      }
    }
    return false;
  }

  private boolean prepareWindow(int startTx, int startTy, int goalTileX, int goalTileY) {
    int minTx = Math.min(startTx, goalTileX) - WINDOW_MARGIN_TILES;
    int minTy = Math.min(startTy, goalTileY) - WINDOW_MARGIN_TILES;
    int maxTx = Math.max(startTx, goalTileX) + WINDOW_MARGIN_TILES;
    int maxTy = Math.max(startTy, goalTileY) + WINDOW_MARGIN_TILES;

    int width = maxTx - minTx + 1;
    int height = maxTy - minTy + 1;
    if (width > WINDOW_TILES || height > WINDOW_TILES) {
      return false; // 창보다 먼 목표 — §9.3 경로 길이 상한과 같은 취지로 포기한다.
    }
    originTx = minTx;
    originTy = minTy;
    windowW = width;
    windowH = height;
    generation++;
    return true;
  }

  private boolean inWindow(int tileX, int tileY) {
    int lx = tileX - originTx;
    int ly = tileY - originTy;
    return lx >= 0 && ly >= 0 && lx < windowW && ly < windowH;
  }

  private int indexOf(int tileX, int tileY) {
    return (tileY - originTy) * windowW + (tileX - originTx);
  }

  private int tileXOf(int index) {
    return originTx + index % windowW;
  }

  private int tileYOf(int index) {
    return originTy + index / windowW;
  }

  private int heuristic(int tileX, int tileY) {
    int dx = Math.abs(tileX - goalTx);
    int dy = Math.abs(tileY - goalTy);
    return STRAIGHT_COST * (dx + dy) + (DIAGONAL_COST - 2 * STRAIGHT_COST) * Math.min(dx, dy);
  }

  private void expandAStar(int current) {
    int cx = tileXOf(current);
    int cy = tileYOf(current);
    for (int d = 0; d < 8; d++) {
      int nx = cx + DIR_X[d];
      int ny = cy + DIR_Y[d];
      if (!inWindow(nx, ny) || !walkable(nx, ny)) {
        continue;
      }
      boolean diagonal = DIR_X[d] != 0 && DIR_Y[d] != 0;
      if (diagonal && !(walkable(nx, cy) && walkable(cx, ny))) {
        continue; // 코너 컷 금지 (§9.3)
      }
      relax(current, nx, ny, diagonal ? DIAGONAL_COST : STRAIGHT_COST);
    }
  }

  private void relax(int current, int nx, int ny, int stepCost) {
    int neighbor = indexOf(nx, ny);
    if (closedStamp[neighbor] == generation) {
      return;
    }
    int tentative = gCost[current] + stepCost;
    if (openStamp[neighbor] == generation && tentative >= gCost[neighbor]) {
      return;
    }
    openStamp[neighbor] = generation;
    gCost[neighbor] = tentative;
    fCost[neighbor] = tentative + heuristic(nx, ny);
    parent[neighbor] = current;
    heapPush(neighbor);
  }

  // ------------------------------------------------------------------- JPS
  // PathFinding.js 의 "대각 이동은 장애물이 없을 때만"(=코너 컷 금지) 변형과 같은 가지치기·점프 규칙이다.

  private void expandJps(int current) {
    int cx = tileXOf(current);
    int cy = tileYOf(current);
    int parentIndex = parent[current];

    if (parentIndex == NO_PARENT) {
      for (int d = 0; d < 8; d++) {
        int nx = cx + DIR_X[d];
        int ny = cy + DIR_Y[d];
        if (!walkable(nx, ny)) {
          continue;
        }
        if (DIR_X[d] != 0 && DIR_Y[d] != 0 && !(walkable(nx, cy) && walkable(cx, ny))) {
          continue;
        }
        jumpFrom(current, cx, cy, DIR_X[d], DIR_Y[d]);
      }
      return;
    }

    int px = tileXOf(parentIndex);
    int py = tileYOf(parentIndex);
    int dx = Integer.signum(cx - px);
    int dy = Integer.signum(cy - py);

    if (dx != 0 && dy != 0) {
      boolean vertical = walkable(cx, cy + dy);
      boolean horizontal = walkable(cx + dx, cy);
      if (vertical) {
        jumpFrom(current, cx, cy, 0, dy);
      }
      if (horizontal) {
        jumpFrom(current, cx, cy, dx, 0);
      }
      if (vertical && horizontal) {
        jumpFrom(current, cx, cy, dx, dy);
      }
    } else if (dx != 0) {
      boolean next = walkable(cx + dx, cy);
      boolean top = walkable(cx, cy - 1);
      boolean bottom = walkable(cx, cy + 1);
      if (next) {
        jumpFrom(current, cx, cy, dx, 0);
        if (top) {
          jumpFrom(current, cx, cy, dx, -1);
        }
        if (bottom) {
          jumpFrom(current, cx, cy, dx, 1);
        }
      }
      if (top) {
        jumpFrom(current, cx, cy, 0, -1);
      }
      if (bottom) {
        jumpFrom(current, cx, cy, 0, 1);
      }
    } else {
      boolean next = walkable(cx, cy + dy);
      boolean right = walkable(cx + 1, cy);
      boolean left = walkable(cx - 1, cy);
      if (next) {
        jumpFrom(current, cx, cy, 0, dy);
        if (right) {
          jumpFrom(current, cx, cy, 1, dy);
        }
        if (left) {
          jumpFrom(current, cx, cy, -1, dy);
        }
      }
      if (right) {
        jumpFrom(current, cx, cy, 1, 0);
      }
      if (left) {
        jumpFrom(current, cx, cy, -1, 0);
      }
    }
  }

  private void jumpFrom(int current, int cx, int cy, int dx, int dy) {
    int jumped = jump(cx + dx, cy + dy, cx, cy);
    if (jumped < 0) {
      return;
    }
    int jx = tileXOf(jumped);
    int jy = tileYOf(jumped);
    relax(current, jx, jy, stepCostBetween(cx, cy, jx, jy));
  }

  /** 점프 결과 타일의 창 인덱스, 없으면 -1. */
  private int jump(int x, int y, int px, int py) {
    if (!inWindow(x, y) || !walkable(x, y)) {
      return -1;
    }
    if (x == goalTx && y == goalTy) {
      return indexOf(x, y);
    }
    int dx = x - px;
    int dy = y - py;

    if (dx != 0 && dy != 0) {
      // 대각 진행 중에는 직교 방향에 점프 포인트가 있으면 여기가 점프 포인트다.
      if (jump(x + dx, y, x, y) >= 0 || jump(x, y + dy, x, y) >= 0) {
        return indexOf(x, y);
      }
    } else if (dx != 0) {
      if ((walkable(x, y - 1) && !walkable(x - dx, y - 1))
          || (walkable(x, y + 1) && !walkable(x - dx, y + 1))) {
        return indexOf(x, y); // 강제 이웃
      }
    } else {
      if ((walkable(x - 1, y) && !walkable(x - 1, y - dy))
          || (walkable(x + 1, y) && !walkable(x + 1, y - dy))) {
        return indexOf(x, y);
      }
    }

    if (dx != 0 && dy != 0) {
      // 코너 컷 금지 — 두 직교 이웃이 모두 열려 있어야 대각으로 더 나아간다.
      if (!(walkable(x + dx, y) && walkable(x, y + dy))) {
        return -1;
      }
    }
    return jump(x + dx, y + dy, x, y);
  }

  private static int stepCostBetween(int fromX, int fromY, int toX, int toY) {
    int dx = Math.abs(toX - fromX);
    int dy = Math.abs(toY - fromY);
    int diagonal = Math.min(dx, dy);
    int straight = Math.max(dx, dy) - diagonal;
    return diagonal * DIAGONAL_COST + straight * STRAIGHT_COST;
  }

  // ------------------------------------------------------------- 결과 복원

  /** parent 사슬을 타일 단위 경로로 펼친다(JPS 의 점프 포인트 사이도 모두 채운다). 상한을 넘으면 실패. */
  private boolean reconstruct(int startIndex) {
    int count = 0;
    int cursor = goalIndex;
    // 먼저 역순으로 담고 뒤집는다. 점프 포인트 사이는 일정한 방향으로 한 칸씩 채운다.
    while (cursor != startIndex) {
      int parentIndex = parent[cursor];
      int cxTile = tileXOf(cursor);
      int cyTile = tileYOf(cursor);
      int pxTile = tileXOf(parentIndex);
      int pyTile = tileYOf(parentIndex);
      int stepX = Integer.signum(pxTile - cxTile);
      int stepY = Integer.signum(pyTile - cyTile);

      int x = cxTile;
      int y = cyTile;
      while (x != pxTile || y != pyTile) {
        if (count >= outX.length) {
          return false; // §9.3 경로 길이 상한 초과
        }
        outX[count] = x;
        outY[count] = y;
        count++;
        x += stepX;
        y += stepY;
      }
      cursor = parentIndex;
    }
    if (count == 0) {
      return false;
    }

    for (int i = 0, j = count - 1; i < j; i++, j--) {
      int tmpX = outX[i];
      int tmpY = outY[i];
      outX[i] = outX[j];
      outY[i] = outY[j];
      outX[j] = tmpX;
      outY[j] = tmpY;
    }
    outLength = count;
    lastCost = gCost[goalIndex];
    return true;
  }

  // ------------------------------------------------- f 비용 이진 힙 (동점은 인덱스 오름차순)

  private void heapPush(int node) {
    int child = heapSize++;
    heap[child] = node;
    while (child > 0) {
      int parentSlot = (child - 1) >>> 1;
      if (less(heap[child], heap[parentSlot])) {
        swap(child, parentSlot);
        child = parentSlot;
      } else {
        break;
      }
    }
  }

  private int heapPop() {
    int top = heap[0];
    heapSize--;
    heap[0] = heap[heapSize];
    int slot = 0;
    while (true) {
      int left = slot * 2 + 1;
      int right = left + 1;
      int smallest = slot;
      if (left < heapSize && less(heap[left], heap[smallest])) {
        smallest = left;
      }
      if (right < heapSize && less(heap[right], heap[smallest])) {
        smallest = right;
      }
      if (smallest == slot) {
        break;
      }
      swap(slot, smallest);
      slot = smallest;
    }
    return top;
  }

  /** 동점일 때 창 인덱스로 갈라 결정성을 보장한다 (§4.3). */
  private boolean less(int a, int b) {
    if (fCost[a] != fCost[b]) {
      return fCost[a] < fCost[b];
    }
    return a < b;
  }

  private void swap(int a, int b) {
    int tmp = heap[a];
    heap[a] = heap[b];
    heap[b] = tmp;
  }
}
