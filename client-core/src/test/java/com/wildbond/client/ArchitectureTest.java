package com.wildbond.client;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * client-core 는 sim 을 SimView(읽기)·Command(쓰기)로만 쓴다 (CLAUDE.md "sim 규칙" 2, docs/architecture.md §6
 * 규칙 3). 위반하면 멀티플레이 확장 경로가 막힌다.
 */
@AnalyzeClasses(
    packages = "com.wildbond.client",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule noSimComponents =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.wildbond.sim.components..")
          .because("client-core 는 sim 의 컴포넌트 클래스를 직접 참조하지 않는다 (§6 규칙 3)");

  @ArchTest
  static final ArchRule noSimSystemsInternals =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.wildbond.sim.systems..")
          .because("client-core 는 sim 의 내부 시스템 구현이 아니라 공개 API(Sim/SimView/Command)만 쓴다 (§6 규칙 3)");
}
