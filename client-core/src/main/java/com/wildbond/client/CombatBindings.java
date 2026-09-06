package com.wildbond.client;

/**
 * 좌클릭=근접, 우클릭=원거리로 보낼 스킬 id (docs/architecture.md §5.4, §4.2 UseSkill). {@code
 * sim.systems.CombatConstants.PLAYER_SKILL_IDS} 와 같은 값이어야 한다 — client-core 는 sim 의 systems 패키지를
 * 참조하지 않으므로(§6, RenderConstants.TILE_PX 와 같은 이유) 여기 따로 둔다.
 */
public final class CombatBindings {

  public static final int MELEE_SKILL_ID = 1; // data/tables/Skill.csv skill.slash
  public static final int RANGED_SKILL_ID = 3; // data/tables/Skill.csv skill.ember

  private CombatBindings() {}
}
