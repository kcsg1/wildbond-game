package com.wildbond.sim.components;

import com.artemis.Component;

/**
 * 던진 포획구 (docs/architecture.md §3.2 "가상 높이 z 를 가진 포물선 투사체"). {@link Position} 은 그림자가 지는 지면 좌표이고,
 * {@link #z} 는 그 위로 뜬 가상 높이다 — 렌더는 y - z 로 그리고 그림자는 z=0 위치에 찍는다.
 *
 * <p>낙하(z=0)한 타일과 팰 AABB 가 겹치면 CaptureSystem 이 §3.2 포획 공식을 굴린다.
 */
public final class Sphere extends Component {

  public int throwerStableId;
  public int sphereItemId;

  public float dirX;
  public float dirY;
  public float speedPxS;

  /** 지금까지 지면 위로 진행한 거리. */
  public float traveledPx;

  /** 착지까지의 총 지면 거리 — 던진 순간 정해진다. */
  public float totalPx;

  /** 포물선 정점 높이. */
  public float apexPx;

  /** 현재 가상 높이. 착지 시 0. */
  public float z;
}
