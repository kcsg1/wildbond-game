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

  /**
   * 포획구 투척 (§3.2, §4.2 ThrowSphere). aimAngle 은 {@link Angle} 단위, distancePx 는 착지까지의 지면 거리다 — 던지는
   * 거리 자체가 규칙이므로 sim 이 받아서 판정한다.
   */
  record ThrowSphere(int entityId, int sphereItemId, int aimAngle, float distancePx)
      implements Command {}

  record SpawnPlayer(float x, float y) implements Command {}

  /**
   * 팰 하나를 지정한 자리에 만든다. 야생 스폰은 SpawnSystem 이 알아서 하고(§9.1), 이 명령은 테스트·벤치·시나리오 배치처럼 "정확히 여기에 이 개체"가
   * 필요할 때 쓴다. iv 가 비어 있으면 0 으로 채운다 — 난수를 소비하지 않아 결정성 테스트에 유리하다.
   */
  record SpawnPal(float x, float y, int speciesId, int level) implements Command {}

  /** 단계 6 전투 확인용 임시 허수아비(EntityKind.DUMMY). */
  record SpawnDummy(float x, float y, Element element, int maxHp, int atk, int def, int level)
      implements Command {}
}
