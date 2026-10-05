<#
.SYNOPSIS
    采集平板上的应用日志与 logcat，导出到本地目录，便于离线排障。
.DESCRIPTION
    Phase 1 产出物（见 docs/design/0005-发布分发与现场排障.md）：
      - device.txt      设备型号 / API / ABI 等基线信息
      - logcat.txt      最近若干行系统日志
      - app-logs/*.log  应用自身日志（filesDir/logs，保留最近 7 天）
    需要先把平板用 USB 连上并允许 USB 调试。
.EXAMPLE
    scripts\collect-logs.bat
#>
[CmdletBinding()]
param(
    [string]$SdkRoot = '',
    [string]$OutputDir = '',
    [string]$Serial = '',
    [string]$Package = 'com.tanpai.menu',
    [int]$LogcatLines = 2000
)

. "$PSScriptRoot\_common.ps1"

$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $SdkRoot) {
    if ($env:ANDROID_HOME) { $SdkRoot = $env:ANDROID_HOME }
    elseif ($env:ANDROID_SDK_ROOT) { $SdkRoot = $env:ANDROID_SDK_ROOT }
    else { $SdkRoot = 'D:\Android\Sdk' }
}
if (-not $OutputDir) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $OutputDir = Join-Path $repoRoot "dist\logs\$stamp"
}

Step '收集现场日志'

$adb = Resolve-Adb $SdkRoot
if (-not $adb) { Fail '找不到 adb.exe；先运行 scripts\setup-toolchain.bat' }
Info "使用 adb: $adb"

$deviceArgs = @()
if ($Serial) { $deviceArgs = @('-s', $Serial) }

$devices = Invoke-Native -Exe $adb -Arguments ($deviceArgs + @('devices')) -Quiet
$online = @(($devices.Output -split "`r?`n") | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' })
if ($online.Count -eq 0) { Fail '没有已授权的设备；请连接平板并允许 USB 调试' }
Info "在线设备: $($online -join '; ')"

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
Info "输出目录: $OutputDir"

$deviceFile = Join-Path $OutputDir 'device.txt'
$deviceInfo = @()
foreach ($prop in 'ro.product.model', 'ro.build.version.release', 'ro.build.version.sdk', 'ro.product.cpu.abi') {
    $value = (Invoke-Native -Exe $adb -Arguments ($deviceArgs + @('shell', 'getprop', $prop)) -Quiet).Output
    $deviceInfo += "$prop = $value"
}
$deviceInfo | Set-Content -LiteralPath $deviceFile -Encoding UTF8
Ok "device.txt"

$logcatFile = Join-Path $OutputDir 'logcat.txt'
$logcatCmd = '"{0}" {1} logcat -d -v time -t {2} > "{3}"' -f $adb, ($deviceArgs -join ' '), $LogcatLines, $logcatFile
Invoke-Native -Exe $env:ComSpec -Arguments @('/c', $logcatCmd) -Quiet | Out-Null
Ok 'logcat.txt'

$logDir = Join-Path $OutputDir 'app-logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$listing = Invoke-Native -Exe $adb -Arguments ($deviceArgs + @('shell', 'run-as', $Package, 'ls', 'files/logs')) -AllowExitCodes @(0, 1) -Quiet
$logFiles = @(($listing.Output -split "`r?`n") | Where-Object { $_ -match '^app-\d{4}-\d{2}-\d{2}\.log$' })
if ($logFiles.Count -eq 0) {
    Warn '未取到应用日志：确认应用已启动过，且当前为 debug 包（run-as 需要 debuggable）'
} else {
    foreach ($name in $logFiles) {
        $remote = "files/logs/$name"
        $dest = Join-Path $logDir $name
        $catCmd = '"{0}" {1} exec-out run-as {2} cat {3} > "{4}"' -f $adb, ($deviceArgs -join ' '), $Package, $remote, $dest
        Invoke-Native -Exe $env:ComSpec -Arguments @('/c', $catCmd) -Quiet | Out-Null
        Ok "app-logs/$name"
    }
}

Step '完成'
Info "把整个目录打包发给开发者即可：$OutputDir"
