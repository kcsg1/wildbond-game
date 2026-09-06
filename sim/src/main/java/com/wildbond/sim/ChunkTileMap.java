package com.wildbond.sim;

import com.wildbond.data.TileCollision;
import com.wildbond.data.chunk.Chunk;
import com.wildbond.data.chunk.ChunkCoord;
import com.wildbond.data.chunk.ChunkFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 청크 기반 TileMap 구현. 청크 좌표당 한 번만 로더를 호출하고 결과를 캐시한다(단계 4, docs/architecture.md §8.2). 청크 수가 적어(최대
 * 32×32) 캐시 조회만 하는 이 맵은 sim 의 "HashMap 순회 금지" 규칙과 무관하다 — 그 규칙은 매 틱 엔티티 순회의 결정성을 위한 것이고, 여기는 순회하지
 * 않는다(조회/생성만).
 */
public final class ChunkTileMap implements TileMap {

  private final Function<ChunkCoord, Chunk> loader;
  private final Map<ChunkCoord, Chunk> cache = new HashMap<>();

  public ChunkTileMap(Function<ChunkCoord, Chunk> loader) {
    this.loader = loader;
  }

  @Override
  public TileCollision collision(int tileX, int tileY) {
    ChunkCoord coord = ChunkCoord.ofTile(tileX, tileY);
    Chunk chunk = cache.computeIfAbsent(coord, loader);
    if (chunk == null) {
      return TileCollision.SOLID; // 청크가 없는 곳(월드 밖)은 막아서 안전하게 처리한다.
    }
    int localTx = Math.floorMod(tileX, ChunkFormat.SIZE);
    int localTy = Math.floorMod(tileY, ChunkFormat.SIZE);
    int bits = chunk.collision()[Chunk.indexOf(localTx, localTy)] & 0xFF;
    return ChunkFormat.collisionFromBits(bits);
  }
}
