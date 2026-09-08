package com.wildbond.client;

/**
 * 포획 조작 바인딩 (docs/m0-prompts.md 단계7 "숫자 키 1 → 포획구 던지기(마우스 방향, 고정 거리 6타일)").
 *
 * <p>인벤토리가 없는 M0 은 1단계 포획구 하나만 무한히 쓴다 — 소지품·수량은 M1(§8.3 세이브)에서 들어온다. 실제 포획 확률·낙하 판정은 전부 sim
 * (CaptureSystem)이 정한다.
 */
public final class CaptureBindings {

  /** data/tables/Item.csv item.sphere_basic (1단계, 배수 1.0). */
  public static final int SPHERE_ITEM_ID = 1;

  /** 던지는 거리 — 6타일 고정. RenderConstants.TILE_PX 와 같은 이유로 client 쪽 상수를 쓴다. */
  public static final float THROW_DISTANCE_PX = 6f * 32f;

  private CaptureBindings() {}
}
