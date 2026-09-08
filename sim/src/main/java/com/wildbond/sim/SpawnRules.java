package com.wildbond.sim;

/**
 * 존마다 다른 야생 스폰 규칙 (docs/architecture.md D-16). 마을처럼 아무것도 안 나오는 곳, 굴처럼 정해진 종만 나오는 곳을 구분한다.
 *
 * @param speciesIds 이 존에 나올 수 있는 종 (빈 배열이면 스폰하지 않는다)
 * @param palsPerChunk 청크가 새로 켜질 때 만들 마리 수
 * @param minDistanceTiles 플레이어에게서 최소 이만큼 떨어진 곳에만 만든다 (§9.1 "화면 밖")
 * @param useSpawnPoints 참이면 청크 오브젝트의 {@code spawn_point} 자리에만 만든다 (굴처럼 방 안에만 두고 싶을 때)
 */
public record SpawnRules(
    int[] speciesIds, int palsPerChunk, int minDistanceTiles, boolean useSpawnPoints) {

  /** 아무것도 나오지 않는다 — 마을. */
  public static SpawnRules none() {
    return new SpawnRules(new int[0], 0, 0, false);
  }

  /** 마리 수가 0 이면 꺼진 것이다. speciesIds 가 비어 있으면 "표에 있는 모든 종"을 뜻한다. */
  public boolean enabled() {
    return palsPerChunk > 0;
  }
}
