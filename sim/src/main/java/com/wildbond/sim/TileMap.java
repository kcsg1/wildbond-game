package com.wildbond.sim;

import com.wildbond.data.TileCollision;

/** 타일 충돌 조회. 단계 4 에서 청크 기반 구현({@code ChunkTileMap})이 추가된다 (docs/architecture.md §4.1, §8.2). */
public interface TileMap {

  /** 타일 좌표(tx,ty)의 충돌 종류. 월드 밖은 구현체가 SOLID 등으로 막는 것을 권장한다. */
  TileCollision collision(int tileX, int tileY);
}
