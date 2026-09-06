package com.wildbond.data.chunk;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 32×32 청크 하나의 정적 지형. ground/detail 은 타일 GID(0=없음), collision 은 타일당 비트마스크 ({@link ChunkFormat}).
 * docs/architecture.md §8.2.
 *
 * <p>배열 컴포넌트가 있는 record 는 기본 equals/hashCode 가 배열을 참조로 비교하므로, 값 비교를 위해 직접 구현한다 (라운드트립 테스트가 이걸 쓴다).
 */
public record Chunk(
    ChunkCoord coord, int[] ground, int[] detail, byte[] collision, List<ChunkObject> objects) {

  public Chunk {
    if (ground.length != ChunkFormat.TILE_COUNT
        || detail.length != ChunkFormat.TILE_COUNT
        || collision.length != ChunkFormat.TILE_COUNT) {
      throw new IllegalArgumentException("레이어 길이는 " + ChunkFormat.TILE_COUNT + " (32x32) 이어야 한다");
    }
    objects = List.copyOf(objects);
  }

  /** 청크 로컬 타일 좌표(0..31)의 배열 인덱스. */
  public static int indexOf(int localTx, int localTy) {
    return localTy * ChunkFormat.SIZE + localTx;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Chunk other)) {
      return false;
    }
    return coord.equals(other.coord)
        && Arrays.equals(ground, other.ground)
        && Arrays.equals(detail, other.detail)
        && Arrays.equals(collision, other.collision)
        && objects.equals(other.objects);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        coord,
        Arrays.hashCode(ground),
        Arrays.hashCode(detail),
        Arrays.hashCode(collision),
        objects);
  }

  @Override
  public String toString() {
    return "Chunk[coord="
        + coord
        + ", objects="
        + objects.size()
        + ", ground="
        + Arrays.toString(ground)
        + "]";
  }
}
