package com.wildbond.sim.systems;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.wildbond.data.GameData;
import com.wildbond.data.PalSpecies;
import com.wildbond.data.TileCollision;
import com.wildbond.data.chunk.Chunk;
import com.wildbond.data.chunk.ChunkCoord;
import com.wildbond.data.chunk.ChunkFormat;
import com.wildbond.data.chunk.ChunkObject;
import com.wildbond.sim.Rng;
import com.wildbond.sim.SpawnRules;
import com.wildbond.sim.TileMap;
import com.wildbond.sim.components.Owner;
import com.wildbond.sim.components.PalData;
import com.wildbond.sim.components.PlayerTag;
import com.wildbond.sim.components.Position;
import com.wildbond.sim.components.SpawnOrigin;
import com.wildbond.sim.events.EventBus;
import java.util.ArrayList;
import java.util.List;

/**
 * §4.1 시스템 10번 — 청크 활성/휴면과 야생 스폰 (docs/architecture.md §9.1, docs/m0-prompts.md 단계7).
 *
 * <p>M0 임시 스폰 규칙: 활성 청크(플레이어 청크 반경 {@value PalConstants#ACTIVE_CHUNK_RADIUS})가 새로 켜지면 그 청크에 종 1가지를
 * 골라 {@value PalConstants#PALS_PER_CHUNK} 마리를, 플레이어에서 {@value
 * PalConstants#MIN_SPAWN_DISTANCE_TILES} 타일 이상 떨어진 walkable 타일에 만든다. 청크가 휴면하면 그 청크에서 나온 야생 팰을 회수한다
 * — 포획되어 주인이 생긴 팰은 남긴다.
 *
 * <p>SpawnTable(§9.1)은 아직 데이터 테이블에 없다. 종은 {@code PalSpecies} 전체에서 id 오름차순으로 굴린다.
 */
public final class SpawnSystem extends BaseSystem {

  private static final int MAX_ACTIVE_CHUNKS =
      (2 * PalConstants.ACTIVE_CHUNK_RADIUS + 1) * (2 * PalConstants.ACTIVE_CHUNK_RADIUS + 1);

  private final EntityIndex index;
  private final TileMap tileMap;
  private final GameData gameData;
  private final Rng rng;
  private final EventBus eventBus;
  private final SpawnRules rules;

  /** 존이 스폰 포인트를 쓸 때(굴) 필요한 청크 조회. 없으면 walkable 타일에서 고른다. */
  private final java.util.function.Function<ChunkCoord, Chunk> chunkLoader;

  private PalFactory palFactory;
  private ComponentMapper<Position> mPosition;
  private ComponentMapper<PlayerTag> mPlayer;
  private ComponentMapper<PalData> mPal;
  private ComponentMapper<Owner> mOwner;
  private ComponentMapper<SpawnOrigin> mSpawnOrigin;

  /** 종 id 오름차순 고정 목록 — GameData 의 Map 순회 순서에 기대지 않는다(§4.3). */
  private int[] speciesIds = new int[0];

  private final long[] activeChunks = new long[MAX_ACTIVE_CHUNKS];
  private int activeChunkCount;
  private final long[] nextActiveChunks = new long[MAX_ACTIVE_CHUNKS];
  private int nextActiveChunkCount;

  /** 후보 타일 스크래치 — 청크 하나(32×32)를 담는다. */
  private final int[] candidateTx = new int[ChunkFormat.SIZE * ChunkFormat.SIZE];

  private final int[] candidateTy = new int[ChunkFormat.SIZE * ChunkFormat.SIZE];
  private final List<Integer> despawnScratch = new ArrayList<>();

  public SpawnSystem(
      EntityIndex index,
      TileMap tileMap,
      GameData gameData,
      Rng rng,
      EventBus eventBus,
      SpawnRules rules,
      java.util.function.Function<ChunkCoord, Chunk> chunkLoader) {
    this.index = index;
    this.tileMap = tileMap;
    this.gameData = gameData;
    this.rng = rng;
    this.eventBus = eventBus;
    this.rules = rules;
    this.chunkLoader = chunkLoader;
  }

