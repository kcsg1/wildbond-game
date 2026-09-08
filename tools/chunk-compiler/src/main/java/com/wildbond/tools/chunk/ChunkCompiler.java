package com.wildbond.tools.chunk;

import com.wildbond.data.GameData;
import com.wildbond.data.TileCollision;
import com.wildbond.data.chunk.Chunk;
import com.wildbond.data.chunk.ChunkCoord;
import com.wildbond.data.chunk.ChunkFormat;
import com.wildbond.data.chunk.ChunkObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@link TmxMap} 을 32×32 {@link Chunk} 목록으로 자른다. collision 은 ground 레이어의 타일 GID 를 Tile.csv 의 id 로
 * 바꿔(gid - firstgid + 1) 조회한 값이고, 그 위에 {@link #BLOCKING_OBJECT_TYPES} 오브젝트가 놓인 타일을 solid 로 덮어쓴다.
 * docs/architecture.md §8.2.
 */
final class ChunkCompiler {

  /**
   * 서 있는 것만으로 길을 막는 오브젝트 — 나무·자원 노드. 지형 타일을 바꾸지 않고 오브젝트만 얹어도 sim 이 막아 주도록, 컴파일 시점에 collision 레이어에
   * 찍어 둔다. sim 은 타일 충돌만 보므로(§4.1 TileMap) 이렇게 해야 오브젝트가 실제로 벽 노릇을 한다.
   */
  private static final Set<String> BLOCKING_OBJECT_TYPES =
      Set.of("tree", "resource_node", "fence", "house", "house_red");

  /** 집은 앵커 타일 하나가 아니라 3×4 칸을 차지한다 (앵커 = 왼쪽 아래). */
  private static final int HOUSE_W = 3;

  private static final int HOUSE_H = 4;

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
      if (!belongsToChunk(object, baseTx, baseTy)) {
        continue;
      }
      objects.add(object);
      if (BLOCKING_OBJECT_TYPES.contains(object.type())) {
        stampSolid(collision, object, baseTx, baseTy);
      }
    }

    return new Chunk(new ChunkCoord(ccx, ccy), ground, detail, collision, objects);
  }

  /** 오브젝트가 차지하는 칸 전부를 solid 로 찍는다. 청크 밖으로 삐져나간 칸은 그 청크가 자기 몫으로 다시 찍는다. */
  private static void stampSolid(byte[] collision, ChunkObject object, int baseTx, int baseTy) {
    boolean house = object.type().startsWith("house");
    int width = house ? HOUSE_W : 1;
    int height = house ? HOUSE_H : 1;
    // 집 앵커는 왼쪽 아래 칸이라 위로 h-1 칸 올라간다.
    int originTy = house ? object.tileY() - (HOUSE_H - 1) : object.tileY();
    for (int dy = 0; dy < height; dy++) {
      for (int dx = 0; dx < width; dx++) {
        int localTx = object.tileX() + dx - baseTx;
        int localTy = originTy + dy - baseTy;
        if (localTx < 0
            || localTy < 0
            || localTx >= ChunkFormat.SIZE
            || localTy >= ChunkFormat.SIZE) {
          continue;
        }
        collision[Chunk.indexOf(localTx, localTy)] |=
            (byte) ChunkFormat.collisionBit(TileCollision.SOLID);
      }
    }
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
