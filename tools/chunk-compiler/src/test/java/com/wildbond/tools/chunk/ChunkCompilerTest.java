package com.wildbond.tools.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import com.wildbond.data.GameData;
import com.wildbond.data.TileCollision;
import com.wildbond.data.chunk.Chunk;
import com.wildbond.data.chunk.ChunkCoord;
import com.wildbond.data.chunk.ChunkFormat;
import com.wildbond.data.chunk.ChunkObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** assets/maps/src/test_island.tmx 를 실제로 파싱·컴파일해 §8.2 청크 규칙을 검증한다 (docs/m0-prompts.md 단계4). */
class ChunkCompilerTest {

  private static GameData gameData;
  private static List<Chunk> chunks;

  @BeforeAll
  static void compileTestIsland() throws IOException {
    Path tablesDir = Path.of(System.getProperty("wildbond.tables", "../../data/tables"));
    Path tmxFile =
        Path.of(System.getProperty("wildbond.testIsland", "../../assets/maps/src/test_island.tmx"));
    gameData = GameData.load(tablesDir);
    TmxMap map = TmxParser.parse(tmxFile);
    chunks = ChunkCompiler.compile(map, gameData);
  }

  @Test
  void producesFourChunksFor64x64Map() {
    assertThat(chunks).hasSize(4);
    assertThat(chunks.stream().map(Chunk::coord))
        .containsExactlyInAnyOrder(
            new ChunkCoord(0, 0), new ChunkCoord(1, 0), new ChunkCoord(0, 1), new ChunkCoord(1, 1));
  }

  @Test
  void pondTileCompilesToWaterCollision() {
    Chunk chunk00 = chunkAt(0, 0);
    int bits = chunk00.collision()[Chunk.indexOf(10, 10)] & 0xFF;
    assertThat(ChunkFormat.collisionFromBits(bits)).isEqualTo(TileCollision.WATER);
    // ground GID 는 Tile.csv 의 water id(3) 그대로 (firstgid=1).
    assertThat(chunk00.ground()[Chunk.indexOf(10, 10)]).isEqualTo(3);
  }

  @Test
  void cliffLineSpansTwoChunksVertically() {
    // 절벽은 world ty 5..58, tx 40..41 — 청크(1,0) 과 (1,1) 양쪽에 걸친다.
    Chunk chunk10 = chunkAt(1, 0);
    Chunk chunk11 = chunkAt(1, 1);

    int bitsTop = chunk10.collision()[Chunk.indexOf(40 - 32, 20)] & 0xFF;
    int bitsBottom = chunk11.collision()[Chunk.indexOf(40 - 32, 40 - 32)] & 0xFF;

    assertThat(ChunkFormat.collisionFromBits(bitsTop)).isEqualTo(TileCollision.CLIFF);
    assertThat(ChunkFormat.collisionFromBits(bitsBottom)).isEqualTo(TileCollision.CLIFF);
  }

  @Test
  void plainGrassTileHasNoCollision() {
    Chunk chunk00 = chunkAt(0, 0);
    int bits = chunk00.collision()[Chunk.indexOf(0, 0)] & 0xFF;
    assertThat(ChunkFormat.collisionFromBits(bits)).isEqualTo(TileCollision.NONE);
    assertThat(chunk00.ground()[Chunk.indexOf(0, 0)]).isEqualTo(1); // grass id
  }

  @Test
  void threeStoneResourceNodesAreAssignedToTheirOwningChunks() {
    long total = chunks.stream().mapToLong(c -> c.objects().size()).sum();
    assertThat(total).isEqualTo(3);

    // 오브젝트 tile(20,20) -> 청크(0,0)
    assertThat(chunkAt(0, 0).objects()).hasSize(1);
    ChunkObject inChunk00 = chunkAt(0, 0).objects().get(0);
    assertThat(inChunk00.type()).isEqualTo("resource_node");
    assertThat(inChunk00.props()).isEqualTo(Map.of("item", "5"));

    // tile(45,10) -> 청크(1,0), tile(50,50) -> 청크(1,1)
    assertThat(chunkAt(1, 0).objects()).hasSize(1);
    assertThat(chunkAt(1, 1).objects()).hasSize(1);
    assertThat(chunkAt(0, 1).objects()).isEmpty();
  }

  private Chunk chunkAt(int cx, int cy) {
    return chunks.stream()
        .filter(c -> c.coord().equals(new ChunkCoord(cx, cy)))
        .findFirst()
        .orElseThrow(() -> new AssertionError("청크(" + cx + "," + cy + ") 없음"));
  }
}
