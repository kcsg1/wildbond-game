package com.wildbond.data.chunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wildbond.data.TileCollision;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChunkRoundTripTest {

  @TempDir Path tempDir;

  @Test
  void writeThenReadReproducesTheChunk() throws IOException {
    Chunk original = sampleChunk(new ChunkCoord(2, -1));

    Path file = tempDir.resolve("2_-1.wbc");
    ChunkWriter.write(original, file);
    Chunk read = ChunkReader.read(file);

    assertThat(read).isEqualTo(original);
  }

  @Test
  void compressedFileIsSmallerThanRawPayload() throws IOException {
    // ground 전부 같은 값 — zstd 가 확실히 줄일 수 있는 입력.
    int[] ground = new int[ChunkFormat.TILE_COUNT];
    Arrays.fill(ground, 1);
    Chunk uniform =
        new Chunk(
            new ChunkCoord(0, 0),
            ground,
            new int[ChunkFormat.TILE_COUNT],
            new byte[ChunkFormat.TILE_COUNT],
            List.of());

    Path file = tempDir.resolve("uniform.wbc");
    ChunkWriter.write(uniform, file);

    long fileSize = Files.size(file);
    long rawPayloadSize =
        (long) ChunkFormat.TILE_COUNT * (2 + 2 + 1); // ground+detail(u16)+collision(u8)
    assertThat(fileSize).isLessThan(rawPayloadSize);
  }

  @Test
  void magicAndVersionAreRejectedWhenWrong() throws IOException {
    Chunk chunk = sampleChunk(new ChunkCoord(0, 0));
    Path file = tempDir.resolve("bad.wbc");
    ChunkWriter.write(chunk, file);

    byte[] bytes = Files.readAllBytes(file);
    bytes[0] = 'X'; // magic 깨뜨리기
    Files.write(file, bytes);

    assertThatThrownBy(() -> ChunkReader.read(file)).isInstanceOf(IOException.class);
  }

  private Chunk sampleChunk(ChunkCoord coord) {
    int[] ground = new int[ChunkFormat.TILE_COUNT];
    int[] detail = new int[ChunkFormat.TILE_COUNT];
    byte[] collision = new byte[ChunkFormat.TILE_COUNT];
    for (int ty = 0; ty < ChunkFormat.SIZE; ty++) {
      for (int tx = 0; tx < ChunkFormat.SIZE; tx++) {
        int idx = Chunk.indexOf(tx, ty);
        ground[idx] = 1 + (tx % 5);
        detail[idx] = (tx + ty) % 3;
      }
    }
    // 연못 한 칸 — water 비트.
    collision[Chunk.indexOf(10, 10)] = (byte) ChunkFormat.collisionBit(TileCollision.WATER);
    collision[Chunk.indexOf(11, 10)] = (byte) ChunkFormat.collisionBit(TileCollision.SOLID);

    List<ChunkObject> objects =
        List.of(
            new ChunkObject("resource_node", 5, 6, Map.of("item", "5")),
            new ChunkObject("spawn_point", 20, 20, Map.of()));

    return new Chunk(coord, ground, detail, collision, objects);
  }
}
