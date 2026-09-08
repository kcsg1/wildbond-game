package com.wildbond.sim;

/**
 * 존마다 다른 스폰표 (docs/architecture.md §9.2). 마을처럼 아무것도 안 나오는 곳, 굴처럼 정해진 종만 나오는 곳을 구분한다.
 *
 * @param speciesIds 이 존에 나올 수 있는 종. 비어 있으면 표에 있는 모든 종
 * @param perChunk 청크마다 유지할 마리 수 — 처음 켜질 때 채우고, 죽어서 비면 리스폰 간격 뒤에 다시 채운다
 * @param minDistanceTiles 플레이어에게서 최소 이만큼 떨어진 곳에만 만든다
 * @param useSpawnPoints 참이면 청크 오브젝트의 {@code spawn_point} 자리에만 만든다 (굴처럼 방 안에만 두고 싶을 때)
 * @param respawnTicks 빈 자리를 다시 채우기까지의 간격
 */
public record SpawnRules(
    int[] speciesIds,
    int perChunk,
    int minDistanceTiles,
    boolean useSpawnPoints,
    int respawnTicks) {

  public static final int DEFAULT_RESPAWN_TICKS = 20 * Ticks.TICKS_PER_SECOND;

  /** 아무것도 나오지 않는다 — 마을. */
  public static SpawnRules none() {
    return new SpawnRules(new int[0], 0, 0, false, DEFAULT_RESPAWN_TICKS);
  }

  public static SpawnRules of(int[] speciesIds, int perChunk, int minDistanceTiles) {
    return new SpawnRules(speciesIds, perChunk, minDistanceTiles, false, DEFAULT_RESPAWN_TICKS);
  }

  /** 마리 수가 0 이면 꺼진 것이다. */
  public boolean enabled() {
    return perChunk > 0;
  }
}
