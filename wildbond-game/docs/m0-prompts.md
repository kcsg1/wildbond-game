# Wildbond M0 구현 프롬프트

- 대상: M0 수직 슬라이스 (약 3주, 싱글플레이)
- 전제: `docs/architecture.md` v0.3, `CLAUDE.md`
- 이 파일의 각 프롬프트는 Claude Code(또는 다른 코딩 에이전트)에 그대로 붙여 넣는다.

## 사용 방법

1. 한 단계 = 한 세션. 프롬프트를 붙여 넣고, 끝나면 **검증 명령**을 직접 실행해 확인한 뒤 다음 단계로 간다.
   - 에이전트는 `CLAUDE.md` "작업 절차"에 따라 시작 전 `troubleshooting.md`를 읽고, 끝나면 `./harness.sh` 전체 모드를 통과시키고 `plan.md`에 히스토리를, 실패는 `troubleshooting.md`에 기록해야 한다. 기록이나 하네스 통과가 빠져 있으면 다음 단계로 가기 전에 요구한다.
2. 에이전트가 아키텍처와 다른 선택을 하면 "docs/architecture.md §n에 맞춰라"로 되돌린다.
3. 단계를 건너뛰지 않는다. 뒤 단계는 앞 단계의 테스트가 통과한 상태를 전제한다.

| 단계 | 내용 | 예상 |
|---|---|---|
| 0 | 사전 준비 (직접) | 1시간 |
| 1 | Gradle 멀티프로젝트 뼈대 + ArchUnit | 1일 |
| 2 | data — 정적 테이블 코드젠 | 1~2일 |
| 3 | sim 코어 — ECS·고정 틱·이동/충돌·결정성 | 4~5일 |
| 4 | 청크 포맷(.wbc) + Tiled 컴파일러 | 2일 |
| 5 | client — LibGDX 창·게임 루프·청크 렌더·입력→Command | 4일 |
| 6 | 전투 — 스킬·히트·데미지 | 3일 |
| 7 | 팰 — 스폰·야생 AI·포획·동행 | 5일 |

---

## 단계 0 — 사전 준비 (직접 수행)

이미 완료된 것: 프로젝트 폴더, `docs/architecture.md`, `CLAUDE.md`, `README.md`, `plan.md`, `troubleshooting.md`, 테스트 하네스, `assets/`·`data/tables/` 디렉터리.

남은 것:

```powershell
# JDK 21 (Temurin), Tiled, Aseprite(선택)
winget install EclipseAdoptium.Temurin.21.JDK
winget install Tiled.Tiled
```

**검증**: `java -version`이 21. (버전 관리·CI는 나중에 도입한다.)

---

## 단계 1 — Gradle 멀티프로젝트 뼈대 + ArchUnit

모듈 경계와 코딩 규칙을 코드로 먼저 고정한다. 이후 모든 작업이 이 위에 쌓인다.

### Prompt 1

```
docs/architecture.md 와 CLAUDE.md 를 읽고, 이 레포(wildbond-game)의 Gradle 멀티프로젝트 뼈대를 만들어줘.

요구사항:
- Gradle 8.x Kotlin DSL, version catalog(gradle/libs.versions.toml), Java 21 toolchain, Gradle wrapper 포함
- 서브프로젝트: data, sim, client-core, client-desktop, tools/chunk-compiler, tools/datagen
- 의존 방향은 architecture.md §5.1 표 그대로. sim 은 data 만 의존하고 com.badlogic.* 를 절대 참조하지 않음
- 공통 convention plugin(build-logic)으로 JUnit 5, AssertJ, ArchUnit, -Xlint:all, spotless(google-java-format) 적용
- sim 모듈에 ArchUnit 테스트를 넣어 위 격리 규칙을 빌드에서 강제
- 각 모듈에 패키지 com.wildbond.<module> 과 빈 클래스 하나씩, 컴파일만 되게
- .editorconfig 추가. 기존 README.md 는 유지하되 필요하면 보강
- 루트의 harness.sh 가 실제로 돌게 만들기: spotlessCheck 와 build 태스크가 존재해야 하고, 모든 모듈의 test 태스크가
  JUnit XML 을 build/test-results/test/ 에 남겨야 함. 완료 조건은 ./harness.sh 가 종료 코드 0 으로 끝나는 것

완료 후 ./harness.sh 를 실행해 통과를 확인하고 리포트를 요약해줘.
```

### 검증

```
./harness.sh                       # 종료 코드 0, reports/harness/latest.md 생성
./gradlew :sim:test --tests "*ArchitectureTest*"
```

---

## 단계 2 — data 모듈: 정적 테이블 코드젠

