package com.wildbond.data.chunk;

import com.wildbond.data.TileCollision;

/** .wbc 청크 파일 포맷 상수. docs/architecture.md §8.2. */
public final class ChunkFormat {

  public static final String MAGIC = "WBC1";
  public static final int VERSION = 1;

  /** 청크 한 변의 타일 수. */
  public static final int SIZE = 32;

  public static final int TILE_COUNT = SIZE * SIZE;

  public static final int SOLID_BIT = 0x1;
  public static final int WATER_BIT = 0x2;
  public static final int CLIFF_BIT = 0x4;
  public static final int EDGE_BIT = 0x8;

  private ChunkFormat() {}

  /** Tile.csv 의 단일 충돌 값을 collision 레이어 비트로 바꾼다. */
  public static int collisionBit(TileCollision collision) {
    return switch (collision) {
      case NONE -> 0;
      case SOLID -> SOLID_BIT;
      case WATER -> WATER_BIT;
      case CLIFF -> CLIFF_BIT;
    };
  }

  /**
   * collision 레이어 비트를 단일 충돌 값으로 되돌린다. 여러 비트가 겹치면 이동을 가장 강하게 막는 순서 (solid &gt; cliff &gt; water)로
   * 우선한다. edge 비트는 이동 판정과 무관하다.
   */
  public static TileCollision collisionFromBits(int bits) {
    if ((bits & SOLID_BIT) != 0) {
      return TileCollision.SOLID;
    }
    if ((bits & CLIFF_BIT) != 0) {
      return TileCollision.CLIFF;
    }
    if ((bits & WATER_BIT) != 0) {
      return TileCollision.WATER;
    }
    return TileCollision.NONE;
  }
}
