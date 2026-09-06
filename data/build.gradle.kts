// docs/architecture.md §5.1 — 최하위 모듈. 프로젝트 내부 의존 없음.
// 정적 테이블은 data/tables/*.csv 가 원천이고, tools/datagen 이 Java record 를 생성한다 (§8.1).

// datagen 을 "도구"로만 쓴다. datagen 은 :data 에 의존하지 않으므로 순환이 없다.
val datagenTool: Configuration = configurations.create("datagenTool")

dependencies {
    datagenTool(project(":tools:datagen"))
    // §5.6 — windows-x64 네이티브만 받는다. zstd-jni 는 기본 아티팩트가 전 플랫폼 네이티브를 한 jar 에
    // 묶으므로, win_amd64 classifier 로 받는다 (Windows amd64 .dll 만 포함).
    implementation(variantOf(libs.zstd.jni) { classifier("win_amd64") })
}

val tablesDir = layout.projectDirectory.dir("tables")
val generatedSrcDir = layout.buildDirectory.dir("generated/sources/datagen/java")

val generateData by tasks.registering(JavaExec::class) {
    group = "build"
    description = "data/tables/*.csv 를 검증하고 Java record 로 생성한다"
    classpath = datagenTool
    mainClass.set("com.wildbond.tools.datagen.DataGenMain")
    args(tablesDir.asFile.absolutePath, generatedSrcDir.get().asFile.absolutePath)
    inputs.dir(tablesDir).withPropertyName("tables")
    outputs.dir(generatedSrcDir).withPropertyName("generatedSources")
}

sourceSets.named("main") {
    java.srcDir(generatedSrcDir)
}

tasks.named("compileJava") {
    dependsOn(generateData)
}

tasks.named<Test>("test") {
    // 테스트가 CSV 를 찾을 경로. 작업 디렉터리에 의존하지 않게 명시적으로 넘긴다.
    systemProperty("wildbond.tables", tablesDir.asFile.absolutePath)
}
