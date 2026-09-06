package com.wildbond.client.render;

/**
 * 렌더 쪽에서 필요한 월드 상수. sim 의 값(예: {@code SimConstants.TILE_SIZE_PX})과 항상 같아야 한다 — client-core 는 sim 의
 * systems/components 패키지를 참조하지 않으므로(§6) 여기서 따로 둔다. CLAUDE.md "좌표" 규칙: 타일은 32px 로 고정.
 */
public final class RenderConstants {

  public static final int TILE_PX = 32;

  public static final float PLAYER_WIDTH_PX = 32f;
  public static final float PLAYER_HEIGHT_PX = 48f;

  private RenderConstants() {}
}
