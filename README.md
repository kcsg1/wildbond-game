# Wildbond

2D 탑다운 사냥 RPG — 마을에서 시작해 사냥터로 나가 몬스터를 잡고 전리품·경험치를 모은다 (싱글플레이, Java 25 + LibGDX).

- 설계: `docs/architecture.md` (단일 원천)
- 에이전트 규칙: `CLAUDE.md`
- 모듈: `data` · `sim` · `client-core` · `client-desktop` · `tools/*` — 단계 1(Gradle 뼈대)에서 생성

## 시작

```
./gradlew build                 # 전체 빌드 + 테스트
./gradlew :client-desktop:run   # 게임 실행 (F3: 디버그 오버레이)
```
