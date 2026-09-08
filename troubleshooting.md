# troubleshooting.md — 실패 기록과 재발 방지

**작업을 시작하기 전에 이 파일을 읽는다.** 같은 종류의 작업에 해당하는 항목이 있으면 "재발 방지"를 먼저 적용한다.
형식은 `CLAUDE.md` "작업 절차" 참고. 번호 `T-nnn`은 증가만 하고 재사용하지 않는다. 가장 최근 항목이 **위**에 온다.

## 태그 색인

| 태그 | 항목 |
|---|---|
| gradle | T-001, T-002, T-004, T-005, T-008, T-011 |
| archunit | — |
| sim | T-014 |
| libgdx | T-005, T-006, T-007, T-009, T-010, T-012, T-013 |
| data | T-004 |
| tooling | T-001, T-002, T-003, T-004, T-005, T-006, T-007, T-008, T-011 |

---

## [T-014] 도망치는 몬스터를 잡는 sim 테스트가 "한 방향으로만" 쫓아가면 두 번째 마리부터 못 잡는다
- 날짜 / 단계: 2026-09-09 / D-19 장르 전환 (HuntLoopTest)
- 상황: 사슴 3마리를 차례로 잡아 레벨업을 검증하는 통합 테스트. 사슴은 맞으면 도망치므로(§9.1) 폭 1 복도에 가둬 두고
  플레이어가 동쪽으로 걸으며 동쪽을 향해 베게 했다.
- 증상: `HuntLoopTest.enoughDeerKillsLevelThePlayerUp` — `AssertionError: 400틱 안에 사슴을 잡지 못했다`.
  첫 마리는 잡히고 두 번째부터 실패.
- 원인: 첫 마리를 쫓느라 플레이어가 복도 동쪽 끝에 가 있는데, 두 번째 사슴은 복도 가운데(플레이어 서쪽)에 스폰된다.
  플레이어는 동쪽 벽에 대고 동쪽으로 휘두르니 영원히 닿지 않는다. 사슴은 플레이어 반대편으로 도망치므로 서쪽 벽에 붙는다.
- 해결: 매 틱 사슴이 플레이어의 어느 쪽에 있는지 보고 이동 방향과 조준각(`AIM_EAST`/`AIM_WEST`)을 고른다.
- 재발 방지: **AI 가 움직이는 대상을 상대로 하는 sim 테스트는 명령을 고정하지 말고 매 틱 SimView 로 상대 위치를 읽어
  만든다.** 복도·좁은 방으로 가두는 트릭을 쓸 때는 두 번째 스폰이 플레이어 뒤에 놓일 수 있다는 것을 먼저 그려 본다.
- 태그: sim

---

## [T-013] SpriteCache 로 그린 반투명 타일의 투명 부분이 검게 칠해진다
- 날짜 / 단계: 2026-09-08 / 아트 교체 (물가 경계 타일 추가)
- 상황: 호수 가장자리가 직각으로 끊기지 않게, 물 타일 위에 반투명한 "물가 띠" 타일을 덧그리게 했다.
- 증상: 연못 둘레 한 겹이 통째로 **검은 사각형**이 됐다. 안쪽(이웃이 전부 물이라 띠를 안 그린 타일)만 정상.
- 원인: `SpriteCache` 는 `SpriteBatch` 와 달리 **알파 블렌딩을 켜 주지 않는다.** `SpriteBatch.begin()` 은
  블렌딩을 켜지만 `SpriteCache.begin()` 은 GL 상태를 그대로 둔다. 그래서 알파 0 인 픽셀이 그대로
  RGB(0,0,0) 으로 칠해졌다. 그때까지 청크에 그린 타일은 전부 불투명이라 드러나지 않았다.
- 해결: `ChunkRenderer.render()` 에서 `spriteCache.begin()` 앞에 `glEnable(GL_BLEND)` 와
  `glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)` 를 건다.
- 재발 방지: **SpriteCache 에 알파가 있는 그림을 넣을 때는 블렌딩을 직접 켠다.** 청크 detail 레이어나
  지형 위 데칼(그림자·길·눈)도 같은 함정에 빠진다. "검은 사각형"이 보이면 텍스처가 아니라 GL 상태를 먼저 의심한다.
- 태그: libgdx

---

