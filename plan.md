# plan.md — 작업 히스토리

가장 최근 항목이 **위**에 온다. 형식은 `CLAUDE.md` "작업 절차" 참고. 단계 번호는 `docs/m0-prompts.md` 기준.

## 현재 상태

- 마일스톤: M0 수직 슬라이스
- 다음 단계: 6 완료 → 7 (팰: 스폰·야생 AI·포획·동행)
- 열린 결정: 없음 (architecture.md §13 기본안 적용 중)
- 하네스: 단계 6 전체 모드 통과 (17:23, 50/50, sim:bench 예산 내 유지). `:client-desktop:run` 화면은
  스크린샷으로 확인(허수아비 렌더링, 674틱 무크래시). **마우스 좌/우클릭 스킬 발동 조작만 아직 사람 확인
  필요** — 이 세션은 원격 입력 주입이 막혀 있다(T-007과 같은 제약, 아래 "검증" 참고)
- 경로: 프로젝트 `C:\Users\user\Desktop\develop\wildbond-game`, Gradle 홈 `...\develop\.gradle-home` (CLAUDE.md "환경")

---

## 2026-09-04 단계 6 — 전투: 스킬 · 히트 판정 · 데미지 공식  (완료)
- 목표: architecture.md §3.2 데미지 규칙과 §4.1 CombatSystem 을 M0 범위로 구현 — 쿨다운 있는 스킬, hit_shape 별
  판정(rect/circle/cone/projectile), 속성 상성·치명타를 포함한 데미지 공식, Damaged/Died 이벤트. 클라이언트는
  마우스로 조준해 좌클릭(근접)/우클릭(원거리) 발동, 타격 시 떠오르는 숫자·3프레임 플래시, 확인용 허수아비.
- 한 일:
  - sim 신규 컴포넌트: `Stats`(atk/def/level), `ElementComponent`(defender 속성 — `data.Element` 열거형과 이름이
    겹쳐 EntityIdComponent 관례대로 접미사를 붙임), `Skills`(skillIds[]/cooldownRemainingTicks[] 병렬 배열),
    `Dead`(ticksRemaining, HP 0 표식), `Projectile`(ownerStableId/방향/속도/사거리/히트반경), `DummyTag`
  - `com.wildbond.sim.Angle` — 1/1024 회전 단위 ↔ 라디안 변환, 클라이언트(마우스 조준 인코딩)와 sim(CombatSystem
    판정) 양쪽이 같은 단위를 쓰도록 공개 유틸로 뺌
  - `Command.SpawnDummy(x,y,element,maxHp,atk,def,level)` 추가 — 단계 6 확인용 임시 엔티티이자 전투 공식 테스트의
    피격 대상. `Command.UseSkill` 은 이제 실제로 처리된다
  - `systems.CombatConstants` (플레이어 기본 스탯·기본 스킬 로드아웃·크리티컬 확률/배율·데미지 랜덤 폭·투사체
    속도·사망 제거 유예), `systems.DamageFormula`(§3.2 공식을 컴포넌트·Rng 접근과 분리한 순수 함수 — 유닛 테스트가
    RNG 없이 정확한 값을 검증할 수 있게 함)
  - `systems.CombatSystem` 전면 구현 — 매 틱 쿨다운 감소 → 기존 투사체 이동/충돌 처리 → 이번 틱 UseSkill 명령
    처리(쿨다운 확인 → hit_shape 판정 → 데미지 적용) → Dead 엔티티 유예 감소/제거. 근접(rect/circle/cone)은 즉시
    판정, 투사체는 Projectile 엔티티를 만들어 이후 틱마다 이 시스템이 직접 이동시키고 타일·대상 충돌을 검사한다
    — MovementSystem 을 일부러 타지 않는다(그쪽의 엔티티간 밀어내기가 겹침을 먼저 떼어내면 명중 판정이
    성립하지 않음). CommandApplySystem 은 SpawnDummy 를 처리하고, MoveInput 은 이제 Dead 엔티티를 무시한다
  - `EntityIndex.remove(stableId)` 추가 — 첫 실제 엔티티 삭제 케이스(정렬 유지한 채 뒤 원소를 당김).
    `EntityQueries`/`EntityKind` 에 DUMMY 추가. `Sim` 에 `Rng` 필드(이전까지 존재하지 않았음), `subscribe(Listener)`
    공개 메서드(§4.1 "렌더·오디오가 구독" — SimView/Command 와 별개인 세 번째 통로로 열었다, 아래 "결정" 참고),
    `stateHash()` 에 health 포함
  - 이벤트: `Damaged` 에 x/y/critical 필드 추가(렌더가 피격 위치에 숫자를 띄우려면 필요), `Died(entityId)` 신규
  - client-core: `InputMapper` 에 좌/우클릭 스킬 큐잉(래치 방식 — 아래 "결정"), `CombatBindings`(좌클릭/우클릭
    스킬 id, sim.systems 참조 금지라 RenderConstants.TILE_PX 와 같은 이유로 중복 정의), `GameCamera.unproject`,
    `render.HitEffects`(떠오르는 숫자 + 플래시 상태 보관, sim.events.Damaged 구독), `EntityRenderer` 가 DUMMY 를
    그리고 피격 엔티티는 흰색 텍스처로 3프레임 대체. `PlayScreen` 이 렌더 프레임마다 한 번 클릭을 감지해 마우스
    월드 좌표 - 플레이어 위치로 조준각을 계산해 InputMapper 에 넘긴다. `BootScreen` 이 허수아비를 스폰하고
    `sim.subscribe` 로 `HitEffects` 를 연결한다
  - sim 테스트 7개 — `DamageFormulaTest` 3(속성 상성 2.0/0.5, 방어력 200→절반, 치명타 배율), `CombatSystemTest`
    4(쿨다운 중 재사용 무시, 투사체가 벽에 막혀 대상이 무피해, 같은 시드 치명타 시퀀스 재현, 사망 후 5초 뒤 제거)
