package com.wildbond.sim.components;

import com.artemis.Component;

/**
 * 몬스터 개체 (docs/architecture.md §3.1 MonsterInstance). 종 id 하나면 나머지(레벨·스탯·성향·전리품표)는 {@code Monster}
 * 표에서 다시 찾는다 — 개체값·성장 같은 개체별 변수는 v0.5 에 없다.
 */
public final class MonsterData extends Component {
  public int speciesId;
}
