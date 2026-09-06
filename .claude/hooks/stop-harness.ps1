# Claude Code Stop 훅: 에이전트가 턴을 끝내려 할 때 하네스(-Quick)를 돌린다.
# 포맷은 자동으로 고치고(spotlessApply), 컴파일·테스트 실패만 에이전트를 붙잡는다.
# 실패하면 exit 2 -> Claude 는 멈추지 못하고 stderr 의 리포트를 보고 고쳐야 한다.
$ErrorActionPreference = 'Continue'
$input_json = [Console]::In.ReadToEnd()
if ($input_json -match '"stop_hook_active"\s*:\s*true') { exit 0 }

$root = if ($env:CLAUDE_PROJECT_DIR) { $env:CLAUDE_PROJECT_DIR } else { Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
$harness = Join-Path $root 'harness.ps1'
if (-not (Test-Path -LiteralPath $harness)) { exit 0 }

$out = & powershell -NoProfile -ExecutionPolicy Bypass -File $harness -Quick 2>&1
$code = $LASTEXITCODE
if ($code -eq 0 -or $code -eq 2) { exit 0 }

$msg = ($out | Out-String) + "`n[stop-hook] 하네스 실패. troubleshooting.md 에 기록하고 원인을 고친 뒤 harness 를 다시 통과시켜라.`n"
[Console]::Error.Write($msg)
exit 2