  @Override
  protected void initialize() {
    mPosition = world.getMapper(Position.class);
    mPlayer = world.getMapper(PlayerTag.class);
    mPal = world.getMapper(PalData.class);
    mOwner = world.getMapper(Owner.class);
    mSpawnOrigin = world.getMapper(SpawnOrigin.class);
    palFactory = new PalFactory(world, index, gameData, eventBus);

    if (rules.speciesIds().length > 0) {
      speciesIds = rules.speciesIds().clone(); // 존이 정한 종만 나온다 (D-16)
    } else {
      List<PalSpecies> all = new ArrayList<>(gameData.allPalSpecies());
      all.sort((a, b) -> Integer.compare(a.id(), b.id()));
      speciesIds = new int[all.size()];
      for (int i = 0; i < all.size(); i++) {
        speciesIds[i] = all.get(i).id();
      }
    }
  }

  @Override
  protected void processSystem() {
    if (!rules.enabled() || speciesIds.length == 0) {
      return;
    }
    int playerArtemisId = findPlayer();
    if (playerArtemisId < 0) {
      return;
    }
    Position player = mPosition.get(playerArtemisId);
    int playerTx = AiContext.tileOf(player.x);
    int playerTy = AiContext.tileOf(player.y);
    int playerCx = Math.floorDiv(playerTx, ChunkFormat.SIZE);
    int playerCy = Math.floorDiv(playerTy, ChunkFormat.SIZE);

    collectActiveChunks(playerCx, playerCy);
    despawnDormantChunks();
    spawnNewlyActiveChunks(playerTx, playerTy);

    System.arraycopy(nextActiveChunks, 0, activeChunks, 0, nextActiveChunkCount);
    activeChunkCount = nextActiveChunkCount;
  }

  private int findPlayer() {
    int n = index.size();
    for (int i = 0; i < n; i++) {
      int artemisId = index.artemisIdAt(i);
      if (mPlayer.has(artemisId) && mPosition.has(artemisId)) {
        return artemisId;
      }
    }
    return -1;
  }

  private void collectActiveChunks(int playerCx, int playerCy) {
    nextActiveChunkCount = 0;
    int radius = PalConstants.ACTIVE_CHUNK_RADIUS;
    for (int cy = playerCy - radius; cy <= playerCy + radius; cy++) {
      for (int cx = playerCx - radius; cx <= playerCx + radius; cx++) {
        nextActiveChunks[nextActiveChunkCount++] = pack(cx, cy);
      }
    }
  }

  private void despawnDormantChunks() {
    despawnScratch.clear();
    int n = index.size();
    for (int i = 0; i < n; i++) {
      int artemisId = index.artemisIdAt(i);
      if (!mPal.has(artemisId) || mOwner.has(artemisId) || !mSpawnOrigin.has(artemisId)) {
        continue;
      }
      SpawnOrigin origin = mSpawnOrigin.get(artemisId);
      if (!isNextActive(pack(origin.chunkX, origin.chunkY))) {
        despawnScratch.add(index.stableIdAt(i));
      }
    }
    for (int i = 0; i < despawnScratch.size(); i++) {
      int stableId = despawnScratch.get(i);
      world.delete(index.artemisIdOf(stableId));
      index.remove(stableId);
    }
  }

  private void spawnNewlyActiveChunks(int playerTx, int playerTy) {
    for (int i = 0; i < nextActiveChunkCount; i++) {
      long chunk = nextActiveChunks[i];
      if (isPreviouslyActive(chunk)) {
        continue;
      }
      spawnInChunk(unpackX(chunk), unpackY(chunk), playerTx, playerTy);
    }
  }