## [T-012] yDown 카메라에서는 스프라이트도 뒤집힌다 (단색 블록일 때는 안 보이던 문제)
- 날짜 / 단계: 2026-09-06 / 단계 7 이후 (스프라이트 입히기)
- 상황: 플레이어를 파란 블록 대신 사람 모양 스프라이트로 바꿨다.
- 증상: (그리기 전에 예측해 막았다) `SpriteBatch.draw(texture, x, y, w, h)` 는 (x,y)에 이미지의 **아래쪽**을
  놓는다. yDown 프로젝션에서는 y+h 가 화면 **아래**라서, 그대로 그리면 머리가 아래로 간다.
- 원인: T-006(BitmapFont)과 정확히 같은 원인이다. 단계 5~7 의 엔티티가 전부 단색 사각형이라 뒤집혀도
  똑같이 보였기 때문에 지금까지 드러나지 않았을 뿐이다.
- 해결: `PlaceholderSprites` 가 돌려주는 모든 리전을 `TextureRegion.flip(false, true)` 로 한 번 뒤집어 둔다.
  좌우 반전이 필요한 방향(왼쪽)은 그 리전을 복사해 `flip(true, false)` 를 추가로 건다.
- 재발 방지: **yDown 카메라로 그리는 이미지 리소스는 만드는 지점에서 한 번 세로로 뒤집는다.**
  타일셋·아틀라스를 실제 그림 파일로 교체할 때(M1) 같은 처리가 필요하다 — 단색 플레이스홀더로 테스트하면
  이 버그가 보이지 않으므로, 좌우/상하가 구분되는 그림으로 한 번은 눈으로 확인한다.
- 태그: libgdx

---

## [T-011] OneDrive 폴더에 두면 Gradle 이 build 를 지우지 못해 빌드가 깨진다
- 날짜 / 단계: 2026-09-06 / 단계 7 이후 (경로 정리)
- 상황: 프로젝트를 바탕화면으로 옮기려 했는데, 이 PC 의 바탕화면은 OneDrive 로 리디렉션돼 있었다
  (`C:\Users\kcsgo\OneDrive\바탕 화면`). 그 안에서 하네스를 돌렸다.
- 증상:
  ```
  Execution failed for task ':sim:spotlessJava'.
  > Unable to delete directory '...\sim\build\spotless-clean\spotlessJava'
    Failed to delete some children. This might happen because a process has files open ...
  Execution failed for task ':tools:datagen:compileJava'.
  > Unable to delete directory '...\tools\datagen\build\generated\sources\annotationProcessor\java\main'
  ```
  전체 모드가 spotless·build·bench 세 스테이지 모두 실패했다.
- 원인: OneDrive 동기화 클라이언트가 `build/` 안의 파일을 열어 두고 있어서 Gradle 이 지우지 못한다.
  빌드는 매 실행마다 그 폴더를 통째로 지웠다 다시 만드는데, 동기화가 계속 따라붙어 경합한다.
