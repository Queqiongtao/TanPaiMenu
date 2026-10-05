<#
.SYNOPSIS
    构建 debug APK、安装到平板并做启动自检。
.EXAMPLE
    scripts\build-and-install.bat
.EXAMPLE
    scripts\build-and-install.bat -SkipInstall
#>
[CmdletBinding()]
param(
    [string]$SdkRoot    = '',
    [string]$JdkHome    = '',
    [string]$GradleHome = 'D:\Android\gradle-8.9',
    [string]$AppId      = 'com.tanpai.menu',
    [string]$Activity   = '.MainActivity',
    [switch]$SkipInstall
)

. "$PSScriptRoot\_common.ps1"

$repoRoot = Split-Path -Parent $PSScriptRoot

if (-not $SdkRoot) {
    if ($env:ANDROID_HOME) { $SdkRoot = $env:ANDROID_HOME } else { $SdkRoot = 'D:\Android\Sdk' }
}
$JdkHome = Resolve-JdkHome $JdkHome

if (-not (Test-Path (Join-Path $JdkHome 'bin\java.exe'))) { Fail "找不到 JDK 17: $JdkHome，先运行 scripts\setup-toolchain.bat" }
$env:JAVA_HOME        = $JdkHome
$env:ANDROID_HOME     = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
Info "JAVA_HOME    = $JdkHome"
Info "ANDROID_HOME = $SdkRoot"

$localProps = Join-Path $repoRoot 'local.properties'
if (-not (Test-Path $localProps)) {
    $sdkDir = $SdkRoot -replace '\\', '/'
    [System.IO.File]::WriteAllText($localProps, "sdk.dir=$sdkDir`r`n", (New-Object System.Text.ASCIIEncoding))
    Info "已生成 local.properties (sdk.dir=$sdkDir)"
}

$adb = Resolve-Adb $SdkRoot
if (-not $adb) { Fail "找不到 adb.exe，先运行 scripts\setup-toolchain.bat" }

$gradlew = Join-Path $repoRoot 'gradlew.bat'
if (Test-Path $gradlew) {
    $runner = $gradlew
    Info "使用 wrapper: gradlew.bat"
} else {
    $runner = Join-Path $GradleHome 'bin\gradle.bat'
    if (-not (Test-Path $runner)) { Fail "既无 gradlew.bat 也无 $runner，先运行 scripts\setup-toolchain.bat" }
    Info "使用本地 Gradle: $runner"
}

Step "构建 assembleDebug"
Push-Location $repoRoot
try {
    Invoke-Native -Exe $runner -Arguments @('assembleDebug', '--no-daemon') -Tail 25 | Out-Null
} finally { Pop-Location }

$apk = Join-Path $repoRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { Fail "未找到 APK: $apk" }
Ok ("构建成功: {0} ({1:N1} MB)" -f (Split-Path -Leaf $apk), ((Get-Item $apk).Length / 1MB))

if ($SkipInstall) { return }

Step "安装到设备"
$r = Invoke-Native -Exe $adb -Arguments @('devices') -Quiet
$lines = @(($r.Output -split "`r?`n") | Select-Object -Skip 1 | Where-Object { $_ -match '^(\S+)\s+device$' })
if ($lines.Count -eq 0) { Fail "没有处于 device 状态的设备，先运行 scripts\device-precheck.bat" }
$serial = ([regex]::Match($lines[0], '^(\S+)')).Groups[1].Value
Info "设备: $serial"

$inst = Invoke-Native -Exe $adb -Arguments @('-s', $serial, 'install', '-r', $apk) -Quiet
$inst.Output | Out-Host
if ($inst.Output -notmatch 'Success') { Fail "adb install 失败" }
Ok "安装成功"

Step "启动并自检"
Invoke-Native -Exe $adb -Arguments @('-s', $serial, 'shell', 'am', 'start', '-n', "$AppId/$Activity") | Out-Null
Start-Sleep -Seconds 4

$crash = Invoke-Native -Exe $adb -Arguments @('-s', $serial, 'logcat', '-d', '-b', 'crash', '-t', '200') -Quiet
if ($crash.Output -match 'FATAL EXCEPTION') {
    Write-Host $crash.Output -ForegroundColor Red
    Fail "应用启动后发生崩溃"
}
Ok "无崩溃日志，Phase 0 真机验收通过"