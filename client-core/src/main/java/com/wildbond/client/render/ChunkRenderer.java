package com.wildbond.client.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteCache;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.wildbond.data.chunk.Chunk;
import com.wildbond.data.chunk.ChunkCoord;
import com.wildbond.data.chunk.ChunkFormat;
import com.wildbond.data.chunk.ChunkObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * 카메라 반경 2청크를 유지한다 — 디코드는 별도 스레드, GL 업로드(SpriteCache)는 렌더 스레드에서만 (docs/architecture.md §5.4). 청크
 * 하나가 SpriteCache 캐시 하나 = 그리기 한 번.
 */
public final class ChunkRenderer implements Disposable {

  private static final int LOAD_RADIUS_CHUNKS = 2;
  private static final int TILES_PER_CHUNK = ChunkFormat.SIZE;

  /**
   * 타일셋 뒤쪽의 렌더 전용 칸 (PlaceholderTilesetGenerator 와 같은 순서여야 한다). Tile.csv 에는 없는 그림들이라 지형 규칙이 아니라
   * 여기서만 쓴다 — 잔디를 몇 종류로 섞어 밋밋함을 없애고, 물 타일 가장자리에 물가 띠를 덧그린다.
   */
  private static final int GRASS_VARIANT_START = 5;

  private static final int GRASS_VARIANT_COUNT = 3;
  private static final int WATER_EDGE_START = 8;

  /** data/tables/Tile.csv 의 id. */
  private static final int TILE_ID_GRASS = 1;

  private static final int TILE_ID_WATER = 3;

  /** 이웃 검사 순서 — 물가 경계 타일 순서(북/동/남/서)와 같아야 한다. */
  private static final int[] EDGE_DX = {0, 1, 0, -1};

  private static final int[] EDGE_DY = {-1, 0, 1, 0};

  private final Function<ChunkCoord, Chunk> loader;
  private final TextureRegion[] tileRegions;
  private final ExecutorService decodeExecutor;

  private final Set<ChunkCoord> requested = new HashSet<>();
  private final ConcurrentLinkedQueue<Chunk> decoded = new ConcurrentLinkedQueue<>();
  private final Map<ChunkCoord, Integer> cacheIds = new HashMap<>();
  private final SpriteCache spriteCache = new SpriteCache(8192, false);

  /**
   * 로드된 청크의 배치 오브젝트(나무·자원 노드). 타일 캐시에 넣지 않고 따로 들고 있는다 — 나무는 32×32 보다 커서 타일 격자에 안 맞고, 엔티티와 함께 Y-정렬해야
   * 플레이어가 나무 뒤로 지나갈 수 있기 때문이다({@link EntityRenderer} 가 그린다).
   */
  private final Map<ChunkCoord, List<ChunkObject>> propsByChunk = new HashMap<>();

  private final List<ChunkObject> sortedProps = new ArrayList<>();

  private int lastRenderCalls;

