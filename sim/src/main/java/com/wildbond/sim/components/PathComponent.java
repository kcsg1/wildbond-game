package com.wildbond.sim.components;

import com.artemis.Component;

/**
 * 타일 웨이포인트 경로 (docs/architecture.md §4.1 {@code Path(waypoints[], idx)}, §9.3). 이름에 접미사를 붙인 것은
 * {@code java.nio.file.Path} 와 헷갈리지 않게 하기 위함이다 — {@link EntityIdComponent}·{@link ElementComponent}
 * 와 같은 관례.
 *
 * <p>배열은 §9.3 "경로 길이 상한 200"에 맞춰 고정 크기로 미리 잡는다. 경로 요청은 매 틱 일어나지 않으므로(§9.3 틱당 40회 상한, 개체별 재요청 간격) 핫
 * 루프 할당 금지 규칙과 충돌하지 않는다.
 */
public final class PathComponent extends Component {

  public static final int MAX_TILES = 200;

  public final int[] tileX = new int[MAX_TILES];
  public final int[] tileY = new int[MAX_TILES];

  /** 유효한 웨이포인트 수. 0 이면 경로 없음. */
  public int length;

  /** 다음에 향할 웨이포인트 인덱스. */
  public int index;

  public void clear() {
    length = 0;
    index = 0;
  }
}
