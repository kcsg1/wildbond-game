#!/usr/bin/env bash
# Wildbond 테스트 하네스
# 모든 작업 단계에서 이 스크립트를 실행한다. 통과하지 않으면 "완료"가 아니다.
#
#   ./harness.sh            전체: 포맷 검사 → 빌드 → 전체 테스트(ArchUnit 포함) → sim 벤치 → 리포트
#   ./harness.sh --quick    빠른: 포맷 검사 → 컴파일 → 변경 모듈 테스트만 (벤치 생략)
#   ./harness.sh --module sim   특정 모듈 테스트만
#   HARNESS_SKIP=1          훅에서 강제 생략 (긴급 시에만)
#
# 종료 코드: 0 통과 / 1 실패 / 2 하네스 실행 불가(gradlew 없음 등 — Gradle 뼈대 전 단계)
# 리포트: reports/harness/latest.md (+ reports/harness/<timestamp>.md)

set -u
cd "$(dirname "$0")"

MODE="full"
MODULE=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --quick) MODE="quick" ;;
    --module) MODULE="$2"; shift ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
  shift
done

if [[ "${HARNESS_SKIP:-0}" == "1" ]]; then
  echo "[harness] HARNESS_SKIP=1 — 생략됨 (plan.md 에 사유를 적을 것)"
  exit 0
fi

if [[ ! -f ./gradlew ]]; then
  echo "[harness] gradlew 없음 — 단계 1(Gradle 뼈대) 전이므로 실행 불가. 단계 1 완료 후 다시 실행."
  exit 2
fi

GRADLEW=./gradlew
[[ "$(uname -s 2>/dev/null)" == MINGW* || "$(uname -s 2>/dev/null)" == MSYS* ]] && GRADLEW="./gradlew.bat"

mkdir -p reports/harness
TS="$(date +%Y%m%d-%H%M%S)"
REPORT="reports/harness/${TS}.md"
LOG="reports/harness/${TS}.log"
START=$(date +%s)
STATUS=0
declare -a ROWS

run_stage() {
  local name="$1"; shift
  local s=$(date +%s)
  echo "[harness] ▶ $name: $*"
  if "$@" >>"$LOG" 2>&1; then
    ROWS+=("| $name | ✅ 통과 | $(( $(date +%s) - s ))s |")
  else
    ROWS+=("| $name | ❌ 실패 | $(( $(date +%s) - s ))s |")
    STATUS=1
    echo "[harness] ✖ $name 실패 — 로그: $LOG (마지막 40줄)"
    tail -n 40 "$LOG"
    return 1
  fi
}

# 1. 포맷
run_stage "spotless" $GRADLEW spotlessCheck -q || true

# 2. 컴파일 + 테스트
if [[ -n "$MODULE" ]]; then
  run_stage "test:$MODULE" $GRADLEW ":$MODULE:test" -q || true
elif [[ "$MODE" == "quick" ]]; then
  run_stage "compile" $GRADLEW compileJava compileTestJava -q || true
  run_stage "test(quick)" $GRADLEW test -q || true
else
  run_stage "build" $GRADLEW build -q || true
fi

# 3. sim 벤치 (전체 모드, 태스크가 있을 때만) — 단계 3 이후 :sim:bench 가 생기면 자동 포함
if [[ "$MODE" == "full" && -z "$MODULE" ]]; then
  if $GRADLEW -q tasks --all 2>/dev/null | grep -q "^sim:bench"; then
    run_stage "sim:bench" $GRADLEW :sim:bench -q || true
  else
    ROWS+=("| sim:bench | ⏭ 없음 (단계 3 이후) | - |")
  fi
fi

# 4. 테스트 집계 (JUnit XML)
TOTAL=0; FAILED=0; ERRORS=0; SKIPPED=0
while IFS= read -r f; do
  t=$(grep -o 'tests="[0-9]*"' "$f" | head -1 | grep -o '[0-9]*'); TOTAL=$((TOTAL + ${t:-0}))
  x=$(grep -o 'failures="[0-9]*"' "$f" | head -1 | grep -o '[0-9]*'); FAILED=$((FAILED + ${x:-0}))
  e=$(grep -o 'errors="[0-9]*"' "$f" | head -1 | grep -o '[0-9]*'); ERRORS=$((ERRORS + ${e:-0}))
  k=$(grep -o 'skipped="[0-9]*"' "$f" | head -1 | grep -o '[0-9]*'); SKIPPED=$((SKIPPED + ${k:-0}))
done < <(find . -path '*/build/test-results/test/*.xml' 2>/dev/null)

FAILED_TESTS=$(grep -l '<failure\|<error' $(find . -path '*/build/test-results/test/*.xml' 2>/dev/null) /dev/null 2>/dev/null \
  | sed 's#.*/TEST-##; s#\.xml##' | sort -u | head -20)

[[ $((FAILED + ERRORS)) -gt 0 ]] && STATUS=1

DUR=$(( $(date +%s) - START ))
RESULT_WORD="통과"; [[ $STATUS -ne 0 ]] && RESULT_WORD="실패"

{
  echo "# 하네스 리포트 — $TS"
  echo
  echo "- 결과: **$RESULT_WORD** (모드: $MODE${MODULE:+, 모듈: $MODULE}, ${DUR}s)"
  echo "- 테스트: 총 $TOTAL / 실패 $FAILED / 오류 $ERRORS / 스킵 $SKIPPED"
  echo
  echo "| 스테이지 | 결과 | 시간 |"
  echo "|---|---|---|"
  printf '%s\n' "${ROWS[@]}"
  if [[ -n "$FAILED_TESTS" ]]; then
    echo
    echo "## 실패한 테스트 클래스"
    echo "$FAILED_TESTS" | sed 's/^/- /'
  fi
  echo
  echo "로그: $LOG"
} > "$REPORT"
cp "$REPORT" reports/harness/latest.md

echo
cat "$REPORT"
echo
if [[ $STATUS -ne 0 ]]; then
  echo "[harness] ❌ 실패. troubleshooting.md 에 기록하고 원인을 고친 뒤 다시 실행할 것."
else
  echo "[harness] ✅ 통과. plan.md 의 '검증' 줄에 위 요약을 옮겨 적을 것."
fi
exit $STATUS