  private void spawnInChunk(int chunkX, int chunkY, int playerTx, int playerTy) {
    int candidates = collectSpawnableTiles(chunkX, chunkY, playerTx, playerTy);
    if (candidates == 0) {
      return;
    }
    int speciesId = speciesIds[rng.nextInt(Rng.Stream.SPAWN, speciesIds.length)];
    int levelSpan = PalConstants.MAX_PAL_LEVEL - PalConstants.MIN_PAL_LEVEL + 1;

    for (int i = 0; i < rules.palsPerChunk() && candidates > 0; i++) {
      int pick = rng.nextInt(Rng.Stream.SPAWN, candidates);
      int tileX = candidateTx[pick];
      int tileY = candidateTy[pick];
      candidates--;
      candidateTx[pick] = candidateTx[candidates];
      candidateTy[pick] = candidateTy[candidates];

      int level = PalConstants.MIN_PAL_LEVEL + rng.nextInt(Rng.Stream.SPAWN, levelSpan);
      float x = tileX * (float) SimConstants.TILE_SIZE_PX + SimConstants.TILE_SIZE_PX / 2f;
      float y = tileY * (float) SimConstants.TILE_SIZE_PX + SimConstants.TILE_SIZE_PX / 2f;
      palFactory.spawn(x, y, speciesId, level, rng, chunkX, chunkY);
    }
  }

  /** 청크 안의 스폰 후보 타일을 모은다. 존이 스폰 포인트를 쓰면(굴) 오브젝트로 찍어 둔 자리만, 아니면 walkable 타일 전체에서 고른다. */
  private int collectSpawnableTiles(int chunkX, int chunkY, int playerTx, int playerTy) {
    if (rules.useSpawnPoints()) {
      return collectSpawnPoints(chunkX, chunkY);
    }
    int count = 0;
    int baseTx = chunkX * ChunkFormat.SIZE;
    int baseTy = chunkY * ChunkFormat.SIZE;
    int minDistanceSquared = rules.minDistanceTiles() * rules.minDistanceTiles();

    for (int localTy = 0; localTy < ChunkFormat.SIZE; localTy++) {
      for (int localTx = 0; localTx < ChunkFormat.SIZE; localTx++) {
        int tileX = baseTx + localTx;
        int tileY = baseTy + localTy;
        int dx = tileX - playerTx;
        int dy = tileY - playerTy;
        if (dx * dx + dy * dy < minDistanceSquared) {
          continue;
        }
        if (tileMap.collision(tileX, tileY) != TileCollision.NONE) {
          continue;
        }
        candidateTx[count] = tileX;
        candidateTy[count] = tileY;
        count++;
      }
    }
    return count;
  }

  /** 맵에 찍어 둔 {@code spawn_point} 오브젝트 자리만 모은다. 굴처럼 "방 안에만" 두고 싶을 때 쓴다. */
  private int collectSpawnPoints(int chunkX, int chunkY) {
    if (chunkLoader == null) {
      return 0;
    }
    Chunk chunk = chunkLoader.apply(new ChunkCoord(chunkX, chunkY));
    if (chunk == null) {
      return 0;
    }
    int count = 0;
    for (ChunkObject object : chunk.objects()) {
      if (!"spawn_point".equals(object.type()) || count >= candidateTx.length) {
        continue;
      }
      candidateTx[count] = object.tileX();
      candidateTy[count] = object.tileY();
      count++;
    }
    return count;
  }

  private boolean isPreviouslyActive(long chunk) {
    for (int i = 0; i < activeChunkCount; i++) {
      if (activeChunks[i] == chunk) {
        return true;
      }
    }
    return false;
  }

  private boolean isNextActive(long chunk) {
    for (int i = 0; i < nextActiveChunkCount; i++) {
      if (nextActiveChunks[i] == chunk) {
        return true;
      }
    }
    return false;
  }

  private static long pack(int chunkX, int chunkY) {
    return ((long) chunkX << 32) | (chunkY & 0xFFFFFFFFL);
  }

  private static int unpackX(long packed) {
    return (int) (packed >> 32);
  }

  private static int unpackY(long packed) {
    return (int) packed;
  }
}