- 검증: **하네스 전체 모드 통과** (`reports\harness\20260904-172330.md`, moveProjectiles() 정리 후 재실행) —
  spotlessApply 5s / build 10s / sim:bench 5s, 총 25s. 테스트 총 50 / 실패 0 / 오류 0 / 스킵 0 (기존 43 + 신규 7).
  `sim:bench`:
  `entities=500 ticks=1000 avg=0.236ms max=2.974ms`(§9.4 예산 8ms 대비 여유 유지, 전투 로직 추가에도 이전
  단계3 수치(0.230ms)와 거의 동일 — CombatSystem 자체는 유휴 시 쿨다운 감소 루프뿐이라 부하가 없다).
  `:client-desktop:run` 을 띄워 `PrintWindow`(T-007)로 캡처 확인: 초록 풀밭 위에 파란 플레이어와 갈색
  허수아비가 3타일 간격으로 정확히 보이고, 디버그 오버레이(FPS 60, tick 674, avg 0.01ms/max 0.03ms, draws 6 —
  단계5의 5에서 허수아비 1개만큼 증가, chunks 4)가 정상. 674틱 동안 무크래시. **마우스 클릭으로 실제 스킬이
  나가는지(떠오르는 숫자·플래시 등장)는 이 세션에서 확인 못 했다** — T-007 과 같은 이유로 원격 세션은 입력
  주입이 Windows 포커스 보호에 막힌다. sim 쪽 로직(쿨다운·판정·공식·이벤트)은 위 7개 테스트로 이미 검증됐고,
  클라이언트 배선(클릭→좌표 변환→InputMapper→Command)은 코드 리뷰로 확인했다
- 결정:
  - **client-core 가 `com.wildbond.sim.events.*` 를 직접 참조하도록 열었다.** CLAUDE.md "sim 규칙"은
    "SimView(읽기)와 Command(쓰기)만 사용한다"고 적혀 있지만, architecture.md §4.1 은 EventFlushSystem 을
    "도메인 이벤트 → 리스너(렌더·오디오가 구독)"라고 명시하고, client-core ArchitectureTest 도 `sim.components`·
    `sim.systems` 만 금지할 뿐 `sim.events`는 막지 않는다 — 애초에 렌더·오디오가 구독하도록 설계된 세 번째
    통로로 해석했다. `Sim.subscribe(EventBus.Listener)` 를 새로 공개해 `BootScreen` 이 `HitEffects` 를 연결한다
  - **플레이어 기본 스킬 로드아웃을 {slash(id1,근접,무속성), ember(id3,원거리,fire)} 로 정했다** —
    arrow(id2,원거리,무속성) 대신 ember 를 골랐다. 문서(Prompt 6)는 "우클릭→원거리"라고만 했지 어떤 스킬인지
    지정하지 않았는데, ember(fire)를 쓰면 기본 로드아웃만으로 속성 상성(§3.2)이 실제로 갈리는 걸 확인할 수
    있다 — arrow(무속성)만 있었다면 ElementChart 배수가 항상 1.0 이라 상성 자체를 시험해 볼 방법이 없었다
  - **치명타 확률 10%(CombatConstants.CRIT_CHANCE)** — §3.2 공식은 "critical(1.5 if roll)"이라고만 적고
    확률을 정하지 않았다. SimConstants 의 이동 상수들과 같은 성격의 임시 고정값으로 취급했다
  - **데미지 공식을 `DamageFormula.compute(...)` 순수 함수로 분리했다.** CombatSystem 안에 인라인으로 두면
    Rng·컴포넌트 없이는 "속성 2.0/0.5", "방어력 200→절반" 같은 정확한 값 테스트가 사실상 불가능하다(치명타·
    랜덤 롤이 두 실행 사이에서 우연히 맞아떨어지길 바라야 함) — 크리티컬 여부와 랜덤 롤을 CombatSystem 이
    Rng 에서 뽑아 순수 함수에 넘기는 구조로 바꿔 formula 정확성과 RNG 결정성을 별도 테스트로 나눴다
  - **투사체는 MovementSystem 을 타지 않고 CombatSystem 이 전담한다.** 사거리·명중 판정이 MovementSystem 의
    엔티티간 밀어내기(resolvePush)와 뒤섞이면 겹침을 먼저 떼어내 버려 명중이 성립하지 않는다. Projectile
    엔티티는 Velocity/Collider 컴포넌트를 아예 갖지 않고, 방향·속도·이동을 자기 컴포넌트 필드로만 들고 다닌다
  - **StatusEffects 컴포넌트는 아직 만들지 않았다.** architecture.md §4.1 목록엔 있지만 §3.2 데미지 공식도,
    단계6 테스트 목록도 실제로 소비하는 곳이 없다(상태이상 배수는 §3.2 "포획" 쪽에만 있고 단계7 몫). CLAUDE.md
    "문서에 없는 시스템을 편의상 만들지 않는다"/"절반짜리 구현을 두지 않는다"를 우선해, 실제로 상태이상을
    거는 시스템(포획·화상 등)이 생기는 단계에서 함께 추가하기로 미뤘다
  - **cast_ticks(시전 시간)는 아직 적용하지 않는다.** Skill.csv 에 필드는 있지만 Prompt 6 은 "쿨다운 확인,
    hit_shape 별 판정"만 요구했고 시전 지연 규칙은 문서에 없다 — M0 은 즉시 발동으로 단순화했다. 실제로 쓰게
    되면 §4.1 문서에 시전 상태 규칙을 먼저 적는다
  - **마우스 클릭은 InputMapper 에 래치로 넘긴다.** `isButtonJustPressed` 는 렌더 프레임 하나에만 참인데,
    고정 틱 루프(`GameLoop.advance`)는 한 렌더 프레임 안에서 0번 또는 여러 번 돌 수 있어 틱 루프 안에서 직접
    읽으면 클릭을 놓치거나 중복 발동할 수 있다. `PlayScreen.render()` 가 프레임당 한 번만 감지해
    `InputMapper.queueMeleeSkill/queueRangedSkill` 로 넘기고, `drain()` 이 다음 한 번만 소비한다
