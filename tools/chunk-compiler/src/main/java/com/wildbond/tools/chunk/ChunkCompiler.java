package com.wildbond.tools.chunk;

import com.wildbond.data.GameData;
import com.wildbond.data.TileCollision;
import com.wildbond.data.chunk.Chunk;
import com.wildbond.data.chunk.ChunkCoord;
import com.wildbond.data.chunk.ChunkFormat;
import com.wildbond.data.chunk.ChunkObject;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link TmxMap} 을 32×32 {@link Chunk} 목록으로 자른다. collision 은 ground 레이어의 타일 GID 를 Tile.csv 의 id 로
 * 바꿔(gid - firstgid + 1) 조회한 값이다. docs/architecture.md §8.2.
 */
final class ChunkCompiler {

  private ChunkCompiler() {}

  static List<Chunk> compile(TmxMap map, GameData gameData) {
    int chunksX = ceilDiv(map.width(), ChunkFormat.SIZE);
    int chunksY = ceilDiv(map.height(), ChunkFormat.SIZE);

    List<Chunk> chunks = new ArrayList<>(chunksX * chunksY);
    for (int ccy = 0; ccy < chunksY; ccy++) {
      for (int ccx = 0; ccx < chunksX; ccx++) {
        chunks.add(sliceChunk(map, gameData, ccx, ccy));
      }
    }
    return chunks;
  }

  private static Chunk sliceChunk(TmxMap map, GameData gameData, int ccx, int ccy) {
    int[] ground = new int[ChunkFormat.TILE_COUNT];
    int[] detail = new int[ChunkFormat.TILE_COUNT];
    byte[] collision = new byte[ChunkFormat.TILE_COUNT];

    int baseTx = ccx * ChunkFormat.SIZE;
    int baseTy = ccy * ChunkFormat.SIZE;

    for (int localTy = 0; localTy < ChunkFormat.SIZE; localTy++) {
      int worldTy = baseTy + localTy;
      if (worldTy >= map.height()) {
        continue; // 맵 경계에 걸친 청크 — 나머지는 0/NONE
      }
      for (int localTx = 0; localTx < ChunkFormat.SIZE; localTx++) {
        int worldTx = baseTx + localTx;
        if (worldTx >= map.width()) {
          continue;
        }
        int localIdx = Chunk.indexOf(localTx, localTy);
        int worldIdx = worldTy * map.width() + worldTx;

        int groundGid = map.ground()[worldIdx];
        ground[localIdx] = groundGid;
        detail[localIdx] = map.detail()[worldIdx];
        collision[localIdx] = (byte) collisionBitFor(groundGid, map.firstGid(), gameData);
      }
    }

    List<ChunkObject> objects = new ArrayList<>();
    for (ChunkObject object : map.objects()) {
      if (belongsToChunk(object, baseTx, baseTy)) {
        objects.add(object);
      }
    }

    return new Chunk(new ChunkCoord(ccx, ccy), ground, detail, collision, objects);
  }

  private static boolean belongsToChunk(ChunkObject object, int baseTx, int baseTy) {
    return object.tileX() >= baseTx
        && object.tileX() < baseTx + ChunkFormat.SIZE
        && object.tileY() >= baseTy
        && object.tileY() < baseTy + ChunkFormat.SIZE;
  }

  private static int collisionBitFor(int gid, int firstGid, GameData gameData) {
    if (gid == 0) {
      return 0;
    }
    int tileId = gid - firstGid + 1;
    TileCollision collision = gameData.tile(tileId).collision();
    return ChunkFormat.collisionBit(collision);
  }

  private static int ceilDiv(int a, int b) {
    return (a + b - 1) / b;
  }
}
