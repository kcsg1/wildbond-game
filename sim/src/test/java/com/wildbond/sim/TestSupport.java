package com.wildbond.sim;

import com.wildbond.data.GameData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/** 테스트 공용 픽스처. CSV 경로는 Gradle 이 {@code wildbond.tables} 시스템 프로퍼티로 넘긴다. */
final class TestSupport {

  private static GameData cached;

  private TestSupport() {}

  static synchronized GameData gameData() {
    if (cached == null) {
      Path dir = Path.of(System.getProperty("wildbond.tables", "../data/tables"));
      try {
        cached = GameData.load(dir);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return cached;
  }

  static Sim newSim(TileMap map, long seed) {
    return new Sim(gameData(), map, seed);
  }

  static ArrayTileMap openMap(int width, int height) {
    return new ArrayTileMap(width, height, new byte[width * height]);
  }
}