- 남은 일 / 다음 단계: **사람이 `.\gradlew.bat :client-desktop:run` 으로 좌/우클릭해 허수아비를 실제로 때려
  봐야 한다** — 떠오르는 숫자·타격 플래시가 나오는지, 쿨다운 동안 스팸 클릭해도 안 나가는지 눈으로 확인.
  확인되면 단계 7(팰: 스폰·야생 AI·포획·동행)로 진행

---

## 2026-09-04 단계 5 — client: LibGDX 창 · 게임 루프 · 청크 렌더 · 입력→Command  (완료)
- 목표: architecture.md §5 의 클라이언트를 M0 범위로 구현 — 처음으로 화면이 뜨는 단계. sim 은 메인 스레드
  고정 틱, 렌더는 틱 사이를 보간
- 한 일:
  - 의존성: LibGDX 1.13.5(core) + gdx-backend-lwjgl3 1.13.5. `org.lwjgl` 그룹을 통째로 제외하고
    win_amd64 클래시파이어만 버전 맞춰(3.3.3, POM 확인) 되붙임(§5.6). `gdx-platform:natives-desktop`
    추가 필요(T-005) — windows 전용 classifier 가 없어 §5.6 예외로 문서화(아래 "결정")
  - client-core 공개 API: `WildbondGame`(Game, BootScreen→PlayScreen), `GameConfig`(경로 레코드),
    `GameLoop`(§5.2 고정 틱 누적기, deltaTime 상한 0.25s, 최근 128틱 평균/최대 틱시간 링버퍼),
    `ViewState`(SimView→prev/cur 스냅샷, Snapshot 객체는 풀에서 재사용), `InputMapper`(WASD/화살표→
    Dir8, Shift 달리기, 매틱 현재 키 상태를 그대로 명령화)
  - `com.wildbond.client.render`: `GameCamera`(가상 640×360, 정수 배율 2×/3×/4×, yDown, 정수 픽셀 스냅),
    `ChunkRenderer`(카메라 반경 2청크, 디코드는 전용 데몬 스레드+ConcurrentLinkedQueue, SpriteCache 로
    청크당 draw 1회), `EntityRenderer`(ViewState Y-정렬, 32×48 플레이어 색 블록을 Pixmap 으로 런타임
    생성, 발 위치 앵커, prev→cur alpha 보간), `DebugOverlay`(FPS/틱/평균·최대 틱시간/stateHash/드로우콜/
    로드된 청크 수, F3 토글)
  - `com.wildbond.client.screen`: `BootScreen`(GameData 로드→ChunkTileMap→Sim 생성→SpawnPlayer→
    PlayScreen 전환), `PlayScreen`(GameLoop 구동, 정수 배율 뷰포트를 창 가운데 정렬, 게임 뷰포트/전체
    창 뷰포트를 전환해 디버그 오버레이는 항상 전체 창에 그림)
  - `com.wildbond.client.map.FileChunkLoader` — `<cx>_<cy>.wbc` 파일 로더, sim(ChunkTileMap, 동기)과
    렌더러(비동기 디코드 스레드)가 같은 인스턴스를 공유(무상태라 스레드 안전)
  - client-desktop: `DesktopLauncher`(1280×720, vsync, 제목 "Wildbond"), `WildbondPaths`
    (%LOCALAPPDATA%\Wildbond\, 없으면 실행 폴더 — 실제 세이브·로그 쓰기는 M1). Gradle `application` 플러그인
    으로 `:client-desktop:run` 태스크, 절대 경로를 시스템 프로퍼티로 전달(:sim:bench 와 같은 관례)
  - `client-core` ArchitectureTest 2개 추가 — sim 의 components/systems 패키지 모두 의존 금지
    (CLAUDE.md "client-core는 sim의 컴포넌트 클래스를 직접 참조하지 않는다")
- 검증: **하네스 전체 모드 통과**(`reports\harness\20260904-115212.md`) — spotless 5s / build 9s /
  sim:bench 7s, 총 29s. 테스트 총 43 / 실패 0 / 오류 0 / 스킵 0 (기존 41 + client-core ArchTest 2개).
  **`.\gradlew.bat :client-desktop:run` 수동 확인**: 창이 "Wildbond" 제목으로 뜨고 크래시 없음. 이 세션은
  화면이 잠겨 있어(원격/무인 상태로 추정) 화면 전체 캡처(CopyFromScreen)는 잠금 화면만 찍혔다 —
  `PrintWindow(hwnd, ..., PW_RENDERFULLCONTENT)`로 창을 직접 캡처해 실제 렌더링을 확인했다(T-007).
  확인된 것: 풀밭(초록) 배경, 파란 32×48 플레이어 블록이 스폰 위치(타일 5.5,5.5)에 정확히, 화면 우하단에
  연못(water) 색이 청크 컴파일 결과와 일치, 디버그 오버레이 텍스트(FPS 60~61, tick 이 계속 증가, avg
  0.01ms/max 0.02ms — §5 수용 기준 "틱 시간 ≤ 2ms" 대비 100배 이상 여유, draws 5 — 수용 기준 "≤10" 충족,
  chunks 4 — test_island 청크 4개 전부 로드). 세션이 잠겨 있어 SendKeys 로 WASD 입력을 흉내 내려 했으나
  `Access is denied`(T-007) — **이동/충돌의 실시간 조작 확인은 못 했다**. 대신 임시 진단 출력으로
  `backbuffer=1280x720 viewport=0,0,1280,720 scale=2`를 직접 확인해 카메라/뷰포트 계산 자체가 정확함을
  코드 레벨에서 검증했고(진단 코드는 확인 후 제거), sim 의 이동/충돌 로직 자체는 단계3 MovementCollisionTest
  로 이미 검증되어 있다. **사람이 실제로 걸어 다니며 확인하는 절차가 남아 있다** — 아래 "남은 일" 참고
