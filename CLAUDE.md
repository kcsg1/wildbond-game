# Wildbond

2D 탑다운 사냥 RPG (바람의나라류, D-19). 싱글플레이·개인용. 전 계층 Java 25 (LTS) + LibGDX. **대상 플랫폼은 Windows 10/11 64-bit 단일**(§5.6).

## 환경 (고정)

- 프로젝트 경로: `C:\develop\develop\wildbond-game\wildbond-game` — **이것이 정본이다.**
  GitHub `kcsg1/wildbond-game` (remote `origin`, branch `main`)의 작업본이다.
- Gradle 홈: `C:\develop\develop\.gradle-home` (환경 변수 `GRADLE_USER_HOME`)
- JDK: `C:\develop\develop\jdk\jdk-25.0.4.1+1` (환경 변수 `JAVA_HOME`) —
  Temurin 25 LTS 하나 (D-15). Gradle 9.2.0 (래퍼 고정). `git` 은 `C:\Program Files\Git\cmd\git.exe`
  (PATH 에 없으므로 전체 경로로 부른다).
- **OneDrive 동기화 폴더 안에 두지 않는다.** 바탕화면이 OneDrive 로 리디렉션돼 있어서 거기 두면 Gradle 이
  `build/` 를 지우지 못해 빌드가 깨진다(`Unable to delete directory ... a process has files open`, T-011).
  세 경로 모두 **백신 예외 처리된 트리**여야 하고, `%USERPROFILE%\.gradle` 로 되돌리지 않는다 (T-002).
- 이 PC 의 백신이 SSL/TLS 스캐닝을 하므로, JDK 를 새로 깔면 그 루트 CA 를 JDK 의 `cacerts` 에 넣어야
  Gradle 이 배포판·의존성을 받을 수 있다 (T-008).

## 작업 절차 — 모든 작업은 이 순서를 따른다

1. **시작 전에 읽는다**: `CLAUDE.md`(이 파일) → `troubleshooting.md` → `plan.md`의 최근 항목 → 관련 `docs/architecture.md` §.
   - `troubleshooting.md`에 같은 종류의 작업에서 실패한 기록이 있으면 **그 해결책을 먼저 적용**한다. 같은 실수를 반복하지 않는다.
2. **작업 중**: 시도했다가 실패한 것(빌드 실패, 테스트 실패, 잘못된 접근, 되돌린 변경)은 즉시 `troubleshooting.md`에 기록한다. 해결 후 "해결" 항목을 채운다.
3. **작업 후**: 하네스(전체 모드)를 통과시킨 뒤 `plan.md`에 히스토리를 남긴다 — 무엇을 왜 했는지, 하네스 결과, 남은 일. 기록 없이 끝내지 않는다.
4. `plan.md`·`troubleshooting.md` 갱신은 코드 변경과 **같은 작업 단위**로 함께 끝낸다. 나중에 하지 않는다.

### plan.md 형식

```
## YYYY-MM-DD 단계 N — 제목        (docs/m0-prompts.md 의 단계 번호)
- 목표:
- 한 일:  (파일/모듈 단위로 간단히)
- 검증:   (하네스 결과 — reports\harness\latest.md 요약: 모드, 총/실패/스킵, 벤치 수치, 소요 시간)
- 결정:   (문서 기본안과 다르게 정한 것이 있으면 D-nn 또는 §n 과 이유)
- 남은 일 / 다음 단계:
```

### troubleshooting.md 형식

```
## [T-nnn] 한 줄 증상                    (번호는 증가만, 재사용 금지)
- 날짜 / 단계:
- 상황:   (무엇을 하려다)
- 증상:   (오류 메시지·테스트 이름을 그대로)
- 원인:
- 해결:   (적용한 변경)
- 재발 방지: (다음에 같은 작업을 할 때 먼저 할 것 — 이 줄이 가장 중요)
- 태그:   gradle | archunit | sim | libgdx | data | tooling | ...
```

- 원인을 아직 모르면 "원인: 조사 중"으로라도 남긴다. 해결되면 갱신한다.
- 같은 증상이 다시 나면 새 항목 대신 기존 항목에 "재발: 날짜"를 추가하고 재발 방지를 강화한다.

## 설계의 단일 원천

`docs/architecture.md`가 설계의 단일 원천이다.
- 코드와 문서가 충돌하면 **문서를 따른다.**
- 설계를 바꿔야 하면 코드보다 **문서를 먼저 고치고** 그 변경을 `plan.md` "결정"에 남긴다.
- 작업 지시가 문서와 다르면 진행 전에 지적하고 §번호를 들어 확인을 받는다.
- 결정 항목(D-nn)은 §13 표의 기본안을 따른다.

## 모듈과 의존 방향 (§5.1)

```
client-desktop → client-core → sim → data
tools/chunk-compiler → data
tools/datagen        → (프로젝트 의존 없음 — data 의 소스를 생성하므로 순환 방지)
```

- `sim`은 **`data`만** 의존한다. `com.badlogic.*`(LibGDX) 참조 금지. ArchUnit이 강제한다.
- `client-core`는 sim의 **컴포넌트 클래스를 직접 참조하지 않는다.** `SimView`(읽기)와 `Command`(쓰기)만 사용한다.
- 새 모듈을 추가하려면 먼저 `docs/architecture.md` §5.1 표를 갱신한다.

## sim 규칙 (§4, §6) — 위반 시 멀티플레이 확장이 막힌다

1. **상태 변경은 `Command`로만.** UI·렌더·오디오는 sim을 읽기만 한다.
2. **결정성.** `float` + `StrictMath`, 각도는 정수 1/1024 회전. 엔티티 순회는 `EntityId` 오름차순. `HashMap` 순회 금지 → 순서 보장 `IntMap`. 난수는 `Rng` 스트림(combat/spawn/loot)만, `java.util.Random`·`Math.random()` 금지. 시스템 실행 순서는 §4.1 고정.
3. **렌더는 틱 스냅을 읽는다.** `ViewState`(prev/cur)만 읽고 라이브 컴포넌트를 참조하지 않는다.
4. **`EntityId`는 안정적 정수.** 세이브·이벤트·정렬 모두 이 id 기준.
5. **핫 루프 객체 할당 금지.** 컴포넌트 풀링, 프리미티브 컬렉션, 람다 캡처 주의.
6. **모든 게임 규칙은 sim에 JUnit 테스트를 동반한다.** 공식(§3.2)은 통계 테스트, 결정성은 리플레이 테스트(같은 시드+명령 로그 → 같은 `stateHash`).

## 코딩 규칙

- Java 25 LTS, Gradle 9.x (D-15). 도메인 타입은 `record` + `sealed interface` 우선. 불변 기본.
- 정적 게임 데이터는 `data/tables/*.csv`에서만 편집한다. 생성된 record(`build/generated`)는 손대지 않는다.
- 패키지: `com.wildbond.<module>`. 포맷은 spotless(google-java-format)이 결정한다 — 스타일 논쟁 없음.
- 좌표: 월드 픽셀은 `float x, y`, 타일은 `int tx, ty` (32px). 변수명으로 구분한다.
- 로그는 slf4j. sim 안에서는 로깅하지 않는다(결정성·성능). 이벤트로 내보낸다.
- 외부 라이브러리 추가는 `gradle/libs.versions.toml`에 버전 고정 후 사용. 버전 해석 실패(`Could not find …`)는 최신 패치로 올리고 `plan.md`에 기록한다. sim에는 Artemis-odb·FlatBuffers 외 추가 금지.
- **Windows x64 전용** (§5.6): 네이티브는 windows-x64만 포함. 경로는 `Path` API, 구분자 하드코딩 금지. 세이브·로그는 `%LOCALAPPDATA%\Wildbond\`. 에셋 파일명은 소문자+언더스코어(NTFS 대소문자 비구분). macOS·Linux 대응 코드나 빌드 타깃을 만들지 않는다.

## 테스트 하네스 — 이것이 통과해야 "완료"다

모든 검증은 **하네스**를 통해서만 한다 (PowerShell 스크립트, 추가 설치 불필요). `gradlew test`를 직접 돌려 "통과했다"고 보고하지 않는다.

```
.\harness.cmd                  # 전체: spotlessApply → build(컴파일+전체 테스트+ArchUnit) → sim:bench → 리포트
.\harness.cmd -Quick           # 빠른: 포맷 → 컴파일 → 테스트 (벤치 생략)
.\harness.cmd -Module sim      # 한 모듈만
.\harness.cmd -CheckFormat     # 포맷을 고치지 않고 검사만 (거의 쓰지 않는다)
```

**포맷은 하네스가 알아서 고친다.** 기본 동작이 `spotlessApply` 이므로 포맷 차이로 검증이 멈추지 않는다.

`harness.cmd`는 `harness.ps1`을 실행 정책 우회로 호출하는 래퍼다. 직접 부를 때는
`powershell -NoProfile -ExecutionPolicy Bypass -File .\harness.ps1`.

- 리포트는 `reports\harness\latest.md`에 남는다(타임스탬프 사본 포함). `reports/`는 산출물이며 소스가 아니다.
- **언제 돌리는가**
  - 작업 중: Stop 훅이 자동으로 돌린다. 중간에 직접 확인하고 싶으면 `-Quick`
  - 작업 끝: 반드시 **전체 모드** 1회. 그 결과 요약(총/실패/시간, 벤치 수치)을 `plan.md` "검증" 줄에 옮겨 적는다
  - 단계 종료 시: 전체 모드 통과가 다음 단계의 전제
- 하네스가 실패하면: `troubleshooting.md`에 기록 → 원인 수정 → 다시 실행. 통과할 때까지 "완료"라고 하지 않는다.
- 포맷은 하네스가 `spotlessApply` 로 고친다 — 손으로 맞추려 하지 않는다. 그 외 실패는 원인을 고친다.
- **자동 실행**: `.claude/settings.json`의 Stop 훅이 턴을 끝낼 때 `-Quick`을 실행한다(포맷은 자동 적용, 컴파일·테스트 실패만 붙잡는다). 따라서 작업 중 하네스를 손으로 부를 필요는 없고, **단계 종료 시 전체 모드 1회만** 직접 실행한다. 실패하면 멈추지 못하고 리포트를 받는다 — 무시하고 우회하지 않는다. `HARNESS_SKIP=1`은 사람이 명시적으로 지시했을 때만 쓰고 `plan.md`에 사유를 적는다.
- 하네스 종료 코드 2(`gradlew.bat` 없음)는 단계 1 이전에만 정상이다. 단계 1은 하네스가 실제로 돌게 만드는 것까지 포함한다.
- 새 검증 항목(벤치 임계값, 리플레이 테스트 등)이 생기면 프롬프트에만 두지 말고 **하네스에 편입**한다. 하네스 밖의 검증은 잊힌다.
- 테스트를 지우거나 `@Disabled`로 통과시키지 않는다. 임계값을 완화해 통과시키지 않는다 — 완화가 필요하면 `docs/architecture.md`의 예산부터 고치고 사유를 `plan.md`에 남긴다.
- 클라이언트 실행: `.\gradlew.bat :client-desktop:run`, F3으로 디버그 오버레이. 수동 확인(수용 기준)은 하네스를 대체하지 않고 보완한다.

## 하지 말 것

- 멀티플레이·네트워크 코드를 지금 추가하지 않는다 (§6 확장 경로만 지킨다).
- sim 밖에서 게임 규칙(데미지·전리품·경험치)을 계산하지 않는다.
- 문서에 없는 시스템을 "편의상" 만들지 않는다. 필요하면 문서 제안부터.
- `plan.md`·`troubleshooting.md` 기록을 건너뛰지 않는다. 실패를 기록하지 않고 조용히 되돌리지 않는다.
- `troubleshooting.md`를 읽지 않고 작업을 시작하지 않는다.
- 하네스를 돌리지 않고 "테스트 통과"라고 보고하지 않는다. 하네스 실패 상태에서 단계를 끝내지 않는다.
- **버전 관리(git)는 2026-09-06 도입됐다** (GitHub `kcsg1/wildbond-game`). 커밋은 해도 되지만
  **`push`·`force`·`reset --hard` 같은 이력·원격을 건드리는 조작은 사람이 지시할 때만** 한다.
  빌드 산출물(`build/`, `.gradle/`)은 `.gitignore` 가 막는다 — 다시 추적되게 만들지 않는다. CI는 아직 없다.
- bash·WSL·Git Bash를 전제로 하는 스크립트를 만들지 않는다. 도구 스크립트는 PowerShell 또는 Gradle 태스크로 작성한다.
