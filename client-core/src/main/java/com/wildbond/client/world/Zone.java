package com.wildbond.client.world;

import com.wildbond.sim.SpawnRules;

/**
 * 존 정의 — 청크 폴더 이름과 야생 스폰 규칙 (docs/architecture.md D-16).
 *
 * <p>맵 3개짜리 M0 범위라 코드에 표로 박아 둔다. 존이 늘어나면 data/tables 로 옮긴다.
 */
public enum Zone {
  /** 시작 마을 — 몬스터가 나오지 않는다. */
  VILLAGE("village", SpawnRules.none()),

  /** 야외 들판 — 초원 팰이 돌아다닌다. */
  FIELD("field", new SpawnRules(new int[] {1, 2}, 3, 20, false)),

  /** 굴 — 맵에 찍어 둔 스폰 포인트에서 바위 팰만 나온다. */
  CAVE("cave", new SpawnRules(new int[] {3}, 3, 8, true));

  /** 존 하나의 크기(타일). 세 맵 모두 같은 크기라 상수로 둔다. */
  public static final int SIZE_TILES = 64;

  private final String chunkDir;
  private final SpawnRules spawnRules;

  Zone(String chunkDir, SpawnRules spawnRules) {
    this.chunkDir = chunkDir;
    this.spawnRules = spawnRules;
  }

  public String chunkDir() {
    return chunkDir;
  }

  public SpawnRules spawnRules() {
    return spawnRules;
  }
}