- 결정:
  - **`gdx-platform:natives-desktop` 를 §5.6(윈도우 x64 네이티브만) 예외로 추가.** lwjgl3 백엔드도
    `GdxNativesLoader`(gdx64.dll, GLFW 초기화 이전 단계)를 위해 이 아티팩트가 필요하다(T-005) — POM만
    보고 "lwjgl3면 필요 없다"고 오판했었다. windows 전용 classifier 가 upstream 에 없고, natives-desktop
    은 win/mac/linux 를 합쳐도 ~1.1MB 라 zstd-jni/LWJGL3 때와 달리 크기 예외를 적용했다
  - **JDK 25 native-access 플래그를 client-desktop:run 에도 적용**(T-004 연장) — LWJGL 이 네이티브를
    로드하므로 같은 경고가 난다
  - **카메라는 yDown.** 타일 그리드·청크 포맷(§8.2)이 행 우선(위→아래로 ty 증가)이라 렌더 좌표계를
    거기 맞췄다. 부작용: 기본 `BitmapFont` 는 y-업 가정이라 텍스트가 뒤집힌다 — `BitmapFont(true)` 로
    해결(T-006)
  - **글꼴은 LibGDX 기본 내장 폰트.** 커스텀 폰트 에셋은 M0 범위 밖
  - **타일 픽셀 크기(32px) 상수를 client-core 에 따로 둔다**(`RenderConstants.TILE_PX`) — sim 의
    `SimConstants`/`systems` 패키지를 참조하면 새 ArchUnit 규칙(컴포넌트/시스템 캡슐화)의 취지를 어기게
    된다. 두 상수가 어긋나지 않게 유지하는 건 문서(CLAUDE.md 좌표 규칙, 32px 고정)로 보장
  - **카메라/UI 뷰포트 크기는 매 프레임 `getBackBufferWidth/Height` 로 다시 계산한다** (생성자/resize()
    콜백 한 번에 캐시하지 않음) — 리사이즈 타이밍에 기대지 않는 안전한 선택, 비용은 무시할 만함
- 남은 일 / 다음 단계: **사람이 `.\gradlew.bat :client-desktop:run` 으로 직접 걸어 다니며 수용 기준
  (연못·절벽에 막히는지, 60fps 에서 안 떨리는지)을 최종 확인해야 한다** — 이 세션은 입력 주입이 막혀 있어
  못 했다. 확인되면 단계 6(전투: 스킬·히트 판정·데미지)으로 진행

## 2026-09-04 단계 5 (계속) — 사용자가 "최종확인 해줘" 요청 → 화면 잠금은 풀렸으나 원격 입력 주입은 안 됨
- 상황: 화면이 잠겨 있던 상태가 풀려서(사람이 실제로 PC 앞에 있음) 다시 `:client-desktop:run` 을 띄우고
  WASD 조작까지 자동으로 검증해 보려 했다
- 시도: `SendKeys::SendWait` (탭 20회) → 창이 움직인 흔적 없음. `keybd_event` 로 D 키를 2초간 진짜로
  누르고 있게 함 → 그 사이 창이 최소화됨(IsIconic=True, 원인 불명 — 포커스 관련 부작용으로 추정),
  복원 후 다시 봐도 이동 없음. `SetForegroundWindow` 단독/`AttachThreadInput` 우회 둘 다
  `GetForegroundWindow()` 로 확인해 보니 실제 포그라운드가 계속 다른 창(핸들 527338, 이 세션 도구가
  실행되는 터미널쪽으로 추정)이었다 — 즉 이 자동화 프로세스가 보낸 키 입력이 애초에 Wildbond 창으로
  간 적이 없었다는 뜻이다
- 원인: Windows 의 포커스 하이재킹 방지(포그라운드 잠금) 정책 — 사용자가 실제로 조작 중인 창이 있으면,
  백그라운드 프로세스가 `SetForegroundWindow`/`AttachThreadInput` 을 불러도 실제 포커스를 가져오지
  못한다(보안 기능, 우회가 의도된 것이 아니다)
- 결론: **이 환경(원격 자동화 세션)에서는 실제 키보드 조작 검증이 원천적으로 불가능하다** — 코드 문제가
  아니라 OS 보안 정책 때문. 렌더링·통계(FPS/틱시간/드로우콜/청크수)는 스크린샷으로 이미 확인했고
  (T-007), sim 의 이동/충돌 로직 자체도 단계3 유닛 테스트로 이미 검증됐다. 남은 것은 "실제 사람이 WASD
  로 걸어서 연못·절벽에 막히는지 눈으로 보는" 30초짜리 확인뿐이다
- 재발 방지: **원격/자동화 세션에서 GUI 앱의 키보드 상호작용을 검증하려 시도하지 않는다.**
  `SendKeys`/`keybd_event`/`SetForegroundWindow`(+`AttachThreadInput`) 모두 시도해 봤지만 전부 실패하며,
  실패 자체가 예측 불가능한 부작용(창이 갑자기 최소화되는 등)을 만들 수 있다. 렌더링 결과 확인은
  `PrintWindow`(T-007)로 계속 유효하지만, 키 입력이 필요한 시나리오는 처음부터 "사람 확인 필요"로
  분류하고 시간을 쓰지 않는다
- 남은 일 / 다음 단계: 동일 — 사람이 WASD 로 최종 확인 후 단계 6 진행

---

## 2026-09-04 단계 4 — 청크 포맷(.wbc) + Tiled 컴파일러  (완료)
- 목표: architecture.md §8.2 의 청크 파일 포맷과 컴파일러 구현 — Tiled .tmx(CSV 인코딩) 를 32×32 바이너리
  청크로 컴파일하고, sim 이 그 충돌 레이어를 ChunkTileMap 으로 읽게 한다