- 해결: 프로젝트를 OneDrive 밖(`C:\develop\develop\wildbond-game\wildbond-game`)에 두고,
  Gradle 홈·JDK 도 같은 트리(`C:\develop\develop\`)에 뒀다.
- 재발 방지: **프로젝트·Gradle 홈·JDK 를 OneDrive(또는 다른 동기화 폴더) 안에 두지 않는다.**
  "바탕화면"은 이 PC 에서 OneDrive 로 리디렉션돼 있으므로 `C:\Users\<user>\Desktop\...` 이라는 경로만 보고
  안전하다고 판단하지 않는다 — `HKCU:\...\User Shell Folders` 의 `Desktop` 값을 확인한다.
- 태그: tooling, gradle

---

## [T-010] 플레이어가 죽으면 화면이 검게 변한다 (카메라가 원점으로 떨어짐)
- 날짜 / 단계: 2026-09-06 / 단계 7
- 상황: 야생 팰이 생긴 뒤 `:client-desktop:run` 을 띄워 두고 스크린샷으로 관찰
- 증상: 1분쯤 지나자 화면 대부분이 검게 변하고 오른쪽 아래 구석에만 풀밭이 보였다. FPS·틱은 정상이었다.
- 원인: 야생 팰이 플레이어(HP 100)를 죽였고, `Dead` 5초 유예 뒤 엔티티가 월드에서 제거됐다.
  `PlayScreen` 이 카메라 위치를 `viewState.curX(playerId, 0f)` 로 읽고 있어서 폴백 0 이 그대로 쓰였고,
  카메라가 월드 원점으로 이동했다. 그 근처에는 로드된 청크가 없어 검은 화면이 된 것이다.
- 해결: `PlayScreen` 이 마지막으로 본 플레이어 위치를 들고 있다가 폴백으로 쓰게 했다.
- 재발 방지: **`ViewState` 조회의 폴백에 0 을 넣지 않는다.** 엔티티는 언제든 사라질 수 있고(사망·청크 회수),
  0 은 "월드 원점"이라는 뜻이 있는 값이라 사라진 것을 화면 밖으로 튀는 버그로 만든다. 사망·부활 처리 자체는
  M0 범위 밖(§12)이므로 여기서는 카메라만 그 자리에 붙잡아 두었다.
- 태그: libgdx

---

## [T-009] HUD 에 한글을 쓰면 □ 로 나온다
- 날짜 / 단계: 2026-09-06 / 단계 7
- 상황: 파티 슬롯 HUD 에 빈 칸 표시를 "- 비어 있음" 으로 적었다.
- 증상: 실제 창을 캡처해 보니 `1. - □□ □□` 처럼 한글이 전부 두부(豆腐) 글리프로 나왔다. 숫자·ASCII 는 정상.
- 원인: LibGDX 기본 내장 `BitmapFont` 는 ASCII 글리프만 담고 있다. 단계 5 에서 "글꼴은 LibGDX 기본 내장 폰트"
  로 정했으므로(커스텀 폰트 에셋은 M0 밖) 한글은 애초에 그릴 수 없다.
- 해결: HUD 문자열을 ASCII 로 바꿨다(`- empty`). 종 이름은 `pal.mossling` → `mossling` 이라 원래 ASCII 다.
- 재발 방지: **화면에 그리는 문자열은 전부 ASCII 로 쓴다.** 한글은 주석·문서·로그에만 쓴다. 한글 UI 가
  필요해지면 폰트 에셋(FreeType 또는 한글 글리프를 구운 .fnt)을 먼저 추가하는 일감으로 잡는다.
- 태그: libgdx

---

## [T-008] 새 작업 트리에 JDK 가 없고, 백신 SSL 스캐닝 때문에 Gradle 이 배포판을 못 받았다
- 날짜 / 단계: 2026-09-06 / 단계 7
- 상황: `C:\develop\develop\wildbond-game\wildbond-game` 트리에서 `.\gradlew.bat` 실행
- 증상:
  1. `ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.`
     — `C:\Program Files` 를 포함해 디스크 어디에도 JDK 가 없었다.
  2. JDK 를 깔고 나니 래퍼가 배포판을 받다가
     ```
     Exception in thread "main" javax.net.ssl.SSLHandshakeException: (certificate_unknown)
     PKIX path building failed: ... unable to find valid certification path to requested target
     ```
     같은 URL 을 `curl.exe` 로 받으면 정상이었다.
- 원인:
  1. T-001 과 달리 네트워크는 열려 있었고 단순히 JDK 미설치였다(이 트리는 개발 PC 의 원래 경로가 아니다).
  2. 이 PC 의 백신이 SSL/TLS 스캐닝을 한다 — 인증서 체인을 확인해 보니 `services.gradle.org`·
     `repo.maven.apache.org` 모두 발급자가 `CN=Norton Web/Mail Shield Root ... generated by Norton
     Antivirus for SSL/TLS scanning` 이었다. Windows 인증서 저장소에는 이 루트가 들어 있어 `curl.exe` 는
     통과하지만, JDK 는 자기 `cacerts` 만 보므로 실패한다. (T-002 의 배포판 격리와 같은 백신이 원인이다.)
- 해결:
  1. Adoptium API 로 Temurin 25 zip 을 받아 `C:\develop\develop\jdk\jdk-25.0.4.1+1` 에 풀고
     사용자 환경 변수 `JAVA_HOME` 과 `GRADLE_USER_HOME`(=`C:\develop\develop\.gradle-home`)을 설정했다.
     관리자 권한이 필요 없고 되돌리기도 폴더 삭제뿐이다.
  2. 백신의 루트 인증서를 그 JDK 의 truststore 에 넣었다:
     ```
     keytool -importcert -trustcacerts -alias norton-ssl-scan -file <root>.cer \
             -keystore "<JDK>\lib\security\cacerts" -storepass changeit
     ```
     이후 배포판 다운로드·의존성 해석·빌드 전부 정상.
- 재발 방지: **JDK 를 새로 깔면 곧바로 백신 루트 CA 를 그 JDK 의 `cacerts` 에 넣는다.** 증상이
  `PKIX path building failed` 인데 `curl.exe` 로는 같은 URL 이 받아지면 원인은 100% 이것이다 —
  프록시 설정이나 `-Dtrust_all` 류를 찾아다니지 않는다. 체인 확인은
  `New-Object Net.Sockets.TcpClient` + `SslStream` + `X509Chain` 으로 발급자를 직접 찍어 보면 된다.
  JDK 는 zip 으로 받아 프로젝트 옆에 풀면 관리자 권한 없이도 된다.
- 태그: tooling, gradle

---

## [T-007] 이 PC 에서 GUI 앱을 화면 캡처로 검증하려면 PrintWindow 를 써야 한다 (세션이 잠겨 있을 수 있다)
- 날짜 / 단계: 2026-09-04 / 단계 5
- 상황: `:client-desktop:run` 으로 띄운 LibGDX 창이 실제로 제대로 그려지는지 화면 캡처로 확인하려 했다.
- 증상:
  - `Graphics.CopyFromScreen(...)` (일반적인 "화면 전체를 찍는" 방식) 으로 캡처하면 게임 창이 아니라
    **Windows 잠금 화면**이 찍혔다 — 이 세션의 대화형 데스크톱이 잠겨 있었다(원격/무인 상태로 추정).
  - `System.Windows.Forms.SendKeys::SendWait` 로 WASD 를 흉내 내려 하면 `Access is denied` 예외 —
    잠긴 세션에는 입력 주입도 막혀 있다.
- 원인: 세션이 잠기면 물리 프레임버퍼에는 잠금 화면만 표시되고(CopyFromScreen 이 그걸 그대로 찍는다),
  OS 가 잠긴 세션으로의 입력 주입도 차단한다. 반면 DWM 은 잠긴 상태에서도 각 창의 백버퍼는 계속
  합성하고 있어서, 특정 창을 **직접** 겨냥해 픽셀을 요청하는 방식은 여전히 통한다.
- 해결: `user32.dll` 의 `PrintWindow(hwnd, hdc, PW_RENDERFULLCONTENT=2)` 로 그 창의 핸들을 직접 지정해
  캡처했다 — OpenGL/LWJGL 창인데도 정상적으로 실제 렌더링 내용이 나왔다(§부록: `GetClientRect` 로 크기를
  구해 그 크기의 비트맵을 만들고, `Graphics.GetHdc()`/`ReleaseHdc()` 로 얻은 HDC 를 넘긴다).
- **재발(같은 날, 화면 잠금이 풀린 뒤)**: 사람이 실제로 PC 앞에 앉아 화면 잠금이 풀린 뒤 다시 키 입력
  자동화를 시도했다 — `SendKeys::SendWait`(반복 탭), `keybd_event`(D 키 2초 유지), `SetForegroundWindow`
  단독, `AttachThreadInput` 으로 감싼 `SetForegroundWindow` 까지 전부 시도. 매번 `GetForegroundWindow()`
  로 확인해 보면 실제 포그라운드는 계속 **다른 창**(도구가 실행 중인 터미널 쪽으로 추정)이었다 — 즉
  Wildbond 창은 한 번도 실제로 키 입력을 받지 못했다. `keybd_event` 를 길게 유지하는 동안 Wildbond 창이
  까닭 모르게 최소화되기도 했다(포커스 도난 방지의 부작용으로 추정). 잠금 여부와 무관하게, **원격
  자동화 세션은 Windows 의 포그라운드 잠금(포커스 하이재킹 방지) 정책 때문에 사용자가 실제로 쓰고 있는
  세션에서 다른 창으로 키보드 포커스를 강제로 옮길 수 없다** — 이건 우회 대상이 아니라 의도된 보안 기능.
- 재발 방지: **이 머신에서 GUI 앱을 스크린샷으로 검증할 때는 처음부터 `PrintWindow(hwnd, ..., 2)`를
  쓴다** — `CopyFromScreen` 을 먼저 시도해서 시간을 버리지 않는다. 창 핸들은
  `Get-Process | Where-Object { $_.MainWindowTitle -eq "..." }` 로 찾는다(제목이 뜰 때까지 폴링 필요 —
  프로세스는 바로 뜨지만 GLFW 창은 몇 초 뒤에 생긴다). **키 입력(WASD 등) 상호작용 검증은 이 환경에서는
  화면 잠금 여부와 무관하게 시도하지 않는다** — `SendKeys`/`keybd_event`/`SetForegroundWindow`/
  `AttachThreadInput` 모두 확인해 봤지만 안 되고, 실패 자체가 창이 최소화되는 등 예측 못 할 부작용을
  낳을 수 있다. 대신 코드 리뷰·유닛 테스트·(가능하면) 코드 안에 임시 진단 출력을 넣어 콘솔로 값을
  확인하는 방식으로 대체한다. 사람이 실제로 조작해 봐야 하는 부분은 "확인 못 함"이라고 솔직히 보고하고,
  30초짜리 최종 확인을 사람에게 요청한다.
- 태그: tooling, libgdx

---

## [T-006] yDown 카메라에 기본 BitmapFont 를 쓰면 글자가 뒤집혀 나온다
- 날짜 / 단계: 2026-09-04 / 단계 5
- 상황: DebugOverlay(F3) 텍스트를 그렸는데, `PlayScreen` 의 UI 카메라를 `setToOrtho(true, ...)`
  (yDown — 타일 그리드와 좌표계를 맞추려고, GameCamera.java 참고) 로 만들어 놨다.
- 증상: 실제 창을 캡처해 보니(T-007 참고) 글자가 좌우·상하로 뒤집혀 알아볼 수 없게 나왔다. FPS·틱 수치
  자체는 맞았다(게임 로직은 정상) — 순전히 폰트 렌더링 방향 문제였다.
- 원인: `new BitmapFont()`(기본 생성자)는 y 가 위로 증가하는 좌표계를 가정한다. yDown 프로젝션 행렬로
  그리면 그 가정이 뒤집혀 글리프가 위아래로 뒤집힌 채 그려진다.
- 해결: `new BitmapFont(true)` — LibGDX 가 정확히 이 상황을 위해 제공하는 `flip` 생성자 인자를 썼다.
  (`DebugOverlay.java`)
- 재발 방지: **yDown 카메라/프로젝션으로 텍스트를 그릴 땐 `BitmapFont` 를 항상 `flip=true` 로 만든다.**
  일반 스프라이트(타일·엔티티)는 이 문제가 없다 — SpriteBatch.draw 의 사각형 좌표 자체가 카메라 방향을
  그대로 따라가기 때문에 별도 처리가 필요 없다. 폰트만 내부적으로 방향을 가정하고 있어서 예외다.
- 태그: libgdx

---

## [T-005] lwjgl3 백엔드도 gdx-platform(gdx64.dll)이 없으면 창이 안 뜬다
- 날짜 / 단계: 2026-09-04 / 단계 5
- 상황: `.\gradlew.bat :client-desktop:run` 첫 실행. `gdx-backend-lwjgl3` 의 POM 이 org.lwjgl 계열
  네이티브만 요구하는 것으로 보여서(§5.1 조사), `gdx-platform`(구식 gdx.dll/gdx64.dll)은 lwjgl3 백엔드엔
  필요 없다고 판단하고 뺐다.
- 증상:
  ```
  Exception in thread "main" com.badlogic.gdx.utils.SharedLibraryLoadRuntimeException:
  Couldn't load shared library 'gdx64.dll' for target: Windows 11, x86, 64-bit
    at ...Lwjgl3Application.initializeGlfw(Lwjgl3Application.java:83)
  Caused by: ... Unable to read file for extraction: gdx64.dll
  ```
- 원인: `Lwjgl3Application.initializeGlfw()` 가 GLFW 초기화 전에 `GdxNativesLoader.load()` 를 호출하는데,
  이건 `gdx-backend-lwjgl3` 의 POM 이 아니라 `gdx-platform` 아티팩트가 제공하는 `gdx64.dll` 을 classpath
  리소스로 찾는다. POM 만 보고 런타임 로더 경로까지 판단한 것이 오판이었다.
- 해결: `gdx-platform:1.13.5:natives-desktop` 을 추가했다. windows 전용 classifier 는 없다 — 이 아티팩트는
  win(gdx.dll/gdx64.dll)·mac(.dylib)·linux(여러 arch .so)를 한 jar(~1.1MB)에 묶어서만 배포한다.
  §5.6(윈도우 x64 네이티브만) 원칙에 대한 문서화된 예외로 처리했다 — 더 잘게 쪼갠 아티팩트가 upstream에
  없고, 크기 영향이 무시할 만하다(zstd-jni/LWJGL3 는 실제로 수십MB 차이가 나서 이 예외를 적용하지 않았다).
- 재발 방지: **libgdx 데스크톱 실행이 네이티브 로드 예외로 실패하면, 먼저 `gdx-platform:natives-desktop`
  이 클래스패스에 있는지 확인한다.** POM의 선언된 의존성만으로 "이 백엔드엔 이 아티팩트가 필요 없다"고
  판단하지 않는다 — 리플렉션/클래스패스 리소스로 찾는 네이티브 로더는 POM에 안 나타날 수 있다. 실제로
  `:client-desktop:run` 을 돌려서 창이 뜨는지까지 확인해야 "완료"다(컴파일 통과만으로는 부족).
- 태그: gradle, tooling, libgdx

---

## [T-004] JDK 25 가 zstd-jni 네이티브 로드에 "restricted method" 경고를 낸다
- 날짜 / 단계: 2026-09-04 / 단계 4
- 상황: `.\gradlew.bat :tools:chunk-compiler:compileChunks` 로 처음 zstd 압축 코드(`ChunkWriter`)를 실행
- 증상:
  ```
  WARNING: A restricted method in java.lang.System has been called
  WARNING: java.lang.System::loadLibrary has been called by com.github.luben.zstd.util.Native$1 ...
  WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
  WARNING: Restricted methods will be blocked in a future release unless native access is enabled
  ```
  `:data:test`/`:sim:test` 도 같은 코드 경로(zstd)를 타지만, `testLogging { events("failed") }` 때문에
  통과한 테스트의 표준출력/에러는 하네스 로그에 안 보여서 그쪽에서는 경고가 눈에 띄지 않았다.
- 원인: JDK 25 부터 JNI 네이티브 로드가 "제한된 메서드"로 분류된다(JEP 472). 경고일 뿐 지금은 실패하지
  않지만, 메시지가 "미래 릴리스에서 막힌다"고 명시한다 — JDK 패치 버전만 올라가도 빌드가 깨질 수 있다.
- 해결: 루트 `build.gradle.kts` 의 `tasks.withType<Test>` 공통 설정과
  `tools/chunk-compiler/build.gradle.kts` 의 `compileChunks` JavaExec 에
  `jvmArgs("--enable-native-access=ALL-UNNAMED")` 를 추가했다.
- 재발 방지: **네이티브 라이브러리(JNI/JNA)를 새로 추가하면, 그 코드를 실행하는 모든 Test/JavaExec
  태스크에 `--enable-native-access=ALL-UNNAMED` 를 바로 추가한다** — 경고가 하네스 로그에 안 보인다고
  없는 게 아니다. 단계 5 LWJGL3(client-desktop) 도 네이티브를 로드하므로 `:client-desktop:run` 에도
  같은 처리가 필요할 것이다.
- 태그: tooling, gradle, data

---

## [T-003] 새로 작성한 Java 때문에 하네스 전체 모드가 spotlessCheck 에서 실패
- 날짜 / 단계: 2026-09-04 / 단계 2
- 상황: 원격 세션에서 작성한 `tools/datagen` 8클래스를 넣고 PC 에서 `.\harness.cmd` 실행
- 증상: `spotlessCheck` 실패 → `build` 도 실패(`check` 가 spotlessCheck 를 포함).
  `Run 'gradlew.bat :tools:datagen:spotlessApply' to fix these violations.`
  컴파일·테스트는 정상인데 포맷 차이만으로 전체 검증이 멈췄다. 리포트의 테스트 수(7)는 직전 실행 값이었다.
- 원인: 손으로 쓴 코드는 google-java-format 출력과 정확히 같을 수 없다. 그런데 하네스 전체 모드가
  `spotlessCheck`(검사)를 쓰고 있어서, "포맷은 논쟁하지 않는다"는 CLAUDE.md 원칙과 어긋났다.
- 해결: 하네스 기본 동작을 **`spotlessApply`(적용)** 로 바꿨다. 검사만 하려면 `-CheckFormat`.
  실패 시 리포트에 "테스트 수는 직전 실행 결과일 수 있다"는 주의 문구도 추가했다.
- 재발 방지: 포맷을 손으로 맞추려 하지 않는다. 새 소스를 추가한 뒤에는 하네스를 그냥 돌리면 된다.
  원칙(포맷은 논쟁하지 않음)과 도구 동작(검사)이 어긋나면 **도구를 원칙에 맞춘다.**
- 태그: tooling, gradle

---

## [T-002] Gradle 래퍼: 배포판 다운로드는 되는데 압축 해제 시 zip 이 없다
- 날짜 / 단계: 2026-09-04 / 단계 1
- 상황: 개발 PC 에서 `.\gradlew.bat spotlessApply` 첫 실행 (Gradle 9.2.0 배포판 최초 다운로드)
- 증상:
  ```
  Downloading https://services.gradle.org/distributions/gradle-9.2.0-bin.zip
  ...100%
  Could not unzip ...\gradle-9.2.0-bin\<hash>\gradle-9.2.0-bin.zip to ...
  Exception in thread "main" java.nio.file.NoSuchFileException: ...gradle-9.2.0-bin.zip
  ```
  다운로드는 100% 완료되었으나 곧바로 그 zip 을 열 때 파일이 존재하지 않는다.
- 원인: `%USERPROFILE%\.gradle` 트리가 백신·보안 소프트웨어의 감시 대상이어서, 내려받은 배포판 zip 이
  다운로드 직후 격리되어 사라졌다. 바탕화면 트리는 예외 처리되어 있다.
- 해결: 프로젝트를 `C:\Users\user\Desktop\develop\wildbond-game` 으로 옮기고,
  Gradle 홈을 `C:\Users\user\Desktop\develop\.gradle-home` 으로 지정했다
  (환경 변수 `GRADLE_USER_HOME`, 그리고 `harness.ps1` 에 폴백 설정). 두 경로 모두 백신 예외 트리다.
  → 2026-09-04 새 경로에서 배포판 다운로드·압축 해제 성공, 하네스 통과로 해결 확인.
- 재발 방지: **빌드 산출물·의존성 캐시를 백신 예외 경로 밖에 두지 않는다.** `GRADLE_USER_HOME` 을
  `%USERPROFILE%\.gradle` 로 되돌리지 않는다(CLAUDE.md "환경" 참고).
  새 도구 배포판을 처음 받을 때 실패하면 먼저 **파일이 실제로 남아 있는지** 확인한다 —
  래퍼가 자동 다운로드에 실패하면 zip 을 직접 내려받아 `<GRADLE_USER_HOME>\wrapper\dists\...` 에 두면 그대로 쓴다.
- 태그: gradle, tooling

---

## [T-001] 클라우드(원격) 세션에서는 Gradle 빌드를 검증할 수 없다
- 날짜 / 단계: 2026-09-04 / 단계 1
- 상황: 원격 Claude 세션(클라우드 컨테이너)에서 Gradle 뼈대를 만들고 하네스로 검증하려 했다.
- 증상: `services.gradle.org`, `repo.maven.apache.org`, `plugins.gradle.org` 모두 CONNECT 403
  (조직 egress 정책). `gradle wrapper --gradle-version 9.1.0` 은
  "Test of distribution url ... failed" 로 실패. PowerShell 도 없어 `harness.cmd` 자체를 돌릴 수 없다.
- 원인: 클라우드 컨테이너의 네트워크 정책 + OS 차이(Linux). 개발 PC 환경이 아니다.
- 해결: 컨테이너에서는 (1) 네트워크가 필요 없는 검증만 한다 — `gradle projects --offline`,
  `gradle compileJava --offline -Pwildbond.javaVersion=21`, `javac` 구문 확인.
  (2) 래퍼는 컨테이너에 설치된 Gradle 로 버전 지정 없이 생성한 뒤 `distributionUrl` 만 손으로 고친다.
  (3) 실제 하네스 통과 여부는 개발 PC(Windows, JDK 25)에서 확인한다.
- 재발 방지: **원격 세션에서 작업할 때는 의존성 해석·하네스 실행을 스스로 마쳤다고 보고하지 않는다.**
  파일 작성 + 오프라인 검증까지만 하고, `plan.md` 검증 줄에 "PC 하네스 실행 대기"로 남긴 뒤 사람에게 실행을 요청한다.
  버전 핀은 추측일 수 있으므로 `libs.versions.toml` 상단 주석대로 해석 실패 시 최신으로 올린다.
- 태그: tooling, gradle

---

(아래는 템플릿)

```
## [T-nnn] 한 줄 증상
- 날짜 / 단계:
- 상황:
- 증상:
- 원인:
- 해결:
- 재발 방지:
- 태그:
```
