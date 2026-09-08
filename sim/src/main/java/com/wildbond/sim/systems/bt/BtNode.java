package com.wildbond.sim.systems.bt;

/**
 * 자체 경량 Behavior Tree 노드 (docs/architecture.md §9.1 "sim 내 자체 경량 BT, 외부 라이브러리 없음").
 *
 * <p>컨텍스트 타입 {@code C} 를 제네릭으로 두어 이 패키지가 sim 의 컴포넌트·시스템을 전혀 모르게 했다 — 트리는 한 번만 만들어 재사용하고, 매 틱 노드를 새로
 * 할당하지 않는다(§4.3 핫 루프 할당 금지).
 *
 * @param <C> 트리가 읽고 쓰는 컨텍스트(대상 엔티티·월드 접근자)
 */
public interface BtNode<C> {
  BtStatus tick(C context);
}