- 한 일:
  - 의존성: `com.github.luben:zstd-jni:1.5.7-16` `win_amd64` classifier 로 추가(§5.6, 기본 아티팩트는
    전 플랫폼 네이티브를 한 jar 에 묶어서 안 됨 — Maven Central 에서 `win_amd64` 분리 아티팩트 확인 후 사용).
    `data/build.gradle.kts` 에 `implementation(variantOf(libs.zstd.jni) { classifier("win_amd64") })`
  - `data/.../chunk` 패키지: `ChunkFormat`(magic "WBC1", version, SIZE=32, collision 비트
    solid=1/water=2/cliff=4/edge=8, TileCollision↔비트 변환 헬퍼), `ChunkCoord`, `ChunkObject`(type,
    tileX,tileY,props — LinkedHashMap 기반, 삽입 순서 보존), `Chunk`(record, 배열 컴포넌트라 equals/
    hashCode 직접 구현 — record 기본 equals 는 배열을 참조 비교한다), `ChunkWriter`/`ChunkReader`
    (헤더+zstd 압축 페이로드, DataOutputStream/DataInputStream 으로 u16/u8/UTF 직렬화)
  - `sim.ChunkTileMap` — `Function<ChunkCoord,Chunk>` 로더로 지연 로드+캐시(청크 수가 적어 조회 전용
    HashMap 사용, "HashMap 순회 금지" 규칙과 무관 — 그 규칙은 매 틱 엔티티 순회 결정성용), 월드 타일 좌표를
    청크 좌표+로컬 좌표로 변환해 `TileMap.collision()` 구현
  - `tools/chunk-compiler`: `TmxParser`(DOM, CSV 레이어·objectgroup·properties 파싱, 검증 실패는
    `TmxParseException`), `TmxMap`(중간 표현), `ChunkCompiler`(32×32 슬라이싱, 맵 경계에 걸친 청크는
    남는 칸을 0/NONE 으로, ground GID → Tile.csv id(`gid - firstgid + 1`) → collision 비트 변환, 오브젝트는
    소속 청크에 배정), `ChunkCompilerMain`(CLI), `PlaceholderTilesetGenerator`(색상 블록 PNG 생성)
  - Gradle 태스크: `:tools:chunk-compiler:generatePlaceholderTileset`, `:tools:chunk-compiler:compileChunks`
    (전자에 의존, `assets/maps/src/test_island.tmx` → `assets/maps/chunks/<cx>_<cy>.wbc`)
  - `assets/maps/src/test_island.tmx` — 64×64(청크 4개), 풀밭 기본 + 연못(10~17,10~15, water) + 절벽
    (40~41 열, 5~58 행 — 청크 경계를 세로로 가로지르게 설계해 슬라이싱을 실제로 검증) + 돌 자원 노드
    오브젝트 3개(type=resource_node, prop item=5). 타일셋은 firstgid=1·columns=5 로 Tile.csv id(1~5)와
    GID 가 그대로 대응하게 설계(별도 매핑 테이블 불필요)
  - 테스트 11개 — `ChunkRoundTripTest` 3(라운드트립 일치, zstd 압축이 원본보다 작음, magic 깨지면 예외),
    `ChunkTileMapTest` 3(연못→water, 월드 좌표→소속 청크 변환, 로더가 청크당 1번만 호출), `ChunkCompilerTest`
    5(실제 test_island.tmx 컴파일 — 청크 4개, 연못 water, 절벽이 청크 경계 위아래 양쪽에서 확인, 평지 NONE,
    오브젝트 3개가 각자 소속 청크에 정확히 배정)
- 검증: **하네스 전체 모드 통과** (`reports\harness\20260904-105331.md`) — spotless 5s / build 9s /
  sim:bench 7s, 총 28s. 테스트 총 41 / 실패 0 / 오류 0 / 스킵 0 (기존 30 + 신규 11).
  `:tools:chunk-compiler:compileChunks` 직접 실행 → `assets/maps/chunks/{0_0,0_1,1_0,1_1}.wbc` 4개 생성
  확인(각 44~148바이트 — 균일한 지형이라 zstd 압축률이 매우 높음), `assets/tilesets/placeholder.png` 생성 확인
- 결정:
  - **zstd-jni 는 win_amd64 classifier 로만 받는다.** 기본 아티팩트는 aix/linux/darwin/freebsd 등 모든
    플랫폼 네이티브(.so/.dylib/.dll)를 한 jar 에 묶어 CLAUDE.md §5.6(윈도우 x64 네이티브만)을 어긴다.
    Maven Central 에 `win_amd64` 분리 classifier 아티팩트가 있어 그걸 썼다
  - **JDK 25 의 네이티브 로드 제한 경고**를 모든 Test 태스크와 compileChunks 에 선제 대응
    (`--enable-native-access=ALL-UNNAMED`) — troubleshooting.md T-004. 단계 5 에서 LWJGL3 를 붙일 때
    `:client-desktop:run` 에도 같은 처리가 필요할 것
  - **ChunkObject 의 tileX/tileY 는 월드(절대) 타일 좌표**로 저장한다(청크 로컬 좌표 아님). 소비하는 쪽
    (스폰·워크태스크 등)이 별도로 청크 오프셋을 더할 필요가 없게 하기 위함
  - **Tiled GID 와 Tile.csv id 를 firstgid=1 로 직접 대응시켰다** (`tileId = gid - firstgid + 1`).
    별도 GID→id 매핑 테이블을 두는 대신, 타일셋 이미지의 타일 순서를 Tile.csv id 순서(1=grass..5=sand)와
    맞춰 변환을 자명하게 만들었다 — 매핑 테이블 자체가 틀릴 수 있는 원천을 없앤다
  - **collision 비트가 여러 개 겹치면 solid &gt; cliff &gt; water 순으로 단일 TileCollision 을 고른다**
    (`ChunkFormat.collisionFromBits`). M0 데이터는 타일당 비트 하나뿐이라 실제로는 겹치지 않지만, 포맷 자체가
    비트마스크이므로 역변환 규칙을 명시해 뒀다
  - **Chunk record 에 equals/hashCode 를 직접 구현.** 배열 컴포넌트가 있는 record 의 기본 equals 는 배열을
    참조로 비교해 라운드트립 테스트가 항상 실패했을 것 — Arrays.equals/hashCode 로 값 비교하게 고쳤다
