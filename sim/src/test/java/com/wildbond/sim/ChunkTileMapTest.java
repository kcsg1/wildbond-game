package com.wildbond.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.wildbond.data.TileCollision;
import com.wildbond.data.chunk.Chunk;
import com.wildbond.data.chunk.ChunkCoord;
import com.wildbond.data.chunk.ChunkFormat;
import com.wildbond.data.chunk.ChunkReader;
import com.wildbond.data.chunk.ChunkWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** docs/m0-prompts.md 단계 4 — 컴파일된 청크를 ChunkTileMap 으로 읽어 연못 타일이 water 로 판정되는지. */
class ChunkTileMapTest {

  @TempDir Path tempDir;

  @Test
  void pondTileReadsAsWater() throws IOException {
    ChunkCoord coord = new ChunkCoord(0, 0);
    int pondLocalTx = 10;
    int pondLocalTy = 10;
    writeCompiledChunk(coord, pondLocalTx, pondLocalTy);

    ChunkTileMap tileMap = new ChunkTileMap(this::readFromTempDir);

    assertThat(tileMap.collision(pondLocalTx, pondLocalTy)).isEqualTo(TileCollision.WATER);
    assertThat(tileMap.collision(0, 0)).isEqualTo(TileCollision.NONE);
  }

  @Test
  void tileCoordinatesResolveToTheirOwningChunk() throws IOException {
    // 청크(1,0)의 로컬(0,0) = 월드 타일(32,0).
    writeCompiledChunk(new ChunkCoord(1, 0), 0, 0);
    ChunkTileMap tileMap = new ChunkTileMap(this::readFromTempDir);

    assertThat(tileMap.collision(32, 0)).isEqualTo(TileCollision.WATER);
  }

  @Test
  void loaderIsCalledOncePerChunkThenCached() throws IOException {
    writeCompiledChunk(new ChunkCoord(0, 0), 5, 5);
    AtomicInteger loadCount = new AtomicInteger();
    ChunkTileMap tileMap =
        new ChunkTileMap(
            coord -> {
              loadCount.incrementAndGet();
              return readFromTempDir(coord);
            });

    for (int i = 0; i < 20; i++) {
      tileMap.collision(i % ChunkFormat.SIZE, 0);
    }

    assertThat(loadCount.get()).isEqualTo(1);
  }

  private Chunk readFromTempDir(ChunkCoord coord) {
    try {
      return ChunkReader.read(tempDir.resolve(coord.cx() + "_" + coord.cy() + ".wbc"));
    } catch (IOException e) {
      return null;
    }
  }

  private void writeCompiledChunk(ChunkCoord coord, int waterLocalTx, int waterLocalTy)
      throws IOException {
    int[] ground = new int[ChunkFormat.TILE_COUNT];
    int[] detail = new int[ChunkFormat.TILE_COUNT];
    byte[] collision = new byte[ChunkFormat.TILE_COUNT];
    collision[Chunk.indexOf(waterLocalTx, waterLocalTy)] =
        (byte) ChunkFormat.collisionBit(TileCollision.WATER);

    Chunk chunk = new Chunk(coord, ground, detail, collision, List.of());
    ChunkWriter.write(chunk, tempDir.resolve(coord.cx() + "_" + coord.cy() + ".wbc"));
  }
}
