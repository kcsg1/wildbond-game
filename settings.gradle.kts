rootProject.name = "wildbond-game"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// docs/architecture.md §5.1 — 모듈 목록
include(
    "data",
    "sim",
    "client-core",
    "client-desktop",
    "tools:chunk-compiler",
    "tools:datagen",
)
