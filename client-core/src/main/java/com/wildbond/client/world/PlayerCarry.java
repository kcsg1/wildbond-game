package com.wildbond.client.world;

/**
 * 존을 넘어갈 때 들고 가는 플레이어 상태 (docs/architecture.md D-16 "존을 넘어 유지되는 것은 player 상태뿐").
 *
 * <p>존 전환은 sim 을 새로 만드는 것이라 야생 팰·드롭 같은 존 로컬 상태는 버려진다. 그 경계를 명시적으로 드러내려고 넘길 값만 이 레코드에 모았다. 파티 팰까지
 * 옮기는 것은 세이브 포맷(§8.3)이 생기는 M1 몫이다.
 */
public record PlayerCarry(int hp, int mp, int coins) {

  /** 새 게임 — sim 기본값을 쓰라는 뜻으로 hp/mp 에 -1 을 넣는다. */
  public static PlayerCarry initial() {
    return new PlayerCarry(-1, -1, 0);
  }
}
