package com.wildbond.sim.systems;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.wildbond.data.GameData;
import com.wildbond.data.Monster;
import com.wildbond.data.TileCollision;
import com.wildbond.data.chunk.Chunk;
import com.wildbond.data.chunk.ChunkCoord;
import com.wildbond.data.chunk.ChunkFormat;
import com.wildbond.data.chunk.ChunkObject;
import com.wildbond.sim.Rng;
import com.wildbond.sim.SpawnRules;
import com.wildbond.sim.TileMap;
import com.wildbond.sim.components.Dead;
import com.wildbond.sim.components.MonsterData;
import com.wildbond.sim.components.PlayerTag;
import com.wildbond.sim.components.Position;
import com.wildbond.sim.components.SpawnOrigin;
import com.wildbond.sim.events.EventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * §4.1 시스템 8번 — 청크 활성/휴면, 스폰표 롤, 리스폰 (docs/architecture.md §9.2).
 *
 * <p>플레이어 청크 반경 {@value MonsterConstants#ACTIVE_CHUNK_RADIUS} 의 청크가 새로 켜지면 스폰표대로 채우고, 그 뒤로는 죽어서 빈
 * 자리를 리스폰 간격마다 한 마리씩 다시 채운다 — 사냥터에 몬스터가 계속 있게. 청크가 휴면하면 그 청크의 몬스터를 회수한다.
 */
public final class SpawnSystem extends BaseSystem {

  private static final int MAX_ACTIVE_CHUNKS =
      (2 * MonsterConstants.ACTIVE_CHUNK_RADIUS + 1)
          * (2 * MonsterConstants.ACTIVE_CHUNK_RADIUS + 1);

  private final EntityIndex index;
  private final TileMap tileMap;
  private final GameData gameData;
  private final Rng rng;
  private final EventBus eventBus;
  private final SimClock clock;
  private final SpawnRules rules;

  /** 존이 스폰 포인트를 쓸 때(굴) 필요한 청크 조회. 없으면 walkable 타일에서 고른다. */
  private final Function<ChunkCoord, Chunk> chunkLoader;

  private MonsterFactory factory;
  private ComponentMapper<Position> mPosition;
  private ComponentMapper<PlayerTag> mPlayer;
  private ComponentMapper<MonsterData> mMonster;
  private ComponentMapper<Dead> mDead;
  private ComponentMapper<SpawnOrigin> mSpawnOrigin;

  /** 종 id 오름차순 고정 목록 — GameData 의 Map 순회 순서에 기대지 않는다(§4.3). */
  private int[] speciesIds = new int[0];

  private final long[] activeChunks = new long[MAX_ACTIVE_CHUNKS];
  private final int[] nextRespawnTick = new int[MAX_ACTIVE_CHUNKS];
  private int activeChunkCount;
  private final long[] nextActiveChunks = new long[MAX_ACTIVE_CHUNKS];
  private int nextActiveChunkCount;

  private final int[] candidateTx = new int[ChunkFormat.SIZE * ChunkFormat.SIZE];
  private final int[] candidateTy = new int[ChunkFormat.SIZE * ChunkFormat.SIZE];
  private final List<Integer> despawnScratch = new ArrayList<>();

  public SpawnSystem(
      EntityIndex index,
      TileMap tileMap,
      GameData gameData,
      Rng rng,
      EventBus eventBus,
      SimClock clock,
      SpawnRules rules,
      Function<ChunkCoord, Chunk> chunkLoader) {
    this.index = index;
    this.tileMap = tileMap;
    this.gameData = gameData;
    this.rng = rng;
    this.eventBus = eventBus;
    this.clock = clock;
    this.rules = rules;
    this.chunkLoader = chunkLoader;
  }

  @Override
  protected void initialize() {
    mPosition = world.getMapper(Position.class);
    mPlayer = world.getMapper(PlayerTag.class);
    mMonster = world.getMapper(MonsterData.class);
    mDead = world.getMapper(Dead.class);
    mSpawnOrigin = world.getMapper(SpawnOrigin.class);
    factory = new MonsterFactory(world, index, gameData, eventBus);

    if (rules.speciesIds().length > 0) {
      speciesIds = rules.speciesIds().clone();
    } else {
      List<Monster> all = new ArrayList<>(gameData.allMonster());
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
    refillActiveChunks(playerTx, playerTy);
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
    int radius = MonsterConstants.ACTIVE_CHUNK_RADIUS;
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
      if (!mMonster.has(artemisId) || !mSpawnOrigin.has(artemisId)) {
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

  /**
   * 활성 청크마다: 처음 켜졌으면 표대로 채우고, 이미 켜져 있던 청크는 리스폰 간격이 지났을 때 살아 있는 수가 모자라면 한 마리 보충한다. 활성 목록을 다음 틱용 배열로
   * 옮기면서 리스폰 타이머도 같이 옮긴다.
   */
  private void refillActiveChunks(int playerTx, int playerTy) {
    int[] carriedTimers = new int[MAX_ACTIVE_CHUNKS];
    for (int i = 0; i < nextActiveChunkCount; i++) {
      long chunk = nextActiveChunks[i];
      int previous = indexOfActive(chunk);
      int chunkX = unpackX(chunk);
      int chunkY = unpackY(chunk);

      if (previous < 0) {
        spawnMany(chunkX, chunkY, playerTx, playerTy, rules.perChunk());
        carriedTimers[i] = clock.tick() + rules.respawnTicks();
        continue;
      }
      carriedTimers[i] = nextRespawnTick[previous];
      if (clock.tick() < carriedTimers[i]) {
        continue;
      }
      if (aliveFrom(chunkX, chunkY) < rules.perChunk()) {
        spawnMany(chunkX, chunkY, playerTx, playerTy, 1);
      }
      carriedTimers[i] = clock.tick() + rules.respawnTicks();
    }
    System.arraycopy(nextActiveChunks, 0, activeChunks, 0, nextActiveChunkCount);
    System.arraycopy(carriedTimers, 0, nextRespawnTick, 0, nextActiveChunkCount);
    activeChunkCount = nextActiveChunkCount;
  }

  private int aliveFrom(int chunkX, int chunkY) {
    int alive = 0;
    int n = index.size();
    for (int i = 0; i < n; i++) {
      int artemisId = index.artemisIdAt(i);
      if (!mMonster.has(artemisId) || mDead.has(artemisId) || !mSpawnOrigin.has(artemisId)) {
        continue;
      }
      SpawnOrigin origin = mSpawnOrigin.get(artemisId);
      if (origin.chunkX == chunkX && origin.chunkY == chunkY) {
        alive++;
      }
    }
    return alive;
  }

  private void spawnMany(int chunkX, int chunkY, int playerTx, int playerTy, int count) {
    int candidates = collectSpawnableTiles(chunkX, chunkY, playerTx, playerTy);
    if (candidates == 0) {
      return;
    }
    for (int i = 0; i < count && candidates > 0; i++) {
      int speciesId = speciesIds[rng.nextInt(Rng.Stream.SPAWN, speciesIds.length)];
      int pick = rng.nextInt(Rng.Stream.SPAWN, candidates);
      int tileX = candidateTx[pick];
      int tileY = candidateTy[pick];
      candidates--;
      candidateTx[pick] = candidateTx[candidates];
      candidateTy[pick] = candidateTy[candidates];

      float x = tileX * (float) SimConstants.TILE_SIZE_PX + SimConstants.TILE_SIZE_PX / 2f;
      float y = tileY * (float) SimConstants.TILE_SIZE_PX + SimConstants.TILE_SIZE_PX / 2f;
      factory.spawn(x, y, speciesId, chunkX, chunkY);
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

  private int indexOfActive(long chunk) {
    for (int i = 0; i < activeChunkCount; i++) {
      if (activeChunks[i] == chunk) {
        return i;
      }
    }
    return -1;
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
