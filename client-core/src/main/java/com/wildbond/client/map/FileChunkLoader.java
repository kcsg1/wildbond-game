package com.wildbond.client.map;

import com.wildbond.data.chunk.Chunk;
import com.wildbond.data.chunk.ChunkCoord;
import com.wildbond.data.chunk.ChunkReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * `<cx>_<cy>.wbc` 파일에서 청크를 읽는다 (docs/architecture.md §8.2). 상태가 없어 여러 스레드에서 동시에 호출해도 안전하다 — sim 의
 * {@code ChunkTileMap}(동기)과 렌더러의 비동기 디코드 스레드가 같은 인스턴스를 공유할 수 있다. 파일이 없으면 null(월드 밖).
 */
public final class FileChunkLoader implements Function<ChunkCoord, Chunk> {

  private final Path chunksDir;

  public FileChunkLoader(Path chunksDir) {
    this.chunksDir = chunksDir;
  }

  @Override
  public Chunk apply(ChunkCoord coord) {
    Path file = chunksDir.resolve(coord.cx() + "_" + coord.cy() + ".wbc");
    if (!Files.isRegularFile(file)) {
      return null;
    }
    try {
      return ChunkReader.read(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
