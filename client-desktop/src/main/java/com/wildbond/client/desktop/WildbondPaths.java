package com.wildbond.client.desktop;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 세이브·로그 경로 (docs/architecture.md §5.6): {@code %LOCALAPPDATA%\Wildbond\}, 환경 변수가 없으면 실행 폴더. Path
 * API 만 쓴다 — 구분자를 하드코딩하지 않는다. 실제 세이브·로그 쓰기는 이후 단계(SaveSystem, M1).
 */
final class WildbondPaths {

  private WildbondPaths() {}

  static Path dataDir() {
    String localAppData = System.getenv("LOCALAPPDATA");
    Path dir =
        (localAppData != null && !localAppData.isBlank())
            ? Path.of(localAppData, "Wildbond")
            : Path.of("").toAbsolutePath();
    try {
      Files.createDirectories(dir);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return dir;
  }
}
