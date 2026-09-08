package com.wildbond.sim.systems;

/**
 * docs/architecture.md §3.2 경험치·레벨 공식을 컴포넌트에서 떼어낸 순수 함수 — {@link DamageFormula} 와 같은 이유(정확한 값 테스트).
 *
 * <p>레벨업 때 스탯을 "더하는" 대신 레벨에서 "계산"한다. 그래야 존을 넘어오며 레벨만 복원해도 스탯이 항상 같은 값으로 맞는다.
 */
public final class Progression {

  public static final int MAX_LEVEL = 99;

  private Progression() {}

  /** level 에서 level+1 로 가는 데 필요한 누적 경험치: 20 × level². */
  public static int expToNext(int level) {
    return 20 * level * level;
  }

  /** 누적 경험치가 정하는 레벨 — 레벨업을 여러 번 건너뛰어도 한 번에 맞춘다. */
  public static int levelFor(int startLevel, int exp) {
    int level = startLevel;
    int remaining = exp;
    while (level < MAX_LEVEL && remaining >= expToNext(level)) {
      remaining -= expToNext(level);
      level++;
    }
    return level;
  }

  /** 레벨업하며 소비하고 남는 경험치. */
  public static int expCarried(int startLevel, int exp) {
    int level = startLevel;
    int remaining = exp;
    while (level < MAX_LEVEL && remaining >= expToNext(level)) {
      remaining -= expToNext(level);
      level++;
    }
    return remaining;
  }

  public static int maxHpAt(int level) {
    return SimConstants.PLAYER_MAX_HP + 10 * (level - 1);
  }

  public static int maxMpAt(int level) {
    return CombatConstants.PLAYER_MAX_MP + 5 * (level - 1);
  }

  public static int atkAt(int level) {
    return CombatConstants.PLAYER_ATK + 2 * (level - 1);
  }

  public static int defAt(int level) {
    return CombatConstants.PLAYER_DEF + (level - 1);
  }
}
