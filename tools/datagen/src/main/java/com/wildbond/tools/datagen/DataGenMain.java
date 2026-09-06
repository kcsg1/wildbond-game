package com.wildbond.tools.datagen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CSV -> Java record 코드 생성기 (docs/architecture.md §8.1).
 *
 * <pre>
 *   java com.wildbond.tools.datagen.DataGenMain &lt;tablesDir&gt; &lt;outDir&gt;
 * </pre>
 *
 * <p>검증(중복 id, 값 범위, 배열 길이, 열거형 값, 참조 무결성)에 실패하면 0 이 아닌 코드로 끝난다.
 */
public final class DataGenMain {

  private DataGenMain() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 2) {
      System.err.println("usage: DataGenMain <tablesDir> <outDir>");
      System.exit(2);
      return;
    }
    Path tablesDir = Path.of(args[0]);
    Path outDir = Path.of(args[1]);
    if (!Files.isDirectory(tablesDir)) {
      System.err.println("tables 디렉터리가 없다: " + tablesDir.toAbsolutePath());
      System.exit(2);
      return;
    }
    try {
      Schema schema = Schema.load(tablesDir);
      new Validator(schema).validate();
      new Emitter(schema, outDir).emitAll();
      System.out.println(
          "datagen: 표 "
              + schema.tables().size()
              + "개, 열거형 "
              + schema.enums().size()
              + "개 -> "
              + outDir.toAbsolutePath());
    } catch (GenException e) {
      System.err.println("datagen 실패: " + e.getMessage());
      System.exit(1);
    }
  }
}