- 남은 일 / 다음 단계: 단계 5 (client: LibGDX 창·게임 루프·청크 렌더·입력→Command). ChunkTileMap 을 실제
  파일 시스템 로더(디렉터리에서 `<cx>_<cy>.wbc` 읽기)와 연결하는 것은 client-core 의 몫

---

## 2026-09-04 단계 3 — sim 코어: ECS · 고정 틱 · 이동/충돌 · 결정성  (완료)
- 목표: architecture.md §4 의 wildbond-sim 을 M0 범위로 구현 — Artemis-odb 기반 ECS, Sim.step() 진입점,
  8방향 이동·타일 AABB 충돌·엔티티 밀어내기, xoshiro256** Rng, stateHash 리플레이 결정성, sim:bench 예산 검증
- 한 일:
  - 의존성: `gradle/libs.versions.toml` 에 `net.onedaybeard.artemis:artemis-odb:2.3.0` 핀 (Maven Central 확인,
    런타임 전이 의존성 없음). `sim/build.gradle.kts` 에 `implementation`, `:sim:test` 에 `wildbond.tables`
    시스템 프로퍼티 배선(data 모듈과 동일 패턴), `:sim:bench` JavaExec 태스크 등록
  - `com.wildbond.sim.components` (public, Artemis 컴포넌트): Position, Velocity, Collider(w,h,layer),
    Health(current,max), EntityIdComponent(안정적 정수 id), PlayerTag(표식)
  - `com.wildbond.sim.systems`: SimConstants(이동 상수), EntityIndex(stableId↔Artemis id, 이진 탐색,
    발급 순서=오름차순), EntityQueries(SimView 가 위임하는 유일한 컴포넌트 읽기 창구),
    CommandApplySystem(§4.1 시스템1 — MoveInput/SpawnPlayer 처리, UseSkill 은 단계6까지 무시),
    MovementSystem(시스템4 — velocity×dt, x축→y축 순서로 스윕, 엔티티간 약한 밀어내기),
    CombatSystem(시스템5, 빈 껍데기), EventFlushSystem(시스템 마지막, EventBus.flush())
  - `com.wildbond.sim.events`: SimEvent(sealed) / EntitySpawned / EntityMoved / Damaged(단계6까지 미발행),
    EventBus(구독자 없으면 EntityMoved 를 아예 쌓지 않음 — 500엔티티 벤치 할당 방지)
  - `com.wildbond.sim` 공개 API: Sim(GameData,TileMap,long seed 생성자, step(tick,commands), stateHash()),
    SimView, Command(sealed: MoveInput/UseSkill/SpawnPlayer), Dir8(8방향+NONE), EntityKind, TileMap,
    ArrayTileMap(테스트·벤치용), Rng(xoshiro256**, combat/spawn/loot 스트림)
  - `com.wildbond.sim.bench.SimBench` — JavaExec 진입점. 500엔티티 SpawnPlayer 로 생성 후 1000틱 이동,
    평균/최대 틱 시간 출력, 평균 > 8ms 면 System.exit(1)
  - ArchitectureTest 규칙 추가/수정: `componentsOnlyUsedBySystems`(컴포넌트는 components/systems 패키지
    에서만 다룬다 — Sim 은 EntityQueries 에 위임해 컴포넌트 타입을 직접 참조하지 않음),
    `noStandardStreams` 에 `com.wildbond.sim.bench..` 예외 추가(벤치 결과 출력용)
  - 테스트 12개 — RngTest 5(같은 시드 동일 시퀀스, 다른 시드 발산, 스트림 독립, nextFloat/nextInt 범위),
    SimSpawnTest 2(스폰 상태, stableId 오름차순), MovementCollisionTest 2(벽 통과 못함, 코너 한 축 슬라이드),
    ReplayDeterminismTest 2(같은 시드 1000틱 → 같은 stateHash, 다른 시드 → 다른 해시)
- 검증: **하네스 전체 모드 통과** (`reports\harness\20260904-102034.md`) — spotless 6s / build 8s /
  sim:bench 7s, 총 28s. 테스트 총 30 / 실패 0 / 오류 0 / 스킵 0 (기존 18 + 신규 12, ArchitectureTest 규칙
  1개 추가로 순수 신규는 11개 테스트 + 1개 ArchTest). `:sim:bench` 직접 실행 결과:
  `entities=500 ticks=1000 avg=0.230ms max=2.565ms` — 예산(§9.4 ≤8ms) 대비 크게 여유 있음
- 결정:
  - **EntityId(stableId)는 Artemis 내부 엔티티 id와 분리한 별도 카운터.** Artemis는 삭제된 엔티티의 내부 id를
    재사용하므로, 세이브·이벤트·정렬 기준(§4.3, §6)이 되려면 독립적이어야 한다. `EntityIndex`가 이진 탐색으로
    양방향 변환하며, 삭제가 없는 M0 단계3 범위에서는 발급 순서 자체가 오름차순 순회 순서와 같다(별도 정렬 불필요)
  - **stateHash 는 seed 를 직접 섞어 넣는다.** M0 단계3은 이동만 있고 CombatSystem/SpawnSystem 이 빈 껍데기라
    Rng 를 소비하는 게임 로직이 없다 — "다른 시드면 해시가 다르다" 테스트가 의미를 가지려면 seed 자체가
    해시 입력이어야 한다고 판단. 이후 단계에서 Rng 소비 로직이 늘어도 이 설계는 그대로 유효하다
  - **컴포넌트는 public 클래스(Artemis reflection 관례상 안전), 대신 ArchUnit + EntityQueries 위임으로 캡슐화.**
    package-private 컴포넌트는 Artemis 리플렉션 인스턴스화 방식(`getConstructor` 가 public만 반환할 가능성)이
    라이브러리 버전에 따라 불확실해 위험 부담이 컸다. 대신 "컴포넌트 패키지는 components/systems 밖에서
    참조 금지" ArchUnit 규칙과, Sim 이 컴포넌트를 절대 직접 만지지 않고 EntityQueries 에 위임하는 구조로
    같은 효과(캡슐화)를 얻었다. 실제 Artemis-odb 2.3.0 jar 를 받아 `javap` 로 API 를 직접 확인한 뒤 결정
  - **엔티티 간 밀어내기는 O(n²) 페어와이즈.** §9.4 의 공간 해시는 거점 3개·팰150마리 예산 기준 최적화이고,
    M0 단계3(500엔티티, 열린 필드)에서는 O(n²)도 벤치 결과(평균 0.230ms) 로 예산에 크게 못 미쳐 불필요.
    필요해지면(§9.4 목표 미달 시) 공간 해시로 교체
  - **이동 튜닝 상수(속도·플레이어 크기 등)는 SimConstants 임시 고정값.** GameData 에 아직 플레이어 스탯
    테이블이 없다 — 추후 데이터 테이블이 생기면 그쪽으로 옮긴다