CSV → Java record. M0에서는 FlatBuffers 없이 CSV를 런타임 로드한다(바이너리는 M1).

### Prompt 2

```
architecture.md §8.1 의 정적 데이터 파이프라인 중 M0 범위를 구현해줘.

1. data/tables/ 에 CSV 5개를 샘플 데이터와 함께 만들어줘 (컬럼은 §8.1 표 기준):
   - PalSpecies.csv : 종 3개 (초원 소형 1×1, 숲 소형 1×1, 대형 2×2 하나)
   - Skill.csv      : 근접 1개(hit_shape=rect), 원거리 1개(projectile), 팰 스킬 2개
   - Item.csv       : 포획구 3단계, 나무, 돌, 회복약
   - Tile.csv       : 풀, 흙, 물(collision=water), 절벽(collision=cliff), 모래
   - ElementChart.csv : 9×9 (무/불/물/풀/전기/얼음/땅/어둠/용)
2. tools/datagen : CSV 헤더와 타입 주석 행(2행)을 읽어 data/build/generated/sources 에 Java record 와
   로더(GameData.load(Path)) 를 생성하는 Java 프로그램. Gradle task `generateData` 로 실행되며 data:compileJava 가 의존.
3. 생성된 record 는 불변, id 는 int, 참조 컬럼(예: Skill.element)은 enum.
4. 검증기: 참조 무결성(현재 있는 참조만), 중복 id, 값 범위. 실패 시 빌드 실패.
5. data 모듈 테스트: 샘플 CSV 로드 → 종 3개, ElementChart 가 대칭이 아님을 확인.

생성 코드는 소스 트리가 아니라 build 디렉터리에 두고, IDE 인식을 위해 sourceSet 에 추가해줘.
```

### 검증

```
./gradlew :data:generateData :data:test
```

---

## 단계 3 — sim 코어: ECS · 고정 틱 · 이동/충돌 · 결정성

가장 중요한 모듈. 렌더링 없이 JUnit만으로 완성한다.

### Prompt 3

```
architecture.md §4 의 wildbond-sim 을 M0 범위로 구현해줘. LibGDX 의존 없이 순수 Java, Artemis-odb 사용.

구현:
- Sim 클래스: 생성자(GameData, TileMap, long seed), step(int tick, List<Command>) 하나의 진입점, 50ms 고정 틱
- Command 는 sealed interface: MoveInput(entityId, dir8, run), UseSkill(entityId, skillId, aimAngle), SpawnPlayer(x, y)
- Components: Position, Velocity, Collider(AABB w,h, layer), Health, EntityId, Player
- Systems 를 §4.1 순서대로 등록하되 M0 는 CommandApply → Movement → CombatSystem(빈 껍데기) → EventFlush 만
- MovementSystem: Velocity×dt, 타일 충돌은 AABB 스윕(x 축 먼저, y 축 다음), solid/cliff/water 는 통과 불가, 엔티티 간 약한 밀어내기
- TileMap 인터페이스 + 테스트용 ArrayTileMap(폭,높이, byte[] collision)
- Rng: xoshiro256** 직접 구현, 스트림 3개(combat/spawn/loot), 시드 고정
- 결정성 규칙: float + StrictMath, 엔티티 순회는 EntityId 오름차순, HashMap 순회 금지
- 읽기 전용 뷰: SimView 인터페이스(엔티티 위치·종류·HP 조회)만 외부에 노출. 컴포넌트 매퍼는 패키지 private.
  ArchUnit 으로 sim 밖에서 컴포넌트 쓰기 접근이 없음을 강제
- 각 틱 끝에 stateHash() (64bit 혼합 해시) 제공
- 도메인 이벤트: EventBus 에 EntitySpawned, EntityMoved(위치 변경 시), Damaged

테스트 (sim/src/test):
- 벽을 향해 10틱 이동해도 solid 타일을 통과하지 못함
- 대각 이동 시 코너에 걸리면 한 축만 미끄러짐
- 같은 시드·같은 명령 로그를 두 Sim 인스턴스에 넣으면 1000틱 후 stateHash 동일 (리플레이 결정성)
- 다른 시드면 해시가 다름
- 벤치: Gradle 태스크 `:sim:bench` (JavaExec) — 엔티티 500개 1000틱을 돌려 평균/최대 틱 시간을 출력하고,
  평균 틱 > 8ms 면 실패(exit 1). 하네스가 이 태스크를 자동으로 포함한다. 임계값은 architecture.md §9.4 예산

sim 의 핫 루프에서 객체 할당이 없도록 IntBag/컴포넌트 매퍼를 사용해줘.
```

### 검증

