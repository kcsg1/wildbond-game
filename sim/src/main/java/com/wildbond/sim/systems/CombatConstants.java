package com.wildbond.sim.systems;

import com.wildbond.sim.Ticks;

/** M0 전투 튜닝 상수. 값 자체는 아직 GameData 에 없으므로 SimConstants 와 같은 임시 고정값이다. */
public final class CombatConstants {

  /** 플레이어 기본 스탯 (§3.2 데미지 공식 입력). */
  public static final int PLAYER_ATK = 40;

  public static final int PLAYER_DEF = 20;
  public static final int PLAYER_LEVEL = 1;

  /**
   * 플레이어가 스폰 시 아는 스킬 — 좌클릭(근접)=skill.slash(id 1), 우클릭(원거리)=skill.ember(id 3, fire)
   * (data/tables/Skill.csv). skill.arrow(id 2, 무속성)는 M0 기본 로드아웃에서는 쓰지 않는다 — ember 를 대신 골라 속성
   * 상성(§3.2)을 실제로 시험해 볼 수 있게 했다.
   */
  public static final int[] PLAYER_SKILL_IDS = {1, 3};

  /** §3.2 critical(1.5 if roll) — 확률은 문서에 없어 임시로 고정했다. */
  public static final float CRIT_CHANCE = 0.10f;

  public static final float CRIT_MULTIPLIER = 1.5f;

  /** §3.2 random(0.95, 1.05). */
  public static final float DAMAGE_RANDOM_MIN = 0.95f;

  public static final float DAMAGE_RANDOM_MAX = 1.05f;

  /** 투사체 스킬(hit_shape=projectile) 이동 속도. */
  public static final float PROJECTILE_SPEED_PX_S = 400f;

  /** HP 0 → 5초 뒤 월드에서 제거 (§4.1). */
  public static final int DEAD_REMOVE_TICKS = Ticks.TICKS_PER_SECOND * 5;

  private CombatConstants() {}
}
