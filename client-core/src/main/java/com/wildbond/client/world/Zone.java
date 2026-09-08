package com.wildbond.client.world;

import com.wildbond.sim.SpawnRules;

/**
 * 존 정의 — 청크 폴더 이름과 몬스터 스폰 규칙 (docs/architecture.md D-16, §9.2).
 *
 * <p>맵 3개짜리 M0 범위라 코드에 표로 박아 둔다. 존이 늘어나면 data/tables 로 옮긴다. 종 id 는 data/tables/Monster.csv.
 */
public enum Zone {
  /** 시작 마을 — 몬스터가 나오지 않는다. */
  VILLAGE("village", SpawnRules.none()),

  /** 첫 사냥터(들판) — 사슴(passive)만 돌아다닌다 (D-19). */
  FIELD("field", SpawnRules.of(new int[] {1}, 3, 20)),

  /** 굴 — 맵에 찍어 둔 스폰 포인트에서 늑대·바위 골렘이 나온다. */
  CAVE("cave", new SpawnRules(new int[] {2, 3}, 3, 8, true, SpawnRules.DEFAULT_RESPAWN_TICKS));

  /** 존 하나의 크기(타일). 세 맵 모두 같은 크기라 상수로 둔다. */
  public static final int SIZE_TILES = 64;

  /** 마을 한가운데 — village.tmx 의 십자 흙길이 만나는 지점. 새 게임과 부활(§3.2)이 여기서 시작한다. */
  public static final int VILLAGE_SPAWN_TX = 32;

  public static final int VILLAGE_SPAWN_TY = 32;

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
