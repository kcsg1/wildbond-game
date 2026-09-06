package com.wildbond.client.render;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteCache;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.wildbond.data.chunk.Chunk;
import com.wildbond.data.chunk.ChunkCoord;
import com.wildbond.data.chunk.ChunkFormat;
import java.util.HashMap;
import java.util.HashSet;
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

  private final Function<ChunkCoord, Chunk> loader;
  private final TextureRegion[] tileRegions;
  private final ExecutorService decodeExecutor;

  private final Set<ChunkCoord> requested = new HashSet<>();
  private final ConcurrentLinkedQueue<Chunk> decoded = new ConcurrentLinkedQueue<>();
  private final Map<ChunkCoord, Integer> cacheIds = new HashMap<>();
  private final SpriteCache spriteCache = new SpriteCache(8192, false);

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
        addTile(chunk.ground()[idx], worldX, worldY);
        addTile(chunk.detail()[idx], worldX, worldY);
      }
    }
    int cacheId = spriteCache.endCache();
    cacheIds.put(chunk.coord(), cacheId);
  }

  private void addTile(int gid, float worldX, float worldY) {
    if (gid <= 0 || gid > tileRegions.length) {
      return;
    }
    spriteCache.add(
        tileRegions[gid - 1], worldX, worldY, RenderConstants.TILE_PX, RenderConstants.TILE_PX);
  }

  /** update() 가 이미 올려 둔 청크를 전부 그린다(청크당 draw 한 번). */
  public void render(Matrix4 projection) {
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
