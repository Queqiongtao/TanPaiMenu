# TanPaiMenu Phase 0 - shared helpers.
# Dot-source from sibling scripts:  . "$PSScriptRoot\_common.ps1"
# Do not run this file directly.

$ErrorActionPreference = 'Stop'

function Step([string]$m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }
function Ok  ([string]$m) { Write-Host "  [OK]   $m" -ForegroundColor Green }
function Info([string]$m) { Write-Host "  [INFO] $m" }
function Warn([string]$m) { Write-Host "  [WARN] $m" -ForegroundColor Yellow }
function Fail([string]$m) { throw $m }

# 统一的原生命令调用口：
#   在 PowerShell 5.1 下，`$ErrorActionPreference = 'Stop'` 与 `2>&1` 组合会把子进程写到
#   stderr 的内容升级成终止性 NativeCommandError（`java -version` 正是把版本写到 stderr），
#   因此这里在调用期间临时降级为 Continue，并显式检查退出码。
function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)][string]$Exe,
        [string[]]$Arguments = @(),
        [string[]]$InputLines,
        [int[]]$AllowExitCodes = @(0),
        [int]$Tail = 0,
        [switch]$Quiet
    )
    if (-not (Test-Path -LiteralPath $Exe)) { Fail "找不到可执行文件: $Exe" }

    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        if ($PSBoundParameters.ContainsKey('InputLines')) {
            $output = @($InputLines) | & $Exe @Arguments 2>&1
        } else {
            $output = & $Exe @Arguments 2>&1
        }
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prev
    }

    $text = ($output | Out-String).TrimEnd()
    if (-not $Quiet -and $text) {
        if ($Tail -gt 0) {
            ($text -split "`r?`n" | Select-Object -Last $Tail) | Out-Host
        } else {
            $text | Out-Host
        }
    }
    if ($AllowExitCodes -notcontains $code) {
        Fail ("命令失败（exit {0}）: {1} {2}" -f $code, (Split-Path -Leaf $Exe), ($Arguments -join ' '))
    }
    return [pscustomobject]@{ ExitCode = $code; Output = $text }
}

function Get-JavaMajor([string]$JavaExe) {
    if (-not (Test-Path -LiteralPath $JavaExe)) { return 0 }
    try {
        $r = Invoke-Native -Exe $JavaExe -Arguments @('-version') -Quiet
        if ($r.Output -match 'version "(\d+)') { return [int]$Matches[1] }
    } catch { }
    return 0
}

# 受限环境（如 Codex 沙箱）下写用户环境变量会被拒绝：降级为告警，不中断后续步骤。
function Set-UserEnv([string]$Name, [string]$Value) {
    try {
        [Environment]::SetEnvironmentVariable($Name, $Value, 'User')
        Set-Item -Path "Env:$Name" -Value $Value
        return $true
    } catch {
        Warn "无法写入用户环境变量 $Name（当前环境不允许写注册表）；请在普通终端重跑本脚本"
        return $false
    }
}

function Add-UserPath([string]$Dir) {
    if (-not (Test-Path -LiteralPath $Dir)) {
        Warn "PATH 目标不存在，跳过: $Dir"
        return $false
    }
    try {
        $cur = [Environment]::GetEnvironmentVariable('Path', 'User')
        if ([string]::IsNullOrEmpty($cur)) { $cur = '' }
        $parts = $cur.Split(';') | Where-Object { $_ -ne '' }
        if ($parts -notcontains $Dir) {
            [Environment]::SetEnvironmentVariable('Path', (($parts + $Dir) -join ';'), 'User')
            Info "PATH += $Dir"
        }
    } catch {
        Warn "无法写入用户 PATH（当前环境不允许写注册表）: $Dir"
    }
    if (($env:Path.Split(';')) -notcontains $Dir) { $env:Path = "$env:Path;$Dir" }
    return $true
}

function Resolve-Adb([string]$SdkRoot) {
    $candidates = @(
        (Join-Path $SdkRoot 'platform-tools\adb.exe'),
        'D:\Program\scrcpy-win64-v4.1\adb.exe',
        'D:\Program\QtScrcpy-win-x64-v4.1.1\adb.exe'
    )
    $hit = @($candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1)
    if ($hit.Count -gt 0) { return $hit[0] }
    $cmd = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

function Resolve-JdkHome([string]$Preferred) {
    if ($Preferred -and (Test-Path (Join-Path $Preferred 'bin\java.exe'))) { return $Preferred }
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { return $env:JAVA_HOME }
    foreach ($base in 'C:\Program Files\Eclipse Adoptium', 'D:\Android') {
        if (-not (Test-Path $base)) { continue }
        $hit = @(Get-ChildItem $base -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like 'jdk*' } | Select-Object -First 1)
        if ($hit.Count -gt 0) { return $hit[0].FullName }
    }
    return $Preferred
}