```
./harness.sh          # build + sim:bench 포함. 리플레이 테스트 통과, 벤치 평균 틱 ≤ 8ms
```

---

## 단계 4 — 청크 포맷(.wbc) + Tiled 컴파일러

테스트 청크를 Tiled로 만들고, sim이 그 충돌 레이어를 읽게 한다.

### Prompt 4

```
architecture.md §8.2 의 청크 파일 포맷과 컴파일러를 구현해줘.

1. data 모듈에 ChunkFormat: 32×32 타일, 레이어 ground(u16) / detail(u16) / collision(u8 비트: solid=1, water=2, cliff=4, edge=8),
   오브젝트 목록(type, tileX, tileY, props). 헤더에 magic "WBC1", 버전, 청크 좌표. zstd 압축(zstd-jni).
   ChunkReader / ChunkWriter 와 라운드트립 테스트.
2. tools/chunk-compiler: Tiled .tmx(CSV 인코딩) 를 읽어 32×32 단위로 잘라 assets/maps/chunks/<x>_<y>.wbc 로 출력.
   collision 은 Tile.csv 의 tileId→collision 매핑으로 계산. Tiled 오브젝트 레이어 → 오브젝트 목록.
   Gradle task `compileChunks`.
3. assets/maps/src/test_island.tmx : 64×64 타일(청크 4개) 샘플. 풀밭 + 연못(water) + 절벽 + 돌 자원 노드 오브젝트 3개.
   tileset 은 assets/tilesets/placeholder.png (32px 격자, 색상 블록으로 프로그램이 생성).
4. sim 의 TileMap 구현 ChunkTileMap: 청크 로더(Function<ChunkCoord, Chunk>)를 받아 lazy 로드, getCollision(tx, ty).
5. 테스트: 컴파일한 청크를 ChunkTileMap 으로 읽어 연못 타일이 water 로 판정되는지.
```

### 검증

```
./gradlew :tools:chunk-compiler:compileChunks
./harness.sh
ls assets/maps/chunks/   # 0_0.wbc 0_1.wbc 1_0.wbc 1_1.wbc
```

---

## 단계 5 — client: LibGDX 창 · 게임 루프 · 청크 렌더 · 입력→Command

처음으로 화면이 뜬다. sim은 메인 스레드에서 고정 틱으로 돌고, 렌더는 틱 사이를 보간한다.

### Prompt 5

```
architecture.md §5 의 클라이언트를 M0 범위로 구현해줘. LibGDX 1.13, LWJGL3.

client-desktop:
- DesktopLauncher: 1280×720 창, vsync, 제목 "Wildbond". jlink/jpackage 는 M3.
client-core:
- WildbondGame(Game) 과 Screen 스택: BootScreen → PlayScreen
- GameLoop: §5.2 그대로. 고정 틱 50ms 누적기, 메인 스레드에서 Sim.step 동기 호출, deltaTime 상한 0.25s, alpha 보간
- ViewState: 틱 끝마다 SimView 에서 엔티티 위치·종류·HP 를 prev/cur 두 벌로 복사(객체 재사용).
  렌더러는 이것만 읽고 sim 컴포넌트를 직접 참조하지 않음 (§6 규칙 3)
- InputMapper: WASD/화살표 → MoveInput(dir8), Shift 달리기. 틱마다 drain 해서 Command 리스트로 전달
- 카메라: 가상 해상도 640×360, 정수 배율(창 크기에 맞춰 2×/3×), 플레이어 보간 위치 추적, 정수 픽셀 스냅
- ChunkRenderer: 카메라 반경 2청크 로드(별도 스레드 디코드 → 렌더 스레드 GL 업로드), 청크당 SpriteCache, placeholder 타일셋
- EntityRenderer: ViewState 를 Y-정렬 후 그리기. 플레이어는 32×48 색 블록 스프라이트(임시), prev→cur 를 alpha 로 보간
- DebugOverlay(F3): FPS, 틱 시간(평균/최대), 틱 해시, 드로우콜, 로드된 청크 수
- 창 종료 시 GameLoop 정상 종료

수용 기준:
- test_island 위를 걸어 다니고 연못·절벽에 막힘
- 60fps 에서 이동이 떨리지 않음(보간 확인), 틱 시간 ≤ 2ms
- 화면 전체 드로우콜 ≤ 10
- ArchUnit: client-core 가 sim 의 컴포넌트 클래스를 참조하지 않음
```

### 검증

```
./harness.sh                    # client-core ArchUnit 포함
./gradlew :client-desktop:run   # 수용 기준 수동 확인
```

---

## 단계 6 — 전투: 스킬 · 히트 판정 · 데미지 공식

