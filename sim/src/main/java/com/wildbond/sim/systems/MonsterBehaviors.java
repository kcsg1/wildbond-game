package com.wildbond.sim.systems;

import com.wildbond.sim.systems.bt.Action;
import com.wildbond.sim.systems.bt.BtNode;
import com.wildbond.sim.systems.bt.Condition;
import com.wildbond.sim.systems.bt.Selector;
import com.wildbond.sim.systems.bt.Sequence;
import java.util.List;

/**
 * 몬스터 행동 트리 (docs/architecture.md §9.1). 한 그루를 모든 몬스터가 공유한다 — 성향 차이는 트리 모양이 아니라 {@link AiContext} 의
 * 조건(감지 방식·도주 조건)에서 갈린다. 상태는 전부 {@code Brain} 컴포넌트에 있다.
 *
 * <pre>
 * Selector
 * ├─ Sequence[ Condition(도주해야 함), Action(Flee) ]
 * ├─ Sequence[ Condition(위협 있음), Selector[ Action(UseSkill), Action(Chase) ] ]
 * └─ Sequence[ Action(Wander), Action(Wait) ]
 * </pre>
 */
public final class MonsterBehaviors {

  private MonsterBehaviors() {}

  public static BtNode<AiContext> tree() {
    BtNode<AiContext> fleeBranch =
        new Sequence<>(
            List.<BtNode<AiContext>>of(
                new Condition<AiContext>(AiContext::shouldFlee),
                new Action<AiContext>(AiContext::flee)));
    BtNode<AiContext> engage =
        new Selector<>(
            List.<BtNode<AiContext>>of(
                new Action<AiContext>(AiContext::attackThreat),
                new Action<AiContext>(AiContext::chaseThreat)));
    BtNode<AiContext> combatBranch =
        new Sequence<>(
            List.<BtNode<AiContext>>of(new Condition<AiContext>(AiContext::hasThreat), engage));
    BtNode<AiContext> idleBranch =
        new Sequence<>(
            List.<BtNode<AiContext>>of(
                new Action<AiContext>(AiContext::wander),
                new Action<AiContext>(AiContext::waitIdle)));
    return new Selector<>(List.<BtNode<AiContext>>of(fleeBranch, combatBranch, idleBranch));
  }
}
