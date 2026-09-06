// docs/architecture.md §5.1 — sim → data. LibGDX 참조 금지(ArchitectureTest 가 강제).
dependencies {
    api(project(":data"))
    implementation(libs.artemis.odb)
    testImplementation(libs.archunit.junit5)
}

// 테스트·벤치 모두 같은 CSV 원천을 쓴다 (data 모듈 규약과 동일, plan.md 단계 2 참고).
val tablesDir = rootProject.layout.projectDirectory.dir("data/tables")

tasks.named<Test>("test") {
    systemProperty("wildbond.tables", tablesDir.asFile.absolutePath)
}

// 헤드리스 sim 부하 테스트. docs/architecture.md §9.4 예산(평균 틱 ≤ 8ms)을 검증한다.
// 하네스(harness.ps1)가 태스크 목록에서 "sim:bench" 를 찾아 전체 모드에 자동 포함한다.
val bench by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "500 엔티티 1000틱 헤드리스 벤치 — 평균 틱 > 8ms 면 실패 (docs/architecture.md §9.4)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.wildbond.sim.bench.SimBench")
    args(tablesDir.asFile.absolutePath)
}
