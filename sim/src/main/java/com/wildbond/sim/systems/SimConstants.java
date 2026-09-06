package com.wildbond.sim.systems;

/** M0 이동·충돌 튜닝 상수. 값 자체는 아직 GameData 에 없으므로 임시 고정값이다. */
public final class SimConstants {

  public static final int TILE_SIZE_PX = 32;

  public static final float WALK_SPEED_PX_S = 96f; // 타일 3개/초
  public static final float RUN_SPEED_MULTIPLIER = 1.6f;
  public static final float DIAGONAL_SCALE = 0.70710678f; // 1/sqrt(2)

  public static final float PLAYER_WIDTH_PX = 24f;
  public static final float PLAYER_HEIGHT_PX = 24f;
  public static final int PLAYER_MAX_HP = 100;

  public static final float PUSH_STRENGTH = 0.5f;

  private SimConstants() {}
}
