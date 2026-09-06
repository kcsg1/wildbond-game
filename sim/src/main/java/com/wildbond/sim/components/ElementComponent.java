package com.wildbond.sim.components;

import com.artemis.Component;
import com.wildbond.data.Element;

/**
 * 엔티티의 속성 — 데미지 공식(§3.2)의 defender.element 로 쓰인다. {@code Component} 이름이 {@code Element}(data 모듈
 * 열거형)와 겹치므로 접미사를 붙였다(EntityIdComponent 와 같은 관례).
 */
public final class ElementComponent extends Component {
  public Element value;
}
