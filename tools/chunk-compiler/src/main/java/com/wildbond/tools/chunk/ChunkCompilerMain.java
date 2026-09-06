package com.wildbond.tools.chunk;

import com.wildbond.data.GameData;
import com.wildbond.data.chunk.Chunk;
import com.wildbond.data.chunk.ChunkWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tiled(.tmx, CSV 인코딩) -&gt; 바이너리 청크(.wbc) 컴파일러 (docs/architecture.md §8.2).
 *
 * <pre>
 *   java com.wildbond.tools.chunk.ChunkCompilerMain &lt;tmx&gt; &lt;tablesDir&gt; &lt;outDir&gt;
 * </pre>
 */
public final class ChunkCompilerMain {

  private ChunkCompilerMain() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      System.err.println("usage: ChunkCompilerMain <tmx> <tablesDir> <outDir>");
      System.exit(2);
      return;
    }
    Path tmxFile = Path.of(args[0]);
    Path tablesDir = Path.of(args[1]);
    Path outDir = Path.of(args[2]);

    if (!Files.isRegularFile(tmxFile)) {
      System.err.println("tmx 파일이 없다: " + tmxFile.toAbsolutePath());
      System.exit(2);
      return;
    }

    GameData gameData = GameData.load(tablesDir);
    TmxMap map = TmxParser.parse(tmxFile);
    List<Chunk> chunks = ChunkCompiler.compile(map, gameData);

    Files.createDirectories(outDir);
    for (Chunk chunk : chunks) {
      Path file = outDir.resolve(chunk.coord().cx() + "_" + chunk.coord().cy() + ".wbc");
      ChunkWriter.write(chunk, file);
    }

    System.out.println(
        "chunk-compiler: "
            + tmxFile.getFileName()
            + " -> 청크 "
            + chunks.size()
            + "개 -> "
            + outDir.toAbsolutePath());
  }
}
