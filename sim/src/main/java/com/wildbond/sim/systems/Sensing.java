package com.wildbond.sim.systems;

import com.wildbond.data.TileCollision;
import com.wildbond.sim.TileMap;

/** 시야 판정 (docs/architecture.md §9.1 "시야 12타일(solid/cliff 레이캐스트)"). */
public final class Sensing {

  private Sensing() {}

  /**
   * 두 타일 사이가 트여 있는가 — Bresenham 직선 위에 solid/cliff 타일이 하나라도 있으면 거짓.
   *
   * <p>물(water)은 시야를 막지 않는다 — 이동은 못 해도 건너편은 보인다(§9.1 은 solid/cliff 만 든다).
   */
  public static boolean hasLineOfSight(
      TileMap tileMap, int fromTx, int fromTy, int toTx, int toTy) {
    int x = fromTx;
    int y = fromTy;
    int dx = Math.abs(toTx - fromTx);
    int dy = -Math.abs(toTy - fromTy);
    int stepX = fromTx < toTx ? 1 : -1;
    int stepY = fromTy < toTy ? 1 : -1;
    int error = dx + dy;

    while (x != toTx || y != toTy) {
      int doubled = 2 * error;
      if (doubled >= dy) {
        error += dy;
        x += stepX;
      }
      if (doubled <= dx) {
        error += dx;
        y += stepY;
      }
      if (x == toTx && y == toTy) {
        break; // 목표 타일 자체는 막고 있어도 "보인다"고 본다.
      }
      if (blocksSight(tileMap.collision(x, y))) {
        return false;
      }
    }
    return true;
  }

  private static boolean blocksSight(TileCollision collision) {
    return collision == TileCollision.SOLID || collision == TileCollision.CLIFF;
  }
}
