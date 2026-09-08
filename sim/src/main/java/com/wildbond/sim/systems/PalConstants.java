package com.wildbond.sim.systems;

import com.wildbond.sim.Ticks;

/**
 * M0 팰 스폰·AI 튜닝 상수 (docs/architecture.md §9.1, §9.3). {@link SimConstants}·{@link CombatConstants}
 * 와 같은 성격의 임시 고정값이다 — 스폰 테이블·개체 성장 곡선이 데이터 테이블로 들어오면 그쪽으로 옮긴다.
 */
public final class PalConstants {

  // ---- 스폰 (§9.1 "청크 활성화 시 SpawnTable 롤, 화면 밖(≥ 24타일) 생성, 청크 휴면 시 회수")

  /** 활성 청크 반경 — §10 "카메라 반경 2청크"와 맞춘다. */
  public static final int ACTIVE_CHUNK_RADIUS = 2;

  /** M0 임시 스폰 규칙: 청크당 종 1가지, 3마리 (docs/m0-prompts.md 단계7). */
  public static final int PALS_PER_CHUNK = 3;

  public static final int MIN_SPAWN_DISTANCE_TILES = 24;

  public static final int MIN_PAL_LEVEL = 1;
  public static final int MAX_PAL_LEVEL = 5;

  /** 개체값(iv) 상한 — hp/atk/def 각각 0..{@value}. */
  public static final int MAX_IV = 9;

  public static final int PAL_BASE_SAN = 100;

  /** 레벨 1 기준으로 레벨당 스탯 증가율. */
  public static final float STAT_GROWTH_PER_LEVEL = 0.05f;

  // ---- 감지 (§9.1 "시야 12타일(solid/cliff 레이캐스트), 0.25s 간격")

  public static final int SIGHT_RADIUS_TILES = 12;

  public static final int SENSE_INTERVAL_TICKS = Ticks.TICKS_PER_SECOND / 4;

  // ---- 이동

  public static final float WANDER_SPEED_PX_S = 48f;
  public static final float CHASE_SPEED_PX_S = 88f;
  public static final float FLEE_SPEED_PX_S = 112f;
  public static final float FOLLOW_SPEED_PX_S = 104f;

  /** 웨이포인트에 이만큼 가까워지면 다음 웨이포인트로 넘어간다. */
  public static final float WAYPOINT_ARRIVE_PX = 6f;

  // ---- Idle (§9.1 "Wander(12타일) → ... → Wait(3~8s)")

  public static final int WANDER_RADIUS_TILES = 12;
  public static final int WANDER_MAX_TICKS = 8 * Ticks.TICKS_PER_SECOND;
  public static final int WAIT_MIN_TICKS = 3 * Ticks.TICKS_PER_SECOND;
  public static final int WAIT_MAX_TICKS = 8 * Ticks.TICKS_PER_SECOND;

  /** 배회 목표 타일을 찾을 때 시도할 횟수 — 실패하면 그냥 기다린다. */
  public static final int WANDER_TARGET_ATTEMPTS = 6;

  // ---- 전투 (§9.1 Combat: UseSkill(offCooldown, inRange) | Chase)

  /** 추격을 포기하는 거리 — 시야보다 넉넉하게 준다. */
  public static final int CHASE_GIVE_UP_TILES = 18;

  /** 근접 스킬은 사거리(range_px)가 짧아 그대로 쓰면 못 닿는다 — 판정 폭(hit_w)까지 감안해 최소 사거리를 준다. */
  public static final float MIN_ATTACK_RANGE_PX = 40f;

  /** §9.1 Flee: HP < 20% && temperament == timid. */
  public static final float FLEE_HP_RATIO = 0.20f;

  // ---- 파티 동행 (docs/m0-prompts.md 단계7 "FollowOwner(3~6타일, 떨어지면 경로 요청)")

  public static final int FOLLOW_MIN_TILES = 3;
  public static final int FOLLOW_MAX_TILES = 6;

  /** 주인이 때린 대상을 이 시간 동안 기억해 함께 공격한다. */
  public static final int OWNER_TARGET_MEMORY_TICKS = 5 * Ticks.TICKS_PER_SECOND;

  /** 개체별 경로 재요청 최소 간격 — §9.3 틱당 40회 상한을 개체 쪽에서도 아낀다. */
  public static final int REPATH_INTERVAL_TICKS = Ticks.TICKS_PER_SECOND / 2;

  private PalConstants() {}
}
