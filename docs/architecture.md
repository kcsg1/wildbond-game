# Wildbond 아키텍처 v0.5 (2D 탑다운 RPG · 싱글플레이 · Java 단일 스택 · Windows x64)

- 작성일: 2026-09-03 (v0.5: 2026-09-09)
- 상태: 검토 대기
- 대상 플랫폼: **Windows 10/11 64-bit (x64)** 단일. **개인용(배포 계획 없음, D-18)**. macOS·Linux는 범위 밖
- 팀 규모 가정: 1~5명, Java 강점
- 이 문서는 설계의 **단일 원천**이다. 코드와 문서가 충돌하면 문서를 따르고, 문서를 바꿔야 하면 먼저 문서를 고친다.

## 변경 이력

| 버전 | 변경 |
|---|---|
| v0.1 | 3D · Unity(C#) 클라이언트 + Java 플랫폼 |
| v0.2 | 2D 탑다운으로 전환. LibGDX(Java) 단일 스택, 공유 sim, Netty 서버, Spring Boot 플랫폼 |
| v0.3 | **멀티플레이 제외.** net/server/platform 계층 삭제, sim은 클라이언트 프로세스 안에서 고정 틱으로 실행. 멀티는 §6 "확장 경로"로만 남김. 대상 플랫폼을 **Windows x64 단일**로 고정 |
| v0.4 | **Java 21 → 25 (LTS)**, Gradle 9.x. 개발 PC에 이미 설치된 JDK를 그대로 사용 (D-15) |
| **v0.5** | **장르 전환 (D-19): 팰 포획·거점 건설 → 바람의나라식 사냥 RPG.** 팰·포획구·파티·거점·노동·서바이벌 삭제. 몬스터·전리품표·경험치/레벨·인벤토리·NPC 상점 추가. 월드는 존 분할(D-16) 유지 |

---

## §0 전제와 범위

### 장르 정의

- **2D 탑다운 사냥 RPG (바람의나라식)** — 32px 타일, 존(맵) 단위 월드, 마을(안전)과 사냥터·던전(몬스터)을 가장자리·포털로 오간다
- **핵심 루프** — 마을에서 준비 → 사냥터에서 몬스터 사냥 → 경험치·돈·전리품 → 레벨업·장비 강화 → 더 깊은 던전
- **전투** — 바라보는 방향 근접 공격(스페이스), MP 를 쓰는 원거리/마법 스킬. 몬스터마다 성향(무해/소극/공격적)이 다르다
- **성장** — 경험치 → 레벨 → 스탯(체력·공격·방어·마나). 장비(무기·갑옷)로 보정. 기술은 후순위
- **경제** — 몬스터 전리품(가죽·고기 등)을 마을 NPC 에게 팔고, 장비·소모품을 산다
- **싱글플레이 전용** — 멀티플레이는 범위 밖. 단, §6 규칙을 지켜 나중에 붙일 수 있게 한다
- **개인용** — 배포·상업화 계획 없음(D-18). 그래서 아트 라이선스 제약이 느슨하다

v0.4 까지의 "팰 포획·동행·거점 건설·노동·서바이벌"은 **전부 범위 밖으로 뺐다** (D-19). 그 코드는 삭제한다 — 반쪽짜리를 남겨 두지 않는다.

### 비기능 목표

| 항목 | 목표 | 비고 |
|---|---|---|
| 프레임 | 60 fps, 내장 GPU(Intel Iris Xe급)에서 유지 | 화면 내 몬스터 60마리, 드로우콜 ≤ 50 |
| sim 틱 | 20 Hz 고정(50ms), 틱당 CPU ≤ 8 ms | 존 하나에 몬스터 100마리 기준 |
| 세이브 무결성 | 크래시 시 손실 ≤ 5분 | 주기 저장 + 종료 시 저장 + 직전 .bak (M1) |
| 규칙 무결성 | 데미지·전리품·경험치·인벤토리는 전부 sim 내부에서만 변경 | UI가 상태를 직접 고치지 않음 (§6) |
| 지원 OS | Windows 10 (1809+) / 11, 64-bit | 32-bit·ARM 미지원. 그래픽: OpenGL 3.2+ |

---

## §1 기술 스택 결정

### D-01 게임 프레임워크 — **LibGDX 1.13 (Java)** [기본안]

- 성숙한 2D 프레임워크(SpriteBatch, Tiled 로더, Scene2D UI, 파티클, 오디오)
- 전 계층 Java → Gradle·JUnit·ArchUnit 한 벌로 검증
- 약점: 에디터 없음 → Tiled + 자체 툴, 조명/셰이더 직접 작성
- 대안: Unity 2D(C#, 에디터·2D 라이트 내장이나 Java 아님), Godot 4(2D 에디터 우수, Java 아님)

### D-02 시점 — **탑다운(직교, 약간의 정면 기울기)** [기본안]

- 타일 = 정사각형, 충돌·A*·건축 그리드 단순. 거점 노동 동선이 한눈에 보임
- 대안: 아이소메트릭(제작 비용 2배), 사이드뷰(팰월드 재미와 멀어짐)

### 확정 스택

| 영역 | 선택 | 이유 |
|---|---|---|
| 언어·런타임 | **Java 25 LTS** (Temurin, Windows x64), jlink 런타임 번들 | D-15. 사용자 PC에 JDK 설치 불필요 |
| 클라이언트 | LibGDX 1.13 + LWJGL3 (natives: windows-x64 만), Scene2D.ui, steamworks4j (win64) | D-01 |
| ECS | Artemis-odb | sim 코어. 컴포넌트 풀링·직렬화 지원 |
| 직렬화 | FlatBuffers (세이브·정적 데이터) | D-04 |
| 맵 제작 | Tiled(.tmx) → 자체 청크 컴파일러 → 바이너리 청크(.wbc) | 런타임 TMX 파싱은 느림 |
| 정적 데이터 | Google Sheets → CSV → Java record 코드젠 + FlatBuffers 바이너리 | 단일 언어라 코드젠 1벌 |
| 세이브 동기화 | Steam Cloud (steamworks4j) | 자체 서버 없이 기기 간 세이브 동기화 |
| 크래시 리포트 | 로컬 로그 + 선택적 Sentry(D-13) | |
| 빌드·검증 | **Gradle 9.2.0** Kotlin DSL(래퍼 고정), 테스트 하네스(`harness.ps1`) | JDK 25 지원 버전. 버전 관리·CI는 후순위 |

**v0.3에서 제거:** Netty, Spring Boot, gRPC, PostgreSQL, Redis, MinIO, Agones, 플랫폼 서비스 6종.

---

## §2 시스템 구성도

프로세스는 하나(게임 클라이언트)다.

```
┌──────────────────────────────── Game Client (JVM) ────────────────────────────────┐
│                                                                                    │
│  Presentation (client-core)                                                        │
│   입력 매핑 → Command 생성 ─────────────┐                                          │
│   렌더(청크·엔티티·조명·UI) ◀── 읽기 전용 뷰 ◀─┐                                    │
│                                          ▼      │                                  │
│  GameLoop (client-core)                  │      │                                  │
│   고정 틱 누적기 50ms ── Sim.step(tick, commands) ── 틱 끝 상태 발행 (보간용 prev/cur)│
│                                          │                                         │
│  wildbond-sim (순수 Java, ECS) ◀──────────┘                                         │
│   이동·충돌, 몬스터 AI, 전투, 전리품·인벤토리, 경험치·레벨, 스폰·리스폰               │
│   의존: data 만. LibGDX 참조 금지                                                     │
│                                                                                    │
│  SaveSystem ── FlatBuffers ── 로컬 디스크 ── Steam Cloud                             │
│  data ── 정적 테이블(record) · 청크 파일 · 세이브 스키마                               │
└────────────────────────────────────────────────────────────────────────────────────┘
```

Gradle 모듈: `data`, `sim`, `client-core`, `client-desktop`, `tools/{chunk-compiler, datagen}`.

---

## §3 게임플레이 도메인 모델

좌표는 **월드 픽셀(float)**, 그리드는 **타일(int, 32px)**로 구분한다.

### 3.1 핵심 엔티티

| 엔티티 | 정의 | 정적/동적 |
|---|---|---|
| MonsterSpecies | 몬스터 종. 이름, 레벨, 기본 HP/공격/방어, 성향(passive/timid/aggressive), 이동 속도, 크기(1×1 / 2×2), 스킬, 경험치, **전리품표**, 스프라이트 | 정적 |
| MonsterInstance | 개체. 종 + 현재 HP, 행동 상태(Brain), 스폰 출처(리스폰용) | 동적 |
| Player | 레벨, 경험치, HP/MP, 스탯(공격·방어), 소지금, 인벤토리, 장비(M1), 위치, 현재 존 | 동적 |
| Item | 아이템 정의. 종류(currency/material/consumable/weapon/armor), 최대 스택, 판매가·구매가 | 정적 |
| LootTable | 몬스터별 전리품 후보: (아이템, 가중치, 수량 범위). **한 번 죽으면 한 줄만** 가중치로 뽑는다 | 정적 |
| DroppedItem | 바닥에 떨어진 아이템 스택. 주울 수 있고 일정 시간 뒤 사라진다 | 동적 |
| Inventory | 슬롯 배열(아이템 id + 수량). 동전은 여기 넣지 않고 소지금으로 따로 센다 | 동적 |
| Npc | 마을 NPC. 상점(사고파는 목록) 또는 대화. M1 | 정적 + 동적 |
| Zone | 청크 집합 + 스폰표 + 이웃 존 링크 + 포털 (D-16) | 정적 |
| Chunk | 32×32 타일. 지형 레이어 + 오브젝트(나무·집·스폰 포인트·포털) | 정적 |

**삭제된 것(v0.4)**: PalSpecies/PalInstance, 포획구, Party/Owner, Base, Structure, WorkTask, Recipe/Tech, 허기·체온·SAN.

### 3.2 핵심 규칙 (sim 내부에서만 판정)

**데미지** (v0.4 그대로)

```
damage = floor(
    baseAttack × skillPower / 100
  × elementChart[attacker.element][defender.element]   // 0.5 / 1.0 / 2.0
  × (1 + 0.02 × attackerLevel) / (1 + defense / 200)
  × critical(1.5 if roll) × random(0.95, 1.05) )
```

히트 모양: 원 · 부채꼴 · 직사각형 · 투사체. 근접은 즉시 판정, 투사체는 sim 이 이동시킨다. 진영은 둘 —
**플레이어 / 몬스터**. 같은 편끼리는 맞지 않는다(몬스터끼리 난투 방지).

**전리품**

```
kill → LootTable[species] 에서 가중치로 한 줄 선택 (loot 스트림)
     → 수량 = rand(min, max) → DroppedItem 을 시체 자리에 생성
플레이어가 반경 28px 안으로 오면 자동 획득:
     currency → 소지금 += 수량
     그 외    → Inventory 에 스택 (같은 id 스택 우선, 없으면 빈 슬롯, 없으면 못 줍고 남는다)
드롭은 60초 뒤 사라진다
```

"동전 **또는** 가죽"처럼 배타적인 보상은 표에 두 줄로 적는다. 둘 다 주고 싶으면 표를 두 개(1차·2차)로 나눈다 — M1.

**경험치·레벨**

```
몬스터가 죽고 마지막 타격자가 플레이어면 exp += species.exp_yield
expToNext(level) = 20 × level²           // Lv1→2: 20, Lv5→6: 500, Lv10→11: 2000
레벨업: maxHp += 10, maxMp += 5, atk += 2, def += 1, HP/MP 전부 회복, LevelUp 이벤트
```

**사망**

- 몬스터: HP 0 → Dead(5초 시체 유지, 쓰러짐 연출) → 제거. 스폰표가 리스폰 타이머를 건다
- 플레이어: HP 0 → 5초 뒤 마을 시작점에서 HP/MP 전부 회복해 부활. 페널티 없음(M1 에서 경험치 감소 검토)

**몬스터 성향**

| 성향 | 시야 감지 | 맞았을 때 | 예 |
|---|---|---|---|
| passive | 안 함 | 공격자에게서 도망 | 사슴, 토끼 |
| timid | 안 함 | 반격, HP 20% 미만이면 도망 | 여우 |
| aggressive | 12타일 시야에 들어오면 추격 | 반격 | 늑대, 박쥐 |

---

## §4 시뮬레이션 코어 (wildbond-sim)

**LibGDX를 모르는 순수 Java 라이브러리.** 게임 규칙 전부가 여기 있고, 상태 변경은 오직 `Command`를 통해서만 일어난다. 결정적(deterministic)이어야 한다 — 리플레이 테스트와 미래 멀티플레이의 전제.

### 4.1 ECS 구성 (Artemis-odb)

```
Components (데이터만)
  Position(x, y)  Velocity  Collider(w, h, layer)  Health(cur, max)  Mana(cur, max)
  Stats(atk, def, level)  Experience(exp)  Wallet(coins)  Inventory(itemIds[], counts[])
  MonsterData(speciesId)  Brain(state, timers, threat)  Path(waypoints[], idx)  Skills(cooldowns[])
  Dead(ticksRemaining)  DeathAnim  DroppedItem(itemId, amount, ttl)  Projectile  SpawnOrigin(chunk)
  EntityId(stable id — 세이브·이벤트·정렬 기준)  PlayerTag  DummyTag(테스트 허수아비)

Systems (실행 순서 고정 = 결정성)
  1 CommandApplySystem    명령 큐 → Velocity / 액션 의도 / 스폰
  2 AISystem              몬스터 BT (§9.1)
  3 PathFollowSystem      Path → Velocity
  4 MovementSystem        Velocity × dt, 타일 AABB 스윕, 엔티티 밀어내기
  5 CombatSystem          스킬 발동, 히트 판정, 데미지, 사망, MP 소모/회복
  6 DropSystem            사망 → 전리품표 롤 → 드롭 생성, 플레이어 근접 시 획득(소지금/인벤토리)
  7 ProgressSystem        사망 → 경험치 → 레벨업, 플레이어 부활 판정
  8 SpawnSystem           청크 활성/휴면, 스폰표 롤, 리스폰 타이머
  9 EventFlushSystem      도메인 이벤트 → 리스너 (렌더·오디오가 구독)

World 서비스
  TileMap      청크 로드, getCollision(tx,ty)
  Pathfinder   8방향 A* / JPS (§9.3)
  GameData     정적 테이블
  Rng          xoshiro256**, 스트림 분리(combat/spawn/loot), 시드 고정
```

### 4.2 명령(Command)

`sealed interface Command` — MoveInput, UseSkill, UseItem, Interact(NPC·포털), BuyItem, SellItem,
SpawnPlayer, **RestorePlayer**(존 전환·부활 시 레벨·경험치·HP/MP·소지금·인벤토리를 새 sim 에 싣는다 — D-16
"존을 넘어 유지되는 것"의 통로), SpawnMonster(테스트·벤치), SpawnDummy(테스트) …
UI·입력은 Command만 만든다. **sim 상태를 직접 수정하는 코드는 sim 패키지 밖에 존재하지 않는다** (ArchUnit으로 강제).

### 4.3 틱과 결정성

- 고정 틱 50ms. `Sim.step(tick, commands)` 하나의 진입점
- `float` + `StrictMath`, 각도는 정수 1/1024 회전 단위
- 엔티티 순회는 EntityId 오름차순 고정. HashMap 순회 금지 → `IntMap`(순서 보장)
- 틱 끝에 `stateHash()` 제공 (디버그 오버레이·리플레이 테스트)
- 핫 루프 객체 할당 금지 — 컴포넌트 풀링, 프리미티브 컬렉션

### 4.4 테스트 전략

- 규칙 테스트: JUnit으로 "사슴 1000마리 처치 → 동전:가죽 비율이 전리품표 가중치 ±3%p" 같은 통계 테스트
- 리플레이 테스트: 명령 로그 + 시드 → 최종 stateHash 비교
- ArchUnit: sim이 `com.badlogic.*`를 참조하면 빌드 실패; sim 밖에서 컴포넌트를 쓰기(write)하면 실패

---

## §5 클라이언트 (LibGDX)

### 5.1 모듈

| 모듈 | 내용 | 의존 |
|---|---|---|
| client-desktop | LWJGL3 런처(Windows x64), Steam 초기화, jlink/jpackage → `Wildbond.exe` | client-core |
| client-core | Screen 스택, GameLoop, 렌더 파이프라인, 입력→Command, UI, 오디오, SaveSystem | sim, data |
| sim | §4 | data |
| data | 코드젠 record, FlatBuffers 스키마, 청크 포맷 | — |
| tools/chunk-compiler | Tiled(.tmx) → .wbc 청크 컴파일러 (§8.2) | data |
| tools/datagen | CSV → Java record 코드 생성기 (§8.1) | — |

`tools/datagen`은 `data`의 소스를 **생성**하므로 `data`에 의존하지 않는다 — 의존하면
`:data:compileJava` → `:tools:datagen` → `:data` 순환이 생긴다.

공통 규약(toolchain, 컴파일 옵션, JUnit, spotless)은 루트 `build.gradle.kts`의 `subprojects {}`에서 적용한다
(plan.md 2026-09-04 단계 1 결정). 대상 Java 버전은 `gradle.properties`의 `wildbond.javaVersion`(기본 25).

### 5.2 게임 루프

```
render(deltaTime):
  accumulator += min(deltaTime, 0.25)
  while accumulator >= 0.05:
      commands = inputMapper.drain()
      sim.step(tick++, commands)          // 메인 스레드, 동기
      view.captureState()                  // 렌더용 prev/cur 위치 스냅
      accumulator -= 0.05
  alpha = accumulator / 0.05
  renderer.draw(alpha)                     // prev→cur 보간으로 60fps 부드럽게
```

sim은 메인 스레드에서 동기 실행한다(스레드 경계 없음). 틱이 8ms를 넘기면 디버그 오버레이에 경고.

### 5.3 렌더 파이프라인 (프레임당)

1. CameraSystem — 플레이어 보간 위치 추적, 정수 픽셀 스냅
2. ChunkRenderer — 가시 청크(3×3)의 바닥·장식 레이어, 청크당 SpriteCache
3. EntityRenderer — Y-정렬 후 스프라이트 배치, 2×2 몬스터는 발 위치 기준
4. StructureRenderer — 엔티티와 함께 Y-정렬, 지붕은 플레이어와 겹칠 때 반투명
5. LightingPass — FBO에 광원 누적 → 멀티플라이
6. ParticleSystem — 타격·제작·날씨
7. UI (Scene2D) — HUD(HP/MP/소지금/경험치), 인벤토리, 상점(M1)
8. DebugOverlay(F3) — FPS, 틱 시간, 틱 해시, 드로우콜, 청크 수

### 5.4 주요 모듈

- **ChunkStreamer** — 카메라 반경 2청크(5×5) 유지, 디코드는 별도 스레드, GL 업로드는 렌더 스레드
- **SpriteAnimator** — 시트 기반 4방향 클립(걷기·공격), 몬스터 종당 리전 공유
- **BuildCursor** — 풋프린트 타일 유효성을 sim의 검증 함수(순수 함수)로 미리 검사, 확정은 PlaceStructure 명령
- **InputMapper** — 키·마우스·패드 → Command. 리바인딩 지원
- **SaveSystem** — §8.3. 5분 주기 + 이벤트 + 종료 시. 직렬화는 sim 상태를 틱 사이에 복사한 뒤 별도 스레드에서 수행
- **AudioDirector** — sim 이벤트 구독 → 효과음, 바이옴·시간대 BGM 크로스페이드

### 5.5 폴더 구조

```
wildbond-game/
  settings.gradle.kts   build-logic/
  data/  sim/  client-core/  client-desktop/
  tools/chunk-compiler/  tools/datagen/
  assets/
    sprites/pals/<species>.png+json  sprites/player/  sprites/structures/  tilesets/
    maps/src/*.tmx  maps/chunks/*.wbc  audio/  fonts/  shaders/  ui/skin/
  docs/architecture.md  CLAUDE.md
```

### 5.6 Windows x64 전용 규칙

- 네이티브: `gdx-platform:natives-desktop` 대신 LWJGL3 natives를 **windows-x64만** 포함한다(배포 크기·의존 최소화). steamworks4j도 win64 바이너리만
- 경로: `java.nio.file.Path`만 사용, 구분자 하드코딩 금지. 세이브·로그는 `%LOCALAPPDATA%\Wildbond\` (`System.getenv("LOCALAPPDATA")`), 없으면 실행 폴더 폴백
- 파일명: NTFS는 대소문자를 구분하지 않으므로 에셋 파일명은 전부 소문자 + 언더스코어. 대소문자만 다른 파일 금지
- 콘솔 없이 실행되는 `.exe`이므로 로그는 파일로만. 크래시 시 `logs\crash-<ts>.txt` + 메시지 박스
- 창 모드: 테두리 없는 전체 화면(Borderless) 기본, DPI 스케일링은 정수 배율 카메라(§10)로 흡수
- 도구 스크립트: 하네스 등 개발용 스크립트는 **PowerShell**로 작성한다(추가 설치 불필요). bash·WSL·Git Bash를 전제하지 않는다
- JDK: 개발·빌드 모두 Java 25 LTS 하나만 쓴다. `gradle/libs.versions.toml`에 Gradle·툴 버전을 고정하고, toolchain은 25로 선언한다 (D-15)

---

## §6 sim 경계와 멀티플레이 확장 경로

지금 멀티를 만들지 않지만, 아래 4가지를 지키면 나중에 `net`·`server` 모듈을 **추가**하는 것만으로 멀티를 붙일 수 있다. 어기면 sim을 다시 써야 한다.

1. **상태 변경은 Command만** — UI·렌더·오디오는 sim을 읽기만 한다 (ArchUnit 강제)
2. **결정성 유지** — §4.3 규칙과 리플레이 테스트를 계속 통과시킨다
3. **렌더는 틱 사이 스냅을 읽는다** — 렌더러가 sim의 라이브 컴포넌트를 직접 참조하지 않고 `view.captureState()` 결과를 읽는다 (멀티에서는 이것이 네트워크 스냅샷이 됨)
4. **EntityId는 안정적 정수** — 세이브·이벤트·정렬 모두 이 id 기준 (멀티에서 netId로 그대로 사용)

멀티 추가 시 할 일(참고, 지금 하지 않음): `net` 모듈(Transport, 비트패커, 스냅샷 델타), `server` 모듈(헤드리스 sim 루프), 클라이언트 예측·보정, 인터레스트 관리, 계정·세이브 보관 서비스. v0.2 문서 §6~§7에 설계가 남아 있다.

---

## §7 Steam 연동·운영

| 기능 | 방식 |
|---|---|
| 세이브 동기화 | Steam Cloud — `saves/` 디렉터리 자동 동기화(Auto-Cloud). 충돌 시 최신 manifest 우선, 사용자에게 선택 UI |
| 도전과제·통계 | steamworks4j, sim 이벤트 구독 → 업적 트리거 |
| 크래시 | 예외 → `logs/crash-<ts>.txt` + 최근 세이브 .bak 보존. Sentry는 D-13 |
| 텔레메트리 | 없음 (D-13에서 opt-in 여부 결정) |
| 배포 | jlink 최소 런타임 + jpackage(app-image) → `Wildbond.exe` + 런타임 폴더. Steam 빌드는 Windows x64 depot 하나 |

---

## §8 데이터 · 세이브

### 8.1 정적 데이터 파이프라인

```
Google Sheets → data/tables/*.csv → datagen → data/build/generated/.../*.java (record·enum)
                                           → assets/data/tables.bin (FlatBuffers, M1)
검증: 참조 무결성, 값 범위, 배열 길이, 중복 ID, 열거형 값 → 빌드 실패 처리
```

CSV 는 **1행 헤더 + 2행 타입 행** 구조이고, `#` 로 시작하는 줄은 주석이다. 첫 컬럼은 반드시 `id`.
열거형은 `data/tables/enums.csv` 가 단일 원천이다. 타입 행 문법:

| 토큰 | 뜻 |
|---|---|
| `int` `float` `bool` `string` | 스칼라 |
| `int(0..4)` `float(-40..40)` | 값 범위 검증 |
| `int[10]` `int[10](0..4)` | `\|` 로 구분된 고정 길이 배열 |
| `enum:Element` | `enums.csv` 의 열거형. 없는 값이면 빌드 실패 |
| `ref:Skill` | 다른 표의 `id`. 대상이 없으면 빌드 실패 |
| 위 모든 토큰 + `?` | 빈 칸 허용 (Java 에서 `null` 또는 `Integer`/`Float`) |

`ElementChart.csv` 는 행=공격 / 열=방어 의 9×9 행렬로 특별 취급하며, 축이 `Element` 열거형과
일치하는지 검증한 뒤 `ElementChart.multiplier(atk, def)` 정적 클래스로 생성된다.
`LootTable.csv` 는 `monster` 컬럼으로 `Monster` 를 참조하는 여러 행이 한 몬스터의 전리품 후보다 (§3.2).

| 테이블 | 키 컬럼 (발췌) |
|---|---|
| PalSpecies | id, name_key, element1, element2, temperament, base_hp, base_atk, base_def, work[10], partner_skill, footprint(1\|2), rideable, ride_speed, capture_rate, exp_yield, sprite_atlas |
| Skill | id, element, power, cooldown, range_px, cast_ticks, hit_shape(circle\|cone\|rect\|projectile), vfx_ref |
| PassiveTrait | id, rarity, modifiers[](stat, op, value) |
| Item | id, category, max_stack, weight, rarity, equip_slot?, tool_power?, food_values?, icon |
| Recipe | id, workbench_type, inputs[], output, seconds, tech_required |
| Structure | id, footprint(w,h), rotatable, hp, materials[], work_slots[](work_type, level, offset_tile), walkable_mask, sprite |
| TechNode | id, tier, points, prerequisites[], unlocks[], ancient? |
| SpawnTable | biome, time_of_day, weather, entries[](species, weight, level_min, level_max, pack_size) |
| LootTable | id, entries[](item, weight, qty_min, qty_max) |
| Tile | id, tileset, collision(none\|solid\|water\|cliff), biome, temperature_offset, walk_speed_mult, minable? |
| ElementChart | attacker × defender → multiplier (9×9) |

### 8.2 청크 파일 (.wbc, 정적 지형)

- 32×32 타일. 레이어: ground(u16) · detail(u16) · collision(u8 비트: solid/water/cliff/edge) + 오브젝트 목록(자원 노드 초기 배치, 스폰 포인트, 트리거)
- 헤더 magic `WBC1`, 버전, 청크 좌표. zstd 압축, 청크당 ≈ 6KB → 1024² 월드 ≈ 6MB
- Tiled(.tmx) → chunk-compiler → .wbc. 존마다 `assets/maps/chunks/<zone>/<cx>_<cy>.wbc` 로 나눠 출력한다 (D-16)

### 8.3 세이브 (동적 상태)

```
saves/<slot>/world_<id>/
  manifest.json           { schema_version, game_version, world_seed, map_hash, saved_at, files[]+sha256 }
  world.fb                시간, 날씨, 보스 타이머, 글로벌 플래그
  player.fb               위치·존, 레벨·경험치, HP/MP, 소지금, 인벤토리, 장비
  chunks/<x>_<y>.fb       변경된 청크만: 자원 노드 HP·리스폰 타이머, 드롭, 설치물, 타일 오버라이드
```

- 파일 분할 → 거점 하나 손상되어도 나머지 복구
- temp → fsync → rename (원자 교체), 직전 세이브 .bak
- 몬스터는 저장하지 않음(재스폰), 보스는 처치 타이머만
- 스키마 버전 헤더 + `SaveMigrator` 체인(v1→v2→…)

### D-04 직렬화 — **FlatBuffers** [기본안]

제로카피 읽기, 스키마 진화, 정적 테이블과 같은 툴체인. 대안 Kryo(설정 거의 없음, 클래스 변경에 취약), Protobuf(제로카피 불가).

---

## §9 몬스터 AI · 스폰

### 9.1 행동 트리 (sim 내 자체 경량 BT, 외부 라이브러리 없음)

```
Root (Selector)
├─ Flee    : (passive && 위협 있음) || (timid && HP < 20%)  → 위협 반대 방향으로 직선 도주
├─ Combat  : 위협 있음 → Selector: UseSkill(offCooldown, inRange) | Chase(시야 트이면 직진, 막히면 경로)
└─ Idle    : Wander(12타일 안 walkable 타일로 경로) → Wait(3~8s)

감지(0.25s 간격): aggressive 만 12타일 시야(solid/cliff 레이캐스트)로 플레이어를 위협으로 잡는다.
passive/timid 는 시야로는 잡지 않고, 맞으면 CombatSystem 이 공격자를 위협으로 심어 준다.
위협은 18타일 밖으로 벗어나거나 죽으면 잊는다.
```

### 9.2 스폰·리스폰

- 존마다 **스폰표**(SpawnRules): 나올 종 목록, 청크당 마리 수, 플레이어와의 최소 거리, 스폰 포인트 사용 여부
- 청크가 처음 켜질 때 표대로 채우고, 이후 **죽어서 빈 자리는 리스폰 간격(기본 20초) 뒤에 다시 채운다** — 바람의나라처럼
  사냥터에 몬스터가 계속 있다
- 청크가 휴면하면 그 청크의 몬스터를 회수한다(살아 있는 개체만; 시체·드롭은 제거)
- 마을(VILLAGE)은 스폰표가 비어 있다 — 안전 지대

### 9.3 경로 탐색 (타일 그리드)

- 8방향 A* + JPS, 코너 컷 금지, 틱당 요청 상한 40, 경로 길이 상한 200
- 2×2 몬스터: 확장(dilated) 충돌 레이어로 같은 A* 사용 (M1)
- 혼잡: 같은 타일 겹침 허용 + 약한 밀어내기

### 9.4 sim 틱 예산 (존 하나 · 몬스터 100마리 · 50ms 틱)

| 항목 | 예산 | 수단 |
|---|---|---|
| 몬스터 AI | ≤ 2 ms | 감지 0.25s 분산, 시야 트이면 경로 생략 |
| 이동·충돌 | ≤ 1.5 ms | 타일 AABB, 밀어내기 |
| 경로 탐색 | ≤ 1.5 ms | JPS, 틱당 40회 |
| 전투·드롭·성장 | ≤ 1 ms | 이벤트 기반 |
| 합계 | ≤ 8 ms | 나머지는 렌더에 |

---

## §10 월드 · 렌더링 · 성능

| 영역 | 설계 |
|---|---|
| 월드 구조 | **존(Zone) 단위 분할 월드** (D-16). 존 하나가 독립된 청크 집합이고, 존 가장자리를 넘으면 이웃 존으로 전환된다. 존 하나는 최대 1024×1024 타일. 바이옴 6종 |
| 맵 제작 | 존마다 Tiled .tmx 하나 → chunk-compiler → `assets/maps/chunks/<zone>/`. 프로시저럴(D-06)을 택하면 같은 .wbc로 출력 |
| 청크 로딩 | 카메라 반경 2청크(5×5) 유지, 디코드 별도 스레드, SpriteCache 재빌드 프레임당 1개 |
| 스프라이트 | 타일 32px(Kenney 16px 2배), 플레이어 LPC 128px 프레임(4방향 걷기 9 · 베기 6), 소형 몬스터 32×32, 대형 64×64 |
| 카메라 | 가상 해상도 640×360, 정수 배율(2×/3×/4×), 픽셀 퍼펙트 |
| 조명 | FBO 1장, 광원 가산 → 멀티플라이. 낮/밤 색조 램프, 실내 지붕 마스크 |
| 날씨 | 파티클 + 전체 화면 셰이더 오버레이 |
| 풀링 | 투사체·파티클·드롭·몬스터 엔티티 풀링, 청크 휴면 시 반환 |
| 프레임 예산 (16.6ms) | sim 틱 평균 분산 ≤ 3 · 보간 1 · Y-정렬 1 · 렌더 제출 3 · UI 1 · 여유, GPU ≤ 6 |
| 메모리 | 힙 상한 512MB(`-Xmx512m`), 텍스처 ≤ 256MB |
| GC | ZGC, 핫 루프 할당 금지 |

### D-06 월드 생성 — **수제작(Tiled)** [기본안]

고정 월드로 던전·보스·랜드마크를 의도대로 배치. 대안: 프로시저럴(시드), 혼합(핵심 수제작 + 외곽 프로시저럴).

### D-16 월드 연결 — **존 분할 + 가장자리 전환** [2026-09-08 확정]

초안은 "연속된 하나의 1024² 월드를 청크 스트리밍"이었으나, **바람의나라식 맵 이동**으로 바꾼다.

- 존(Zone) = 독립된 청크 집합 + 자체 스폰 규칙. 존 사이는 **가장자리 전환**(edge link)으로 잇는다.
- 전환 시 sim 을 그 존의 TileMap 으로 다시 만든다. 존을 넘어 유지되는 것은 §8.3 의 player 상태
  (레벨·경험치·HP/MP·소지금·인벤토리)뿐이고, 몬스터·드롭 같은 존 로컬 상태는 버린다.
- 이유: 굴·마을·야외처럼 **분위기와 스폰 규칙이 뚜렷이 다른 공간**을 만들기 쉽고, 존마다 청크 수가 적어
  스트리밍·메모리 부담이 사라진다. 대가는 이음매 없는 이동감의 상실 — 의도한 교환이다.
- 청크 포맷(§8.2)·좌표 규약은 그대로다. 달라지는 것은 청크 파일이 존별 하위 폴더로 나뉘는 것뿐이다.

### D-19 장르 전환 — **팰 포획 게임 → 바람의나라식 사냥 RPG** [2026-09-09 확정]

사람의 결정: "팰은 잊어버려, 바람의나라와 비슷한 게임을 만들 거야."

- **버리는 것**: 팰(종·개체값·포획 공식·포획구 투척·파티/동행·주인 따라 싸우기), 거점(팰박스·건축·노동 스케줄러·플로우필드),
  서바이벌(허기·체온·SAN), 기술 트리. 코드·테이블·테스트 모두 삭제 — 반쪽짜리를 남기지 않는다.
- **남기는 것**: sim 뼈대(ECS·고정 틱·결정성·리플레이 테스트), 이동·충돌, 전투(스킬·데미지 공식·투사체), 몬스터 AI(BT·감지·
  경로탐색), 존 분할 월드(D-16), 청크 파이프라인, 드롭/HP/MP(2026-09-08 추가분), 아트 파이프라인.
- **새로 넣는 것**: MonsterSpecies·LootTable 표, 경험치/레벨업, 인벤토리, 리스폰, 플레이어 부활, NPC 상점(M1).
- 첫 사냥터의 몬스터는 **사슴**(passive) — 잡으면 동전 또는 사슴가죽 중 하나가 떨어진다.
- 남는 이름: 프로젝트 id `wildbond` 와 패키지명은 그대로 둔다(바꾸는 비용만 들고 얻는 게 없다).

### D-18 아트 라이선스 — **개인용 전제, 표기 의무만 지킨다** [2026-09-09 확정]

지형·소품은 Kenney(CC0), 플레이어 캐릭터는 LPC(CC-BY-SA / GPL 계열)를 쓴다.

- 처음에는 §7 의 Steam 출시를 보고 CC0 만 쓰기로 했으나, 사람이 "개인용 게임"으로 범위를 정해 완화했다.
- LPC 는 4방향 걷기·무기 베기 프레임이 갖춰진 사실상 유일한 무료 선택지였다 — CC0 팩(Kenney 등)에는
  방향별·공격 프레임이 아예 없어서 요청한 동작을 그릴 그림 자체가 없었다.
- **되돌릴 지점**: 상업 배포로 바뀌면 LPC 캐릭터를 걷어내고 CC0/구매 에셋으로 교체한다. 코드는 시트 좌표만
  바꾸면 되도록 `PlaceholderSprites` 한 곳에 몰아 두었다. 저작자 표기는 assets/CREDITS.md.

### D-17 전투 조작 — **스페이스바 근접 공격** [2026-09-08 확정]

단계 6 은 마우스 좌/우클릭 조준이었다. 바라보는 방향으로 **스페이스바** 근접 공격을 기본으로 바꾼다
(조준각은 클라이언트가 들고 있는 facing 에서 만들어 `Command.UseSkill` 로 넘긴다 — sim 규약은 그대로).
원거리 스킬은 마우스 우클릭으로 남는다.

---

## §11 빌드 · 검증 (로컬)

단일 Gradle 멀티프로젝트 `wildbond-game` (Gradle 9.x, Java 25 toolchain). 모든 검증은 루트의 **테스트 하네스** `harness.ps1`(래퍼 `harness.cmd`)를 통한다 (`CLAUDE.md` "테스트 하네스" 참고).

| 스테이지 | 내용 |
|---|---|
| spotless | google-java-format 검사 |
| build | 컴파일 + 전체 테스트(sim 규칙·리플레이 결정성·ArchUnit) |
| sim:bench | 헤드리스 sim 벤치: 평균 틱 ≤ 8ms (§9.4) — 단계 3 이후 |
| 리포트 | `reports\harness\latest.md` (JUnit 집계, 스테이지별 결과) |

버전 관리(git)와 CI(GitLab, Trivy, SBOM)는 현재 범위 밖이며 나중에 도입한다. 도입 시 CI는 로컬과 같은 하네스를 그대로 실행한다.

---

## §12 로드맵

| 마일스톤 | 목표 |
|---|---|
| **M0 사냥 루프** (v0.5 시작점) | 마을 → 사냥터 → 사슴 사냥 → 동전/가죽 드롭 → 획득 → 경험치·레벨업. 존 3개, 리스폰, 플레이어 부활, 인벤토리 표시 |
| M1 마을 경제 | NPC 상점(사고팔기), 장비(무기·갑옷)와 스탯 보정, 소모품 사용(물약), 세이브/로드, 두 번째 던전과 몬스터 3종 추가 |
| M2 콘텐츠 | 원거리/마법 스킬 트리, 퀘스트(NPC 대화), 던전 5개, 몬스터 15종, 보스, 낮/밤 |
| M3 마무리 | 밸런스, 사운드, 타이틀·옵션 화면, jpackage 로 `Wildbond.exe` |

멀티플레이는 §6 확장 경로에 따라 필요해지면 별도로 검토한다.

---

## §13 결정 필요 목록

| ID | 질문 | 기본안 | 영향 |
|---|---|---|---|
| D-01 | 게임 프레임워크 | LibGDX (Java) | 전체 |
| D-02 | 시점 | 탑다운 | §3, §5, §9 |
| D-04 | 세이브 직렬화 | FlatBuffers (대안 Kryo) | §8 |
| D-06 | 월드 생성 | 수제작 (Tiled) | §8, §10 |
| D-09 | ~~팰 교배~~ (D-19 로 폐기) | — | — |
| D-11 | 아트 — 픽셀아트 32px / HD 2D | 픽셀아트 32px | §10 |
| D-12 | UI — Scene2D.ui / 자체 즉시모드 | Scene2D.ui | §5 |
| D-13 | 크래시·텔레메트리 수집 (Sentry 등, opt-in) | 로컬 로그만 | §7 |
| D-14 | 세이브 슬롯 수 / Steam Cloud 사용 여부 | 슬롯 3, Cloud 사용 | §7, §8 |
| D-15 | JDK 버전 | **Java 25 LTS** (개발 PC 기설치분 사용). Gradle 9.x 필요 | §1, §5.6, §11 |

v0.2에서 제거된 결정: D-03 트랜스포트, D-05 PvP, D-07 동시 인원, D-08 공식 서버, D-10 자체 계정 — 멀티 추가 시 다시 논의.
