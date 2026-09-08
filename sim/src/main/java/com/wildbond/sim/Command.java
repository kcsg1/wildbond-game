package com.wildbond.sim;

import com.wildbond.data.Element;

/**
 * sim 상태를 바꾸는 유일한 통로 (docs/architecture.md §4.2, §6). UI·입력은 이 명령만 만든다 — sim 내부 상태를 직접 고치는 코드는 sim
 * 패키지 밖에 있을 수 없다.
 */
public sealed interface Command {

  /** entityId 는 SimView 가 돌려주는 안정적 id. */
  record MoveInput(int entityId, Dir8 dir, boolean run) implements Command {}

  /** aimAngle 은 {@link Angle} 단위(1/1024 회전, §4.3). CombatSystem 이 처리한다. */
  record UseSkill(int entityId, int skillId, int aimAngle) implements Command {}

  record SpawnPlayer(float x, float y) implements Command {}

  /**
   * 존을 넘어오거나 부활한 플레이어의 진행 상태를 되돌려 준다 (docs/architecture.md D-16 "존을 넘어 유지되는 것"). SpawnPlayer 바로 뒤
   * 같은 틱에 넣는다. hp/mp 가 0 이하이면 레벨에 맞는 최대치로 채운다.
   */
  record RestorePlayer(
      int entityId, int level, int exp, int hp, int mp, int coins, int[] itemIds, int[] counts)
      implements Command {}

  /**
   * 몬스터 하나를 지정한 자리에 만든다. 사냥터 스폰은 SpawnSystem 이 알아서 하고(§9.2), 이 명령은 테스트·벤치·시나리오 배치처럼 "정확히 여기에 이 종"이
   * 필요할 때 쓴다.
   */
  record SpawnMonster(float x, float y, int speciesId) implements Command {}

  /** 전투 테스트용 허수아비(EntityKind.DUMMY) — 어느 진영도 아니라 누구에게나 맞고, 스스로는 아무것도 안 한다. */
  record SpawnDummy(float x, float y, Element element, int maxHp, int atk, int def, int level)
      implements Command {}
}
