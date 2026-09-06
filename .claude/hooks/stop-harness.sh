#!/usr/bin/env bash
# Claude Code Stop 훅: 에이전트가 턴을 끝내려 할 때 하네스(quick)를 돌린다.
# 실패하면 exit 2 → Claude 는 멈추지 못하고 stderr 의 리포트를 보고 고쳐야 한다.
INPUT="$(cat)"
# 이미 훅 때문에 이어서 작업 중이면 무한 루프 방지를 위해 통과
if printf '%s' "$INPUT" | grep -q '"stop_hook_active": *true'; then exit 0; fi
cd "$CLAUDE_PROJECT_DIR" 2>/dev/null || cd "$(dirname "$0")/../.."
OUT="$(bash ./harness.sh --quick 2>&1)"; CODE=$?
case $CODE in
  0) exit 0 ;;
  2) exit 0 ;;   # gradlew 없음 등 실행 불가 — 막지 않음
  *) printf '%s\n\n[stop-hook] 하네스 실패. troubleshooting.md 에 기록하고 원인을 고친 뒤 ./harness.sh 를 다시 통과시켜라.\n' "$OUT" >&2; exit 2 ;;
esac
