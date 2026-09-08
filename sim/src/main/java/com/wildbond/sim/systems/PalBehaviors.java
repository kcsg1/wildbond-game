package com.wildbond.sim.systems;

import com.wildbond.sim.systems.bt.Action;
import com.wildbond.sim.systems.bt.BtNode;
import com.wildbond.sim.systems.bt.Condition;
import com.wildbond.sim.systems.bt.Selector;
import com.wildbond.sim.systems.bt.Sequence;
import java.util.List;

/**
 * 팰 행동 트리 두 그루 (docs/architecture.md §9.1, docs/m0-prompts.md 단계7). 트리는 한 번만 만들고 모든 팰이 같은 인스턴스를
 * 공유한다 — 상태는 전부 {@code Brain} 컴포넌트에 있다.
 *
 * <p>M0 범위는 §9.1 의 Flee / Combat / Idle 세 갈래다. Investigate(청각)와 Pack(무리)은 M0 프롬프트 범위 밖이라 넣지 않았다.
 */
public final class PalBehaviors {

  private PalBehaviors() {}

  /**
   * 야생 팰:
   *
   * <pre>
   * Selector
   * ├─ Sequence[ Condition(HP&lt;20% &amp;&amp; timid), Action(Flee) ]
   * ├─ Sequence[ Condition(위협 있음), Selector[ Action(UseSkill), Action(Chase) ] ]
   * └─ Sequence[ Action(Wander), Action(Wait) ]
   * </pre>
   */
  public static BtNode<AiContext> wildTree() {
    BtNode<AiContext> fleeBranch =
        new Sequence<>(
            List.<BtNode<AiContext>>of(
                new Condition<AiContext>(AiContext::shouldFlee),
                new Action<AiContext>(AiContext::flee)));
    BtNode<AiContext> idleBranch =
        new Sequence<>(
            List.<BtNode<AiContext>>of(
                new Action<AiContext>(AiContext::wander),
                new Action<AiContext>(AiContext::waitIdle)));
    return new Selector<>(List.<BtNode<AiContext>>of(fleeBranch, combatBranch(), idleBranch));
  }

  /**
   * 파티 팰: 주인이 공격한 대상이 있으면 함께 싸우고, 없으면 주인을 따라다닌다.
   *
   * <pre>
   * Selector
   * ├─ Sequence[ Condition(주인의 대상 있음), Selector[ Action(UseSkill), Action(Chase) ] ]
   * └─ Action(FollowOwner)
   * </pre>
   */
  public static BtNode<AiContext> partyTree() {
    return new Selector<>(
        List.<BtNode<AiContext>>of(combatBranch(), new Action<AiContext>(AiContext::followOwner)));
  }

  private static BtNode<AiContext> combatBranch() {
    BtNode<AiContext> engage =
        new Selector<>(
            List.<BtNode<AiContext>>of(
                new Action<AiContext>(AiContext::attackThreat),
                new Action<AiContext>(AiContext::chaseThreat)));
    return new Sequence<>(
        List.<BtNode<AiContext>>of(new Condition<AiContext>(AiContext::hasThreat), engage));
  }
}