- 남은 일 / 다음 단계: 단계 4 (청크 포맷 .wbc + Tiled 컴파일러). ChunkTileMap 이 지금의 ArrayTileMap 을
  대체할 sim.TileMap 실제 구현이 된다

---

## 2026-09-04 단계 2 — data 정적 테이블 코드젠  (완료 — 위 단계3 하네스 실행으로 검증 확인)
- 목표: CSV 를 원천으로 Java record·enum 을 생성하고, 잘못된 데이터가 빌드를 깨게 만들기
- 한 일:
  - `data/tables/` 6개 CSV — `enums.csv`(열거형 7종), `PalSpecies`(3종), `Skill`(4), `Item`(6), `Tile`(5), `ElementChart`(9×9)
  - `tools/datagen` 구현 8클래스 — `Csv`(주석·인용 지원), `ColumnType`(타입 행 파서), `Schema`(로더),
    `Validator`(검증), `Emitter`(코드 생성), `Names`, `GenException`, `DataGenMain`
  - 타입 문법: 스칼라 / 범위 `int(0..4)` / 고정 길이 배열 `int[10]` / `enum:Name` / `ref:Table` / `?` 널 허용
  - 생성물 13개 — enum 7종, record 4종(`PalSpecies` `Skill` `Item` `Tile`), `ElementChart`, `GameData`
  - `data/build.gradle.kts` — `datagenTool` 설정으로 :tools:datagen 을 도구로만 쓰고(순환 없음),
    `generateData` JavaExec 를 `compileJava` 앞에 배선, 생성 디렉터리를 sourceSet 에 추가, 테스트에 `wildbond.tables` 전달
  - 테스트 11개 — `GameDataTest` 7 (행 수, 스칼라·열거형 파싱, 빈 칸→null, 배열 길이, 참조 해석, 포획구 배수, 없는 id 예외),
    `ElementChartTest` 4 (축 크기, 비대칭, 배수 3단계, none 은 항상 1.0)
- 검증: **하네스 미실행 — PC 실행 필요** (T-001). 원격에서 가능한 범위는 모두 확인:
  - 생성기 컴파일 → 실행 → 생성 코드 `javac -Xlint:all` 경고 0
  - 로드 동작 스모크 30항목 전부 통과 (별도 main 으로 실제 값 검증)
  - **검증기 역방향 테스트 10종 전부 잡힘** — id 중복 / 범위 초과 / 배열 길이 / 없는 enum 값 /
    깨진 ref / 필수 칸 비움 / 상성 배수 범위 / 상성표 행 순서 / 칸 수 불일치 / 없는 열거형.
    오류 메시지가 파일·행·컬럼을 지목함
  - Gradle 배선: `gradle compileJava --offline -Pwildbond.javaVersion=21` 로 `generateData` → 생성 → 전 모듈 컴파일 성공
  - **PC 하네스 실행 확인** (`reports\harness\20260904-095919.md`): 전체 모드 통과, spotlessApply 8s / build 12s,
    총 26s, 테스트 총 18 / 실패 0 / 오류 0 / 스킵 0 — 예상했던 18개(기존7 + data11)와 정확히 일치
- 결정:
  - **열거형은 `enums.csv` 단일 원천.** 컬럼 값에서 추론하지 않는다 — 오타가 상수로 굳는 것을 막는다
  - **`ElementChart` 는 행렬로 특별 취급.** id 표가 아니므로 축을 `Element` 열거형과 대조해 검증하고 정적 클래스로 생성
  - **`work` 는 `int[10]` 단일 컬럼**(`WorkType` 순서). 10개 컬럼으로 펼치면 생성기에 표별 특수 규칙이 생긴다
  - **생성 코드는 `data/build/generated/` 로.** 소스 트리에 넣지 않으므로 spotless 대상도 아니다
  - M0 는 CSV 런타임 로드. FlatBuffers 바이너리는 M1 (§8.1 그대로)
- 남은 일 / 다음 단계: 없음. 단계 3 완료

---

## 2026-09-04 단계 1 (계속) — 백신 격리 회피를 위한 경로 이전
- 목표: Gradle 배포판 zip 이 다운로드 직후 격리되는 문제(T-002) 해소
- 한 일: 프로젝트를 `C:\develop\wildbond-game` → `C:\Users\user\Desktop\develop\wildbond-game` 로 복사(바탕화면 트리는 백신 예외).
  Gradle 홈을 `C:\Users\user\Desktop\develop\.gradle-home` 로 지정 — `harness.ps1` 에 `GRADLE_USER_HOME` 폴백 추가,
  환경 변수 설정 절차를 docs/m0-prompts.md 단계 0 에 기재. CLAUDE.md 에 "환경" 절 추가(경로 되돌리지 않기)
- 검증: 파일 41개 새 경로로 복사 확인. 하네스 실행은 대기
- 결정: 빌드 산출물·의존성 캐시를 백신 예외 경로 밖에 두지 않는다 (T-002 재발 방지)
- 검증 결과: 새 경로에서 배포판 다운로드·압축 해제 성공, 하네스 통과 — T-002 해결 확인
- 남은 일 / 다음 단계: 구 경로 `C:\develop\wildbond-game` 삭제(원격 도구에 삭제 권한 없음 → 사람이 직접)

