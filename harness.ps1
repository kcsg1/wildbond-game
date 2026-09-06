<#
Wildbond 테스트 하네스 (Windows / PowerShell)
모든 작업 단계에서 이 스크립트를 실행한다. 통과하지 않으면 "완료"가 아니다.

  .\harness.ps1              전체: spotless -> build(컴파일+전체 테스트+ArchUnit) -> sim:bench -> 리포트
  .\harness.ps1 -Quick       빠른: 포맷 -> 컴파일 -> 테스트 (벤치 생략). 작업 중 반복 실행용
  .\harness.ps1 -Module sim  한 모듈만
  .\harness.ps1 -CheckFormat 포맷을 고치지 말고 검사만 한다 (기본은 spotlessApply 로 고침)
  $env:HARNESS_SKIP=1        훅에서 강제 생략 (긴급 시에만)

종료 코드: 0 통과 / 1 실패 / 2 실행 불가(gradlew.bat 없음 - Gradle 뼈대 전 단계)
리포트: reports\harness\latest.md (+ reports\harness\<timestamp>.md)
#>
[CmdletBinding()]
param(
  [switch]$Quick,
  [string]$Module = "",
  [switch]$CheckFormat
)

$ErrorActionPreference = 'Continue'
Set-Location -LiteralPath $PSScriptRoot
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

if ($env:HARNESS_SKIP -eq '1') {
  Write-Host "[harness] HARNESS_SKIP=1 - 생략됨 (plan.md 에 사유를 적을 것)"
  exit 0
}

# Gradle 홈(배포판·의존성 캐시)을 프로젝트 상위 폴더에 둔다.
# 이 경로는 백신 예외 처리된 트리이며, %USERPROFILE%\.gradle 에서 배포판 zip 이
# 다운로드 직후 격리되는 문제를 피한다 (troubleshooting.md T-002).
# 셸에 GRADLE_USER_HOME 이 이미 있으면 그것을 존중한다.
if (-not $env:GRADLE_USER_HOME) {
  $gradleHome = Join-Path (Split-Path -Parent $PSScriptRoot) '.gradle-home'
  New-Item -ItemType Directory -Force -Path $gradleHome | Out-Null
  $env:GRADLE_USER_HOME = $gradleHome
}
Write-Host "[harness] GRADLE_USER_HOME = $env:GRADLE_USER_HOME"

$gradlew = Join-Path $PSScriptRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradlew)) {
  Write-Host "[harness] gradlew.bat 없음 - 단계 1(Gradle 뼈대) 전이므로 실행 불가. 단계 1 완료 후 다시 실행."
  exit 2
}

$mode = if ($Module) { "module:$Module" } elseif ($Quick) { 'quick' } else { 'full' }
$reportDir = Join-Path $PSScriptRoot 'reports\harness'
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
$ts = Get-Date -Format 'yyyyMMdd-HHmmss'
$report = Join-Path $reportDir "$ts.md"
$log = Join-Path $reportDir "$ts.log"
$started = Get-Date
$status = 0
$rows = New-Object System.Collections.Generic.List[string]

function Invoke-Stage {
  param([string]$Name, [string[]]$GradleArgs)
  $s = Get-Date
  Write-Host "[harness] > $Name : gradlew $($GradleArgs -join ' ')"
  & $gradlew @GradleArgs *>> $log
  $code = $LASTEXITCODE
  $secs = [int]((Get-Date) - $s).TotalSeconds
  if ($code -eq 0) {
    $script:rows.Add("| $Name | 통과 | ${secs}s |")
  } else {
    $script:rows.Add("| $Name | 실패 | ${secs}s |")
    $script:status = 1
    Write-Host "[harness] x $Name 실패 - 로그: $log (마지막 40줄)"
    if (Test-Path -LiteralPath $log) { Get-Content -LiteralPath $log -Tail 40 | Write-Host }
  }
}

