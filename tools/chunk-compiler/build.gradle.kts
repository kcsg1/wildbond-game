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
    description = "Kenney Tiny Town(CC0) 시트에서 지형 타일을 뽑아 assets/tilesets/placeholder.png 를 만든다"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.wildbond.tools.chunk.PlaceholderTilesetGenerator")
    val tinyTownSheet =
        rootProject.layout.projectDirectory.file("assets/tilesets/kenney_tiny_town.png")
    args(tinyTownSheet.asFile.absolutePath, tilesetFile.asFile.absolutePath)
    inputs.file(tinyTownSheet)
    outputs.file(tilesetFile)
}

val zones = listOf("village", "field", "cave")

// 존마다 tmx 하나 → assets/maps/chunks/<zone>/ (docs/architecture.md D-16)
val zoneTasks = zones.map { zone ->
    tasks.register<JavaExec>("compileChunks${zone.replaceFirstChar { it.uppercase() }}") {
        group = "build"
        description = "$zone.tmx 를 .wbc 청크로 컴파일한다"
        dependsOn(generatePlaceholderTileset)
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("com.wildbond.tools.chunk.ChunkCompilerMain")
        // zstd-jni 네이티브 로드 경고 방지 (troubleshooting.md T-004).
        jvmArgs("--enable-native-access=ALL-UNNAMED")
        val tmxFile = rootProject.layout.projectDirectory.file("assets/maps/src/$zone.tmx")
        val tablesDir = rootProject.layout.projectDirectory.dir("data/tables")
        val chunksDir = rootProject.layout.projectDirectory.dir("assets/maps/chunks/$zone")
        args(tmxFile.asFile.absolutePath, tablesDir.asFile.absolutePath, chunksDir.asFile.absolutePath)
        inputs.file(tmxFile)
        inputs.dir(tablesDir)
        outputs.dir(chunksDir)
    }
}

val compileChunks by tasks.registering {
    group = "build"
    description = "모든 존의 청크를 컴파일한다"
    dependsOn(zoneTasks)
}
