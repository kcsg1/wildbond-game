import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.spotless) apply false
}

// subprojects {} 블록 안에서는 type-safe 카탈로그 접근자를 쓸 수 없으므로
// 스크립트 최상단에서 한 번 잡아 둔다.
val catalog = libs

// 대상 Java 버전은 gradle.properties 의 wildbond.javaVersion (기본 25, D-15).
// 다른 JDK 로 검증할 때만 -Pwildbond.javaVersion=NN 으로 덮어쓴다.
val wildbondJavaVersion: Int =
    (providers.gradleProperty("wildbond.javaVersion").orNull ?: "25").toInt()

// 공통 규약. 모듈이 6개뿐이고, build-logic(Kotlin 컴파일)을 도입하면 JDK 25 에서
// embedded Kotlin 의 jvmTarget 문제가 생길 수 있어 여기서 적용한다.
// (plan.md 2026-09-04 단계 1 "결정" 참고. 필요해지면 build-logic 으로 분리)
subprojects {
    apply(plugin = "java-library")
    apply(plugin = "com.diffplug.spotless")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(wildbondJavaVersion))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
    }

    dependencies {
        add("testImplementation", platform(catalog.junit.bom))
        add("testImplementation", catalog.junit.jupiter)
        add("testImplementation", catalog.assertj.core)
        add("testRuntimeOnly", catalog.junit.platform.launcher)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // 하네스가 build/test-results/test/*.xml 을 집계한다.
        reports.junitXml.required.set(true)
        testLogging {
            events("failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
        // JDK 25 는 네이티브 라이브러리 로드(zstd-jni 등)에 경고를 낸다 — 나중 릴리스에서 막힐 수 있다고
        // 명시한다. 미리 허용해 둔다 (troubleshooting.md T-003).
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }

    extensions.configure<SpotlessExtension> {
        java {
            target("src/**/*.java")
            googleJavaFormat(catalog.versions.googleJavaFormat.get())
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}
