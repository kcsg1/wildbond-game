package com.wildbond.sim;

import com.wildbond.data.TileCollision;

/**
 * 테스트·벤치용 평면 타일맵. collision 배열의 값은 {@link TileCollision#ordinal()} 과 같은 순서 (NONE=0, SOLID=1,
 * WATER=2, CLIFF=3, docs/architecture.md §8.1 enums.csv 참고)다.
 */
public final class ArrayTileMap implements TileMap {

  private static final TileCollision[] CODES = TileCollision.values();

  private final int width;
  private final int height;
  private final byte[] collision;

  public ArrayTileMap(int width, int height, byte[] collision) {
    if (collision.length != width * height) {
      throw new IllegalArgumentException("collision 배열 길이가 width*height 와 다르다");
    }
    this.width = width;
    this.height = height;
    this.collision = collision;
  }

  @Override
  public TileCollision collision(int tileX, int tileY) {
    if (tileX < 0 || tileY < 0 || tileX >= width || tileY >= height) {
      return TileCollision.SOLID;
    }
    return CODES[collision[tileY * width + tileX]];
  }
}
