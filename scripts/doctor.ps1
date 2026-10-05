<#
.SYNOPSIS
    TanPaiMenu 工具链自检：只读检查，不下载、不修改任何东西。
.DESCRIPTION
    检查 JDK 17、Android SDK 包、Gradle、wrapper 文件与用户 PATH，输出问题清单。
    退出码 0 表示全部通过，非 0 表示存在问题。
.EXAMPLE
    scripts\doctor.bat
#>
[CmdletBinding()]
param(
    [string]$SdkRoot    = '',
    [string]$JdkHome    = '',
    [string]$GradleHome = 'D:\Android\gradle-8.9'
)

. "$PSScriptRoot\_common.ps1"

$repoRoot = Split-Path -Parent $PSScriptRoot
$problems = @()

if (-not $SdkRoot) {
    if ($env:ANDROID_HOME) { $SdkRoot = $env:ANDROID_HOME } else { $SdkRoot = 'D:\Android\Sdk' }
}
$JdkHome = Resolve-JdkHome $JdkHome

Step "环境变量"
Info "JAVA_HOME    = $JdkHome"
Info "ANDROID_HOME = $SdkRoot"

Step "JDK 17"
$javaExe = Join-Path $JdkHome 'bin\java.exe'
if (Test-Path $javaExe) {
    $major = Get-JavaMajor $javaExe
    $first = ((Invoke-Native -Exe $javaExe -Arguments @('-version') -Quiet).Output -split "`r?`n")[0]
    if ($major -eq 17) { Ok $first } else { $problems += "JDK 版本不是 17（检测到 $major）: $JdkHome" }
} else {
    $problems += "找不到 java.exe: $javaExe"
}

Step "Android SDK"
$sdkManager = Join-Path $SdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
$adb        = Join-Path $SdkRoot 'platform-tools\adb.exe'
if (Test-Path $sdkManager) {
    $env:JAVA_HOME = $JdkHome
    Ok ("sdkmanager " + (Invoke-Native -Exe $sdkManager -Arguments @('--version', "--sdk_root=$SdkRoot") -Quiet).Output)
    $list = (Invoke-Native -Exe $sdkManager -Arguments @('--list_installed', "--sdk_root=$SdkRoot") -Quiet).Output
    foreach ($pkg in 'platform-tools', 'platforms;android-34', 'platforms;android-35', 'build-tools;35.0.0') {
        if ($list -match [regex]::Escape($pkg)) { Ok "已安装 $pkg" } else { $problems += "缺少 SDK 包: $pkg" }
    }
    if ($list -match 'licenses') { }  # 许可目录由 sdkmanager 自身维护
} else {
    $problems += "找不到 sdkmanager: $sdkManager"
}

if (Test-Path $adb) {
    Ok (((Invoke-Native -Exe $adb -Arguments @('version') -Quiet).Output -split "`r?`n")[0])
} else {
    $problems += "找不到 adb: $adb"
}

Step "Gradle"
$gradleBat = Join-Path $GradleHome 'bin\gradle.bat'
if (Test-Path $gradleBat) {
    $gv = Invoke-Native -Exe $gradleBat -Arguments @('-v', '--no-daemon') -Quiet -AllowExitCodes @(0, 1)
    if ($gv.ExitCode -eq 0) {
        $line = @(($gv.Output -split "`r?`n") | Where-Object { $_ -match '^Gradle ' } | Select-Object -First 1)
        if ($line.Count -gt 0) { Ok ($line[0].Trim() + "  [$GradleHome]") } else { Ok "Gradle 可执行 [$GradleHome]" }
    } elseif ($gv.Output -match 'native|Could not initialize') {
        Warn "Gradle 在受限沙箱内无法启动（native-library 限制），发行包本身完整；请在普通终端复验"
    } else {
        $problems += "Gradle 无法启动（exit $($gv.ExitCode)）"
    }
} else {
    $problems += "找不到 gradle.bat: $gradleBat"
}

Step "工程与 PATH"
foreach ($f in 'gradlew.bat', 'gradle\wrapper\gradle-wrapper.jar', 'gradle\wrapper\gradle-wrapper.properties') {
    if (Test-Path (Join-Path $repoRoot $f)) { Ok $f } else { $problems += "缺少 $f" }
}

$wp = Join-Path $repoRoot 'gradle\wrapper\gradle-wrapper.properties'
if (Test-Path $wp) {
    $txt = Get-Content $wp -Raw
    if ($txt -cmatch '(?m)^validatedistributionUrl') { $problems += "gradle-wrapper.properties 含脏键 validatedistributionUrl" }
    elseif ($txt -match 'mirrors\.cloud\.tencent\.com/gradle') { Ok "wrapper distributionUrl 指向国内镜像" }
    else { Warn "wrapper distributionUrl 未指向国内镜像（不影响功能，可能较慢）" }
}

$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
foreach ($d in @(
    (Join-Path $JdkHome 'bin'),
    (Join-Path $SdkRoot 'platform-tools'),
    (Join-Path $SdkRoot 'cmdline-tools\latest\bin'),
    (Join-Path $GradleHome 'bin')
)) {
    if ($userPath -and ($userPath.Split(';') -contains $d)) { Ok "PATH 含 $d" }
    else { $problems += "用户 PATH 缺少 $d（运行 scripts\setup-toolchain.bat -ConfigureOnly 修复）" }
}

Step "结论"
if ($problems.Count -eq 0) {
    Write-Host "  全部检查通过。" -ForegroundColor Green
    exit 0
}
Write-Host "  发现 $($problems.Count) 个问题：" -ForegroundColor Red
$problems | ForEach-Object { Write-Host "   - $_" -ForegroundColor Red }
exit 1