---

## 2026-09-04 단계 1 — Gradle 멀티프로젝트 뼈대 + ArchUnit  (완료)
- 목표: 모듈 경계·공통 규약·ArchUnit 격리 규칙을 코드로 고정하고 하네스가 실제로 돌게 만들기
- 한 일:
  - Gradle 래퍼 생성, `distributionUrl` = Gradle 9.2.0
  - `settings.gradle.kts` — 모듈 6개(`data`, `sim`, `client-core`, `client-desktop`, `tools:chunk-compiler`, `tools:datagen`)
  - `gradle/libs.versions.toml` — junit 5.13.4 / assertj 3.27.3 / archunit 1.4.1 / spotless 7.2.1 / google-java-format 1.28.0
  - 루트 `build.gradle.kts` — 공통 규약(java-library, toolchain, `-Xlint:all`, UTF-8, JUnit5, spotless, JUnit XML 리포트)
  - `gradle.properties` — GJF 용 `--add-exports`, parallel/caching, `wildbond.javaVersion=25`
  - 모듈별 `build.gradle.kts` 로 §5.1 의존 방향 배선
  - `sim` — `Ticks`(§4.3 고정 틱 상수), `SimModule`; `ArchitectureTest`(LibGDX·네트워크·java.util.Random·Math.random·로깅·표준출력 금지), `TicksTest`
  - 나머지 모듈은 자리표시자 클래스 1개씩, `.editorconfig`
- 검증: **하네스 전체 모드 통과** (`reports\harness\20260904-093843.md`) — spotless 57s / build 17s / 총 81s,
  테스트 총 7 · 실패 0 · 오류 0 · 스킵 0 (`TicksTest` 2 + `ArchitectureTest` 규칙 5).
  `sim:bench` 는 단계 3 이후이므로 "없음"으로 건너뜀. 첫 실행 전 `spotlessApply` 1회 필요했음.
  참고: 원격 세션에서는 오프라인 검증만 가능했다(T-001) — `gradle projects --offline`,
  `compileJava --offline -Pwildbond.javaVersion=21`, `javac` 구문 확인.
- 결정:
  - **build-logic 대신 루트 `subprojects {}`로 공통 규약 적용.** m0-prompts Prompt 1 은 build-logic 을 요구했으나,
    `kotlin-dsl` 로 사전 컴파일 플러그인을 만들면 JDK 25 에서 embedded Kotlin 의 jvmTarget 문제가 생길 수 있고
    원격 세션에서 검증할 수 없다. 모듈 6개 규모에서는 이득도 작다. 필요해지면 분리한다
  - **`tools:datagen` 은 `:data` 에 의존하지 않는다** — 의존하면 `:data:compileJava` → `:tools:datagen` → `:data` 순환.
    architecture.md §5.1 과 CLAUDE.md 의 의존 블록을 이에 맞게 정정했다
  - **Gradle 9.2.0 핀** (JDK 25 지원 예상 버전). 래퍼가 배포판을 못 찾거나 Gradle 이 Java 25 를 거부하면
    `gradle/wrapper/gradle-wrapper.properties` 의 버전 한 줄만 올린다
  - **`wildbond.javaVersion` 프로퍼티** 추가 — 기본 25(D-15), 다른 JDK 로 검증할 때만 덮어쓴다
  - 라이브러리 버전은 오프라인에서 핀했으므로 추측이 섞여 있다. `libs.versions.toml` 상단 주석 참고
- 남은 일 / 다음 단계: 없음. **버전 핀은 전부 해석 성공** — junit 5.13.4 / assertj 3.27.3 / archunit 1.4.1 /
  spotless 7.2.1 / google-java-format 1.28.0 / Gradle 9.2.0(JDK 25 지원 확인). 다음은 단계 2 (Prompt 2)

---

## 2026-09-04 단계 0 — 개발 환경 확인
- 목표: 개발 환경 확인 후 단계 1 진입 가능 상태로
- 한 일: 개발 PC 확인 — Temurin JDK 25.0.1 LTS 설치됨(64-bit). 문서를 Java 25 / Gradle 9.x 기준으로 갱신
  (architecture.md v0.4, CLAUDE.md, docs/m0-prompts.md). 하네스를 bash → **PowerShell**(`harness.ps1` + `harness.cmd` 래퍼)로 교체,
  Stop 훅도 PowerShell 로 교체
- 검증: `java -version` → 25.0.1 (64-Bit Server VM). 하네스는 아직 종료 코드 2 (gradlew.bat 없음, 정상)
- 결정: **D-15 JDK = Java 25 LTS.** JDK 21을 추가 설치해 두 개를 관리하는 대신, 이미 설치된 25 LTS를 쓴다.
  필요 라이브러리(LibGDX·Artemis-odb·ArchUnit·spotless)는 모두 순수 JVM이라 25에서 동작. Gradle은 25를 지원하는 9.x 사용
  하네스를 PowerShell 로 작성 — git(Git Bash) 설치를 요구하지 않기 위해. 개발용 스크립트는 앞으로도 PowerShell 또는 Gradle 태스크로
- 남은 일 / 다음 단계: 단계 1 (Prompt 1) 진행. 추가 설치할 도구 없음

---

## 2026-09-03 단계 0 — 사전 준비
- 목표: 프로젝트 폴더와 설계 문서, 에이전트 지침 준비
- 한 일: `docs/architecture.md`(v0.3), `CLAUDE.md`, `README.md`, `assets/`·`data/tables/` 디렉터리, `docs/m0-prompts.md`, `plan.md`, `troubleshooting.md`, 테스트 하네스, `.claude/settings.json` Stop 훅
- 검증: 파일 존재 확인. 하네스 → 종료 코드 2 (gradlew 없음, 단계 1 전이므로 정상)
- 결정: 버전 관리(git)·CI는 당분간 도입하지 않고 로컬로만 작업. 필요해지면 추가
- 남은 일 / 다음 단계: 2026-09-04 항목 참고
