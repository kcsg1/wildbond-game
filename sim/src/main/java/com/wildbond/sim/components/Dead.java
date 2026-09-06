package com.wildbond.sim.components;

import com.artemis.Component;

/** HP 0 이 된 엔티티에 붙는다. ticksRemaining 이 0 이 되면 CombatSystem 이 월드에서 제거한다(§4.1, 5초 유예). */
public final class Dead extends Component {
  public int ticksRemaining;
}
