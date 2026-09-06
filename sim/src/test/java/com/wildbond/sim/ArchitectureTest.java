package com.wildbond.sim;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * sim 격리·결정성 규칙을 빌드에서 강제한다. CLAUDE.md "sim 규칙", docs/architecture.md §4.3 · §5.1.
 *
 * <p>규칙을 완화하려면 문서를 먼저 고치고 plan.md 에 사유를 남긴다.
 */
@AnalyzeClasses(packages = "com.wildbond.sim", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  /** §5.1 — sim 은 LibGDX 를 참조하지 않는다. */
  @ArchTest
  static final ArchRule noLibgdx =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.badlogic..")
          .because("sim 은 엔진에 의존하지 않는 순수 Java 여야 한다 (§5.1)");

  /** §6 — 멀티플레이 코드는 지금 넣지 않는다. */
  @ArchTest
  static final ArchRule noNetworking =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("io.netty..", "java.net..")
          .because("멀티플레이·네트워크는 현재 범위 밖이다 (§6)");

  /** §4.3 — 난수는 Rng 스트림만 쓴다. */
  @ArchTest
  static final ArchRule noJdkRandom =
      noClasses()
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("java.util.Random")
          .orShould()
          .callMethod(Math.class, "random")
          .because("결정성을 위해 난수는 Rng 스트림만 사용한다 (§4.3)");

  /** §4.3 — sim 안에서는 로깅하지 않고 이벤트로 내보낸다. */
  @ArchTest
  static final ArchRule noLogging =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.slf4j..", "java.util.logging..")
          .because("sim 은 로깅하지 않는다 — 이벤트로 내보낸다 (§4.3)");

  /** 표준 출력은 디버깅 잔재이므로 남기지 않는다. :sim:bench 는 결과를 콘솔에 내야 하는 CLI 라 예외. */
  @ArchTest
  static final ArchRule noStandardStreams =
      noClasses()
          .that()
          .resideOutsideOfPackage("com.wildbond.sim.bench..")
          .should()
          .accessField(System.class, "out")
          .orShould()
          .accessField(System.class, "err")
          .because("디버깅 잔재를 남기지 않는다 (:sim:bench 결과 출력은 예외)");

  /**
   * §4.1, §6 — 컴포넌트는 components/systems 패키지에서만 다룬다. Sim/SimView 를 포함한 공개 API 는 원시 값만
   * 넘긴다(EntityQueries 가 유일한 창구). 이 규칙은 sim 모듈 자체 범위에서만 검사되지만, client-core 가 실제로 sim 을 참조하기 시작하는 단계
   * 5부터 §6 규칙 1(상태 변경은 Command 로만)의 전제가 된다.
   */
  @ArchTest
  static final ArchRule componentsOnlyUsedBySystems =
      noClasses()
          .that()
          .resideOutsideOfPackages("com.wildbond.sim.components..", "com.wildbond.sim.systems..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.wildbond.sim.components..")
          .because("컴포넌트는 systems 패키지에서만 직접 다룬다 (§4.1, §6)");
}
