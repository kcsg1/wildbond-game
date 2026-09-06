// docs/architecture.md §5.1 — client-core → sim → data. LibGDX 는 여기서부터 쓴다(sim 은 여전히 금지).
dependencies {
    api(project(":sim"))
    api(libs.gdx.core)
    testImplementation(libs.archunit.junit5)
}
