# plan.md — 작업 히스토리

가장 최근 항목이 **위**에 온다. 형식은 `CLAUDE.md` "작업 절차" 참고. 단계 번호는 `docs/m0-prompts.md` 기준.

## 현재 상태

- 마일스톤: M0 수직 슬라이스
- 다음 단계: 1 — Gradle 멀티프로젝트 뼈대 + ArchUnit
- 열린 결정: 없음 (architecture.md §13 기본안 적용 중)

---

## 2026-09-03 단계 0 — 사전 준비
- 목표: 프로젝트 폴더와 설계 문서, 에이전트 지침 준비
- 한 일: `docs/architecture.md`(v0.3), `CLAUDE.md`, `README.md`, `assets/`·`data/tables/` 디렉터리, `docs/m0-prompts.md`, `plan.md`, `troubleshooting.md`, 테스트 하네스(`harness.sh`, `harness.cmd`, `.claude/settings.json` Stop 훅)
- 검증: 파일 존재 확인. `./harness.sh` → 종료 코드 2 (gradlew 없음, 단계 1 전이므로 정상)
- 결정: 버전 관리(git)·CI는 당분간 도입하지 않고 로컬로만 작업. 필요해지면 추가
- 남은 일 / 다음 단계: JDK 21 설치 확인 → 단계 1
