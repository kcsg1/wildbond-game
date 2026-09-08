package com.wildbond.sim.components;

import com.artemis.Component;

/**
 * 팰 개체 데이터 (docs/architecture.md §3.1 PalInstance, §4.1 {@code PalData(speciesId, level, iv[],
 * passives[], san)}).
 *
 * <p>HP 는 여기 두지 않는다 — §4.1 은 Health 를 별도 컴포넌트로 두고 있고, 전투(단계 6)가 이미 Health 를 쓴다. 패시브 4슬롯은 M0 에 소비하는
 * 시스템이 없어 넣지 않았다(plan.md 단계7 "결정" 참고).
 */
public final class PalData extends Component {

  public int speciesId;
  public int level;

  /** §3.1 SAN. M0 에는 감소·회복 규칙(SurvivalSystem, §4.1 시스템 7번)이 없어 초기값만 유지한다. */
  public int san;

  /** 개체값 — 순서대로 hp/atk/def. */
  public int[] iv = new int[3];
}
