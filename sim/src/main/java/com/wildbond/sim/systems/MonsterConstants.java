package com.wildbond.sim.systems;

import com.wildbond.sim.Ticks;

/**
 * 몬스터 스폰·AI 튜닝 상수 (docs/architecture.md §9). 종마다 다른 값(속도·스탯)은 {@code Monster} 표에 있고, 여기는 종과 무관한 규칙
 * 상수만 둔다.
 */
public final class MonsterConstants {

  // ---- 스폰 (§9.2)

  /** 활성 청크 반경 — §10 "카메라 반경 2청크"와 맞춘다. */
  public static final int ACTIVE_CHUNK_RADIUS = 2;

  // ---- 감지 (§9.1 "12타일 시야, 0.25s 간격")

  public static final int SIGHT_RADIUS_TILES = 12;
  public static final int SENSE_INTERVAL_TICKS = Ticks.TICKS_PER_SECOND / 4;

  /** passive/timid 가 맞고 나서 공격자를 기억하는 시간. */
  public static final int HIT_MEMORY_TICKS = 4 * Ticks.TICKS_PER_SECOND;

  /** 추격을 포기하는 거리 — 시야보다 넉넉하게 준다. */
  public static final int CHASE_GIVE_UP_TILES = 18;

  // ---- 이동 배율 (Monster.speed_px_s 기준)

  public static final float CHASE_SPEED_MULT = 1.8f;
  public static final float FLEE_SPEED_MULT = 2.3f;

  /** 웨이포인트에 이만큼 가까워지면 다음 웨이포인트로 넘어간다. */
  public static final float WAYPOINT_ARRIVE_PX = 6f;

  // ---- Idle (§9.1 "Wander(12타일) → Wait(3~8s)")

  public static final int WANDER_RADIUS_TILES = 12;
  public static final int WANDER_MAX_TICKS = 8 * Ticks.TICKS_PER_SECOND;
  public static final int WAIT_MIN_TICKS = 3 * Ticks.TICKS_PER_SECOND;
  public static final int WAIT_MAX_TICKS = 8 * Ticks.TICKS_PER_SECOND;
  public static final int WANDER_TARGET_ATTEMPTS = 6;

  // ---- 전투

  /** 근접 스킬은 사거리(range_px)가 짧아 그대로 쓰면 못 닿는다 — 판정 폭(hit_w)까지 감안해 최소 사거리를 준다. */
  public static final float MIN_ATTACK_RANGE_PX = 40f;

  /** §9.1 Flee: timid && HP < 20%. */
  public static final float FLEE_HP_RATIO = 0.20f;

  /** 개체별 경로 재요청 최소 간격 — §9.3 틱당 40회 상한을 개체 쪽에서도 아낀다. */
  public static final int REPATH_INTERVAL_TICKS = Ticks.TICKS_PER_SECOND / 2;

  private MonsterConstants() {}
}
