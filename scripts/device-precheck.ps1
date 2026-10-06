<#
.SYNOPSIS
    采集 Android 平板的设备能力，写入 docs/device-baseline.md。
.DESCRIPTION
    Phase 0 产出物：API 版本、ABI、内存、分辨率、密度、型号与存储余量。
    需要先把平板用 USB 连上并允许 USB 调试。
.EXAMPLE
    scripts\device-precheck.bat
#>
[CmdletBinding()]
param(
    [string]$SdkRoot    = '',
    [string]$OutputPath = '',
    [int]$TimeoutSec    = 90
)

. "$PSScriptRoot\_common.ps1"

$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $OutputPath) { $OutputPath = Join-Path $repoRoot 'docs\device-baseline.md' }

if (-not $SdkRoot) {
    if ($env:ANDROID_HOME) { $SdkRoot = $env:ANDROID_HOME }
    elseif ($env:ANDROID_SDK_ROOT) { $SdkRoot = $env:ANDROID_SDK_ROOT }
    else { $SdkRoot = 'D:\Android\Sdk' }
}

$adb = Resolve-Adb $SdkRoot
if (-not $adb) { Fail "找不到 adb.exe；先运行 scripts\setup-toolchain.bat" }
Info "使用 adb: $adb"

Invoke-Native -Exe $adb -Arguments @('kill-server')  -Quiet | Out-Null
Invoke-Native -Exe $adb -Arguments @('start-server') -Quiet | Out-Null
Info "等待设备就绪（最长 $TimeoutSec 秒），请在平板上确认 USB 调试授权..."

$deadline = (Get-Date).AddSeconds($TimeoutSec)
$serial   = $null
while ((Get-Date) -lt $deadline) {
    $r = Invoke-Native -Exe $adb -Arguments @('devices') -Quiet
    $lines = ($r.Output -split "`r?`n") | Select-Object -Skip 1 | Where-Object { $_ -match '\S' }
    foreach ($l in $lines) {
        if ($l -match '^(\S+)\s+device$') { $serial = $Matches[1]; break }
        if ($l -match 'unauthorized') { Warn "设备未授权：请在平板屏幕上点击 允许 USB 调试" }
        if ($l -match 'offline')      { Warn "设备 offline，尝试重插 USB" }
    }
    if ($serial) { break }
    Start-Sleep -Seconds 3
}
if (-not $serial) { Fail "未检测到已授权设备（adb devices 里没有 device 状态条目）" }
Ok "设备已连接: $serial"

function Prop([string]$key) { (Invoke-Native -Exe $adb -Arguments @('-s', $serial, 'shell', 'getprop', $key) -Quiet).Output }
function Sh([string]$cmd)   { (Invoke-Native -Exe $adb -Arguments @('-s', $serial, 'shell', $cmd) -Quiet).Output }

$model       = Prop 'ro.product.model'
$brand       = Prop 'ro.product.brand'
$androidVer  = Prop 'ro.build.version.release'
$apiLevel    = Prop 'ro.build.version.sdk'
$abi         = Prop 'ro.product.cpu.abi'
$abiList     = Prop 'ro.product.cpu.abilist'
$fingerprint = Prop 'ro.build.fingerprint'

$memLine    = Sh 'cat /proc/meminfo | head -n 1'
$memTotalKb = 0
if ($memLine -match '(\d+)') { $memTotalKb = [int]$Matches[1] }
$memTotalMb = [math]::Round($memTotalKb / 1024, 0)

$wmSize    = Sh 'wm size'
$wmDensity = Sh 'wm density'
$dataDf    = Sh 'df -h /data'

$freeMb = 0
foreach ($l in ($dataDf -split "`n")) {
    if ($l -match '\s(\d+(?:\.\d+)?)([KMGT])\s+\d+%') {
        $v = [double]$Matches[1]; $u = $Matches[2]
        $freeMb = switch ($u) {
            'K' { [math]::Round($v / 1024, 0) }
            'M' { [math]::Round($v, 0) }
            'G' { [math]::Round($v * 1024, 0) }
            'T' { [math]::Round($v * 1024 * 1024, 0) }
            default { 0 }
        }
        break
    }
}

$adbVersion = ((Invoke-Native -Exe $adb -Arguments @('version') -Quiet).Output -split "`r?`n" | Select-Object -First 2) -join ' / '
$now         = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
$densityFlat = ($wmDensity -replace "`r?`n", ' / ')
$sizeFlat    = if ($wmSize -match '(\d+x\d+)') { $Matches[1] } else { $wmSize }
if ($wmSize -match 'Override size:\s*(\d+x\d+)') { $sizeFlat = "$sizeFlat（覆盖 $($Matches[1])）" }
$fence       = '```'   # 用单引号常量：双引号 here-string 会把 3 个反引号转义成 1 个

$abiNote = if ($abi -eq 'arm64-v8a') {
    '主 ABI 为 arm64-v8a：只需 arm64-v8a 单 ABI 包'
} else {
    "主 ABI 为 $abi：只需包含 $abi 的包"
}
$memNote = if ($memTotalMb -lt 2048) {
    "$memTotalMb MB 偏小，需收紧 Coil 缓存与首屏卡片数"
} else {
    "$memTotalMb MB 充足，可沿用默认 Coil 缓存策略"
}
$densityNote = if ($wmDensity -match 'Override density:\s*(\d+)') {
    "存在密度覆盖（$densityFlat）：UI 按覆盖密度 $($Matches[1]) 设计"
} else {
    "字号与卡片尺寸按 $densityFlat 设计"
}

$md = @"
# 设备能力基线（Phase 0 产出）

> 采集时间：$now
> 采集命令：`scripts\device-precheck.bat`
> 设备序列号：$serial
> adb：$adbVersion

| 项目 | 取值 |
| --- | --- |
| 品牌 / 型号 | $brand / $model |
| Android 版本 | $androidVer（API $apiLevel） |
| 主 ABI | $abi |
| ABI 列表 | $abiList |
| 内存总量 | $memTotalMb MB |
| 屏幕 | $sizeFlat |
| 密度 | $densityFlat |
| /data 可用 | $freeMb MB |
| Build 指纹 | $fingerprint |

## 原始输出

$fence
cat /proc/meminfo        -> $memLine
wm size                  -> $wmSize
wm density               -> $densityFlat
df -h /data              -> $dataDf
$fence

## 对打包与 UI 的影响

- ABI：$abiNote。
- 内存：$memNote。
- 分辨率与密度：$sizeFlat，$densityNote；展示页按可用宽高自动分档（横屏单行三列 / 竖屏两行卡片），字号与卡片尺寸按该密度换算。
- 存储：/data 可用 $freeMb MB，容纳 APK 与菜品图片素材无压力。
"@
$outDir = Split-Path -Parent $OutputPath
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }
[System.IO.File]::WriteAllText($OutputPath, $md, (New-Object System.Text.UTF8Encoding($false)))
Ok "已写入 $OutputPath"
Write-Host "  API $apiLevel / $abi / ${memTotalMb}MB / $wmSize / $wmDensity" -ForegroundColor Green