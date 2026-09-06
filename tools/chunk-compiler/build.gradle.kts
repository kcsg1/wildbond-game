// docs/architecture.md §5.1 — tools/chunk-compiler → data (ChunkFormat 사용, 단계 4).
dependencies {
    implementation(project(":data"))
}

tasks.named<Test>("test") {
    systemProperty("wildbond.tables", rootProject.layout.projectDirectory.dir("data/tables").asFile.absolutePath)
    systemProperty(
        "wildbond.testIsland",
        rootProject.layout.projectDirectory.file("assets/maps/src/test_island.tmx").asFile.absolutePath,
    )
}

val tilesetFile = rootProject.layout.projectDirectory.file("assets/tilesets/placeholder.png")

val generatePlaceholderTileset by tasks.registering(JavaExec::class) {
    group = "build"
    description = "assets/tilesets/placeholder.png 를 색상 블록으로 생성한다 (docs/m0-prompts.md 단계4)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.wildbond.tools.chunk.PlaceholderTilesetGenerator")
    args(tilesetFile.asFile.absolutePath)
    outputs.file(tilesetFile)
}

val compileChunks by tasks.registering(JavaExec::class) {
    group = "build"
    description = "assets/maps/src/test_island.tmx 를 .wbc 청크로 컴파일한다 (docs/architecture.md §8.2)"
    dependsOn(generatePlaceholderTileset)
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.wildbond.tools.chunk.ChunkCompilerMain")
    // zstd-jni 네이티브 로드 경고 방지 (troubleshooting.md T-004).
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    val tmxFile = rootProject.layout.projectDirectory.file("assets/maps/src/test_island.tmx")
    val tablesDir = rootProject.layout.projectDirectory.dir("data/tables")
    val chunksDir = rootProject.layout.projectDirectory.dir("assets/maps/chunks")
    args(tmxFile.asFile.absolutePath, tablesDir.asFile.absolutePath, chunksDir.asFile.absolutePath)
    inputs.file(tmxFile)
    inputs.dir(tablesDir)
    outputs.dir(chunksDir)
}
