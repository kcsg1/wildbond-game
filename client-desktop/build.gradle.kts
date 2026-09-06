// docs/architecture.md §5.1 — client-desktop → client-core. LWJGL3 런처(windows-x64 natives, §5.6).
plugins {
    application
}

// gdx-backend-lwjgl3 의 POM 은 linux/macos/windows(+arm) 네이티브를 전부 딸려 보낸다. org.lwjgl 그룹을
// 통째로 제외하고, windows-x64 에 필요한 것만 버전을 맞춰(gradle/libs.versions.toml lwjgl 버전 참고) 되붙인다.
val lwjglModules =
    listOf("lwjgl", "lwjgl-glfw", "lwjgl-jemalloc", "lwjgl-openal", "lwjgl-opengl", "lwjgl-stb")

dependencies {
    implementation(project(":client-core"))
    implementation(libs.gdx.backend.lwjgl3) {
        exclude(group = "org.lwjgl")
    }
    lwjglModules.forEach { module ->
        implementation("org.lwjgl:$module:${libs.versions.lwjgl.get()}")
        implementation("org.lwjgl:$module:${libs.versions.lwjgl.get()}:natives-windows")
    }
    // GdxNativesLoader(gdx64.dll)는 lwjgl3 백엔드도 여전히 필요로 한다. libgdx 는 이걸 windows 전용으로
    // 쪼개 배포하지 않는다 — natives-desktop(~1.1MB, win/mac/linux 를 한 jar 에 묶음)이 가장 작은 선택지다.
    // §5.6 예외로 문서화 (plan.md 단계5 "결정" 참고).
    implementation("com.badlogicgames.gdx:gdx-platform:${libs.versions.gdx.get()}:natives-desktop")
}

application {
    mainClass.set("com.wildbond.client.desktop.DesktopLauncher")
}

// 개발 실행은 작업 디렉터리에 기대지 않고 절대 경로를 넘긴다 (:sim:bench, :tools:chunk-compiler:compileChunks 와 같은 관례).
tasks.named<JavaExec>("run") {
    jvmArgs("--enable-native-access=ALL-UNNAMED") // troubleshooting.md T-004
    systemProperty(
        "wildbond.tables",
        rootProject.layout.projectDirectory.dir("data/tables").asFile.absolutePath,
    )
    systemProperty(
        "wildbond.chunks",
        rootProject.layout.projectDirectory.dir("assets/maps/chunks").asFile.absolutePath,
    )
    systemProperty(
        "wildbond.tileset",
        rootProject.layout.projectDirectory.file("assets/tilesets/placeholder.png").asFile.absolutePath,
    )
}
