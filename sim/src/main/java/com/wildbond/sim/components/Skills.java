package com.wildbond.sim.components;

import com.artemis.Component;

/**
 * 엔티티가 아는 스킬과 남은 쿨다운. skillIds[i] 의 쿨다운은 cooldownRemainingTicks[i] (병렬 배열, docs/architecture.md
 * §4.1 Skills(cooldowns[])).
 */
public final class Skills extends Component {
  public int[] skillIds = new int[0];
  public int[] cooldownRemainingTicks = new int[0];
}