  public ChunkRenderer(
      Texture tilesetTexture, int tileColumns, Function<ChunkCoord, Chunk> loader) {
    this.loader = loader;
    this.tileRegions = new TextureRegion[tileColumns];
    for (int i = 0; i < tileColumns; i++) {
      tileRegions[i] =
          new TextureRegion(
              tilesetTexture,
              i * RenderConstants.TILE_PX,
              0,
              RenderConstants.TILE_PX,
              RenderConstants.TILE_PX);
    }
    this.decodeExecutor =
        Executors.newSingleThreadExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "chunk-decode");
              thread.setDaemon(true);
              return thread;
            });
  }

  /** 카메라 월드 좌표 기준 반경 안 청크를 요청/업로드한다. 매 프레임 호출. */
  public void update(float cameraWorldX, float cameraWorldY) {
    ChunkCoord center =
        ChunkCoord.ofTile(
            (int) Math.floor(cameraWorldX / RenderConstants.TILE_PX),
            (int) Math.floor(cameraWorldY / RenderConstants.TILE_PX));

    for (int dy = -LOAD_RADIUS_CHUNKS; dy <= LOAD_RADIUS_CHUNKS; dy++) {
      for (int dx = -LOAD_RADIUS_CHUNKS; dx <= LOAD_RADIUS_CHUNKS; dx++) {
        ChunkCoord coord = new ChunkCoord(center.cx() + dx, center.cy() + dy);
        if (!cacheIds.containsKey(coord) && requested.add(coord)) {
          requestDecode(coord);
        }
      }
    }

    Chunk chunk;
    while ((chunk = decoded.poll()) != null) {
      uploadToCache(chunk);
    }
  }

  private void requestDecode(ChunkCoord coord) {
    decodeExecutor.submit(
        () -> {
          try {
            Chunk chunk = loader.apply(coord);
            if (chunk != null) {
              decoded.add(chunk);
            }
          } catch (RuntimeException ignored) {
            // 청크 파일이 없거나 깨졌다 — 그 자리는 그냥 빈 채로 둔다 (월드 밖과 동일하게 취급).
          }
        });
  }

  private void uploadToCache(Chunk chunk) {
    spriteCache.beginCache();
    int baseX = chunk.coord().cx() * TILES_PER_CHUNK * RenderConstants.TILE_PX;
    int baseY = chunk.coord().cy() * TILES_PER_CHUNK * RenderConstants.TILE_PX;
    for (int ty = 0; ty < TILES_PER_CHUNK; ty++) {
      for (int tx = 0; tx < TILES_PER_CHUNK; tx++) {
        int idx = Chunk.indexOf(tx, ty);
        float worldX = baseX + tx * RenderConstants.TILE_PX;
        float worldY = baseY + ty * RenderConstants.TILE_PX;
        int groundGid = chunk.ground()[idx];

        addRegion(groundRegionIndex(groundGid, tx, ty), worldX, worldY);
        if (groundGid == TILE_ID_WATER) {
          addWaterEdges(chunk, tx, ty, worldX, worldY);
        }
        addTile(chunk.detail()[idx], worldX, worldY);
      }
    }
    int cacheId = spriteCache.endCache();
    cacheIds.put(chunk.coord(), cacheId);

    propsByChunk.put(chunk.coord(), List.copyOf(chunk.objects()));
    rebuildSortedProps();
  }

  /** 청크가 새로 올라올 때만 다시 정렬한다 — 오브젝트는 움직이지 않으므로 매 프레임 정렬할 이유가 없다. */
  private void rebuildSortedProps() {
    sortedProps.clear();
    for (List<ChunkObject> objects : propsByChunk.values()) {
      sortedProps.addAll(objects);
    }
    sortedProps.sort(
        Comparator.comparingInt(ChunkObject::tileY).thenComparingInt(ChunkObject::tileX));
  }

  /** 그리기 순서(위에서 아래)로 정렬된, 현재 로드된 배치 오브젝트. */
  public List<ChunkObject> props() {
    return sortedProps;
  }

  private void addTile(int gid, float worldX, float worldY) {
    if (gid <= 0 || gid > tileRegions.length) {
      return;
    }
    addRegion(gid - 1, worldX, worldY);
  }

  private void addRegion(int regionIndex, float worldX, float worldY) {
    if (regionIndex < 0 || regionIndex >= tileRegions.length) {
      return;
    }
    spriteCache.add(
        tileRegions[regionIndex], worldX, worldY, RenderConstants.TILE_PX, RenderConstants.TILE_PX);
  }

  /**
   * 잔디는 위치로 정한 변형 중 하나를 쓴다 — 같은 그림이 끝없이 반복되면 바닥이 비어 보인다. 좌표 해시라서 매 프레임 흔들리지 않고, 청크를 다시 올려도 같은 자리에는
   * 같은 변형이 나온다.
   */
  private static int groundRegionIndex(int gid, int tileX, int tileY) {
    if (gid != TILE_ID_GRASS) {
      return gid - 1;
    }
    int hash = Math.floorMod((tileX * 73856093) ^ (tileY * 19349663), 1 << 20);
    // 눈에 띄는 것일수록 드물게 — 돌·꽃이 흔하면 들판이 아니라 꽃밭처럼 보인다.
    if (hash % 61 == 0) {
      return GRASS_VARIANT_START + 2; // 돌
    }
    if (hash % 29 == 0) {
      return GRASS_VARIANT_START + 1; // 꽃
    }
    if (hash % 9 == 0) {
      return GRASS_VARIANT_START; // 잔풀
    }
    return gid - 1;
  }

  /** 물 타일에서 이웃이 물이 아닌 방향에만 물가 띠를 덧그린다. 청크 경계 밖은 물로 쳐서 이음매에 띠가 생기지 않게 한다. */
  private void addWaterEdges(Chunk chunk, int tileX, int tileY, float worldX, float worldY) {
    for (int d = 0; d < EDGE_DX.length; d++) {
      int nx = tileX + EDGE_DX[d];
      int ny = tileY + EDGE_DY[d];
      if (nx < 0 || ny < 0 || nx >= TILES_PER_CHUNK || ny >= TILES_PER_CHUNK) {
        continue;
      }
      if (chunk.ground()[Chunk.indexOf(nx, ny)] != TILE_ID_WATER) {
        addRegion(WATER_EDGE_START + d, worldX, worldY);
      }
    }
  }

  /** update() 가 이미 올려 둔 청크를 전부 그린다(청크당 draw 한 번). */
  public void render(Matrix4 projection) {
    // SpriteCache 는 SpriteBatch 와 달리 알파 블렌딩을 켜 주지 않는다 — 켜지 않으면 물가 경계처럼 반투명한
    // 타일의 투명 픽셀이 그대로 검게 칠해진다(T-013).
    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    spriteCache.setProjectionMatrix(projection);
    spriteCache.begin();
    for (Integer cacheId : cacheIds.values()) {
      spriteCache.draw(cacheId);
    }
    spriteCache.end();
    lastRenderCalls = spriteCache.renderCalls;
  }

  public int renderCalls() {
    return lastRenderCalls;
  }

  public int loadedChunkCount() {
    return cacheIds.size();
  }

  @Override
  public void dispose() {
    decodeExecutor.shutdownNow();
    spriteCache.dispose();
  }
}