sim에서 규칙과 테스트를 먼저, 클라이언트 연출은 최소로.

### Prompt 6

```
architecture.md §3.2 데미지 규칙과 §4.1 CombatSystem 을 구현해줘.

sim:
- Components: Skills(cooldownTicks[]), StatusEffects, Element, Stats(atk, def, level)
- UseSkill 명령 → CombatSystem: 쿨다운 확인, hit_shape 별 판정(rect: 바라보는 방향 앞 w×h / circle / cone / projectile 은 Projectile 엔티티 생성 후 이동·충돌)
- 데미지 공식 §3.2 그대로, ElementChart 참조, 치명타·랜덤은 combat Rng 스트림
- Damaged, Died 이벤트. HP 0 → Dead 컴포넌트, 5초 후 제거
- 테스트: 속성 상성 2.0/0.5 적용, 방어력 200 일 때 데미지 절반, 쿨다운 중 재사용 무시, 투사체가 solid 타일에 막힘, 같은 시드로 치명타 시퀀스 재현

client:
- 마우스 좌클릭 → UseSkill(근접), 우클릭 → 원거리. aim 은 마우스 방향
- Damaged 이벤트 → 떠오르는 숫자, 타격 플래시 3프레임
- 임시 허수아비 엔티티(Health 100, 월드 시작 시 spawn)로 확인
```

### 검증

```
./harness.sh
./gradlew :client-desktop:run
```

---

## 단계 7 — 팰: 스폰 · 야생 AI · 포획 · 동행

M0의 마지막 조각. 팰 1종이 돌아다니고, 싸우고, 잡히고, 따라온다.

### Prompt 7

```
architecture.md §3.2 포획 규칙, §9.1 야생 BT, §9.3 경로 탐색을 M0 범위로 구현해줘.

sim:
- PalData 컴포넌트(speciesId, level, hp, san, iv[]), Owner, Party(slot), Brain(state, timers), Path
- SpawnSystem: 청크 활성화 시 임시 스폰 규칙(청크당 종 1, 3마리, 플레이어에서 24타일 이상 떨어진 walkable 타일), 청크 휴면 시 회수
- 자체 경량 BT(Selector/Sequence/Condition/Action ~10 노드, 외부 라이브러리 없음). 야생 트리는 §9.1: Flee / Combat(Chase+UseSkill) / Idle(Wander→Wait)
- 감지: 반경 12타일 + solid/cliff 레이캐스트(Bresenham)
- Pathfinder: 8방향 A* + JPS, 코너 컷 금지, 경로 길이 상한 200, 틱당 요청 상한 40. PathFollowSystem
- CaptureSystem: ThrowSphere 명령 → 가상 높이 z 를 가진 포물선 투사체, z=0 낙하 타일과 팰 AABB 겹침 → §3.2 공식으로 판정.
  성공: Owner 설정, Party 빈 슬롯에 추가, PalCaptured 이벤트(흔들림 횟수 1~3 포함). 실패: PalCaptureFailed, 팰은 Combat 상태로
- 파티 팰 AI: FollowOwner(3~6타일, 떨어지면 경로 요청) / 주인이 공격한 대상 Combat
- 테스트: HP 5% + 3단계 포획구 1000회 시뮬 → 성공률이 공식 기대값 ±3%p, 레벨 차 페널티 적용,
  야생 팰이 벽 너머 플레이어를 감지하지 못함, A* 가 연못을 우회, JPS 결과가 일반 A* 와 같은 길이

client:
- 팰 스프라이트(임시 색 블록 32×32), 숫자 키 1 → 포획구 던지기(마우스 방향, 고정 거리 6타일), 투사체 그림자 + y-오프셋 연출
- PalCaptured → 포획구가 흔들림 횟수만큼 흔들린 뒤 팰 사라짐, 실패 시 팰 튀어나옴
- HUD: 파티 슬롯 5칸(종 이름, HP 바)

수용 기준 (M0 완료 조건):
- 야생 팰 3마리가 배회하다 접근하면 공격하고, 때려서 HP 를 깎은 뒤 포획구로 잡으면 따라다니며 대신 싸움
- 팰 24마리 상황에서 sim 틱 ≤ 3ms
- 전체 sim 테스트가 결정성 리플레이 포함 통과
```

### 검증

```
./harness.sh
./gradlew :client-desktop:run
```

---

M0 완료 후 M1(거점 루프) 프롬프트를 이어서 작성한다. 버전 관리(git)와 CI는 나중에 도입한다. 멀티플레이는 architecture.md §6 확장 경로에 따라 나중에 net/server 모듈을 추가하는 방식으로 붙인다.