# 1. 포맷 — 기본은 "고친다".
# 포맷은 논쟁하지 않는다(CLAUDE.md). 포맷 차이로 전체 검증이 멈추는 것은 낭비이므로
# 검사가 아니라 적용이 기본이고, -CheckFormat 을 줄 때만 검사만 한다.
if ($CheckFormat) {
  Invoke-Stage 'spotlessCheck' @('spotlessCheck', '-q')
} else {
  Invoke-Stage 'spotlessApply' @('spotlessApply', '-q')
}

# 2. 컴파일 + 테스트
if ($Module) {
  Invoke-Stage "test:$Module" @(":${Module}:test", '-q')
} elseif ($Quick) {
  Invoke-Stage 'compile' @('compileJava', 'compileTestJava', '-q')
  Invoke-Stage 'test(quick)' @('test', '-q')
} else {
  Invoke-Stage 'build' @('build', '-q')
}

# 3. sim 벤치 (전체 모드, 태스크가 존재할 때만) - 단계 3 이후 :sim:bench 가 생기면 자동 포함
if ($mode -eq 'full') {
  $tasks = & $gradlew '-q' 'tasks' '--all' 2>$null
  if ($tasks -and ($tasks | Select-String -Pattern '^sim:bench' -Quiet)) {
    Invoke-Stage 'sim:bench' @(':sim:bench', '-q')
  } else {
    $rows.Add('| sim:bench | 없음 (단계 3 이후) | - |')
  }
}

# 4. 테스트 집계 (JUnit XML)
$total = 0; $failed = 0; $errors = 0; $skipped = 0
$failedClasses = New-Object System.Collections.Generic.List[string]
$xmlFiles = Get-ChildItem -Path $PSScriptRoot -Recurse -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match '\\build\\test-results\\test\\' }
foreach ($f in $xmlFiles) {
  try {
    [xml]$x = Get-Content -LiteralPath $f.FullName -Raw
    $suites = @($x.testsuite) + @($x.testsuites.testsuite) | Where-Object { $_ }
    foreach ($su in $suites) {
      $total   += [int]($su.tests    -as [int])
      $failed  += [int]($su.failures -as [int])
      $errors  += [int]($su.errors   -as [int])
      $skipped += [int]($su.skipped  -as [int])
      if (([int]($su.failures -as [int]) + [int]($su.errors -as [int])) -gt 0) {
        $failedClasses.Add($su.name)
      }
    }
  } catch { }
}
if (($failed + $errors) -gt 0) { $status = 1 }

$dur = [int]((Get-Date) - $started).TotalSeconds
$result = if ($status -ne 0) { '실패' } else { '통과' }

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# 하네스 리포트 - $ts")
$lines.Add('')
$lines.Add("- 결과: **$result** (모드: $mode, ${dur}s)")
$lines.Add("- 테스트: 총 $total / 실패 $failed / 오류 $errors / 스킵 $skipped")
$lines.Add('')
$lines.Add('| 스테이지 | 결과 | 시간 |')
$lines.Add('|---|---|---|')
$rows | ForEach-Object { $lines.Add($_) }
if ($failedClasses.Count -gt 0) {
  $lines.Add('')
  $lines.Add('## 실패한 테스트 클래스')
  $failedClasses | Sort-Object -Unique | Select-Object -First 20 | ForEach-Object { $lines.Add("- $_") }
}
$lines.Add('')
$lines.Add("로그: reports\harness\$ts.log")

$lines | Set-Content -LiteralPath $report -Encoding UTF8
Copy-Item -LiteralPath $report -Destination (Join-Path $reportDir 'latest.md') -Force

Write-Host ''
$lines | ForEach-Object { Write-Host $_ }
Write-Host ''
if ($status -ne 0) {
  Write-Host '[harness] 실패. troubleshooting.md 에 기록하고 원인을 고친 뒤 다시 실행할 것.'
} else {
  Write-Host '[harness] 통과. plan.md 의 검증 줄에 위 요약을 옮겨 적을 것.'
}
exit $status
