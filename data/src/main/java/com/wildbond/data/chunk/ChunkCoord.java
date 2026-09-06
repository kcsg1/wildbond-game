package com.wildbond.data.chunk;

/** 청크 좌표 (타일 좌표 / {@link ChunkFormat#SIZE}). docs/architecture.md §8.2. */
public record ChunkCoord(int cx, int cy) {

  public static ChunkCoord ofTile(int tileX, int tileY) {
    return new ChunkCoord(
        Math.floorDiv(tileX, ChunkFormat.SIZE), Math.floorDiv(tileY, ChunkFormat.SIZE));
  }
}
