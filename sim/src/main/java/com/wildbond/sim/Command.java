package com.wildbond.sim;

import com.wildbond.data.Element;

/**
 * sim 상태를 바꾸는 유일한 통로 (docs/architecture.md §4.2, §6). UI·입력은 이 명령만 만든다 — sim 내부 상태를 직접 고치는 코드는 sim
 * 패키지 밖에 있을 수 없다.
 */
public sealed interface Command {

  /** entityId 는 SimView 가 돌려주는 안정적 id. */
  record MoveInput(int entityId, Dir8 dir, boolean run) implements Command {}

  /** aimAngle 은 {@link Angle} 단위(1/1024 회전, §4.3). CombatSystem(단계 6) 이 처리한다. */
  record UseSkill(int entityId, int skillId, int aimAngle) implements Command {}

  record SpawnPlayer(float x, float y) implements Command {}

  /** 단계 6 전투 확인용 임시 허수아비(EntityKind.DUMMY). 단계 7 에서 실제 PalData 스폰으로 대체될 자리표시자. */
  record SpawnDummy(float x, float y, Element element, int maxHp, int atk, int def, int level)
      implements Command {}
}
