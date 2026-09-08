package com.wildbond.sim.components;

import com.artemis.Component;

/**
 * 이 엔티티를 스폰시킨 청크 (docs/architecture.md §9.1 "청크 휴면 시 회수"). SpawnSystem 이 청크가 비활성으로 바뀌면 이 좌표를 보고
 * 회수한다 — 주인이 생긴(포획된) 팰은 회수하지 않는다.
 */
public final class SpawnOrigin extends Component {
  public int chunkX;
  public int chunkY;
}
