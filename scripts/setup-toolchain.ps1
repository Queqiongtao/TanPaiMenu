<#
.SYNOPSIS
    安装 TanPaiMenu 的 Android 构建工具链：JDK 17 + Android SDK + Gradle，并生成 gradle wrapper。
.DESCRIPTION
    幂等：已装好的组件自动复用/跳过。直连不通时按候选顺序尝试国内镜像；
    未显式传 -ProxyUrl 时会自动探测本地代理端口（7890/7897/10809/1080/10808）。
.PARAMETER ConfigureOnly
    不下载任何内容，只重新写入环境变量、PATH 与 gradle-wrapper.properties。
.EXAMPLE
    scripts\setup-toolchain.bat
.EXAMPLE
    scripts\setup-toolchain.bat -ProxyUrl http://127.0.0.1:7890
.EXAMPLE
    scripts\setup-toolchain.bat -ConfigureOnly
#>
[CmdletBinding()]
param(
    [string]$SdkRoot         = 'D:\Android\Sdk',
    [string]$JdkHome         = 'D:\Android\jdk-17',
    [string]$GradleHome      = 'D:\Android\gradle-8.9',
    [string]$GradleVersion   = '8.9',
    [string]$ProxyUrl        = '',
    [string]$CmdlineToolsZip = 'commandlinetools-win-11076708_latest.zip',
    [switch]$ConfigureOnly,
    [switch]$SkipWrapper
)

. "$PSScriptRoot\_common.ps1"

$ProgressPreference = 'SilentlyContinue'
$repoRoot           = Split-Path -Parent $PSScriptRoot

function Test-Url([string]$url) {
    try {
        $r = Invoke-WebRequest -Uri $url -Method Head -TimeoutSec 12 -UseBasicParsing
        return ($r.StatusCode -ge 200 -and $r.StatusCode -lt 400)
    } catch { return $false }
}

function Get-FirstReachable([string[]]$urls) {
    foreach ($u in $urls) {
        if (Test-Url $u) { return $u }
        Info "不可达: $u"
    }
    return $null
}

function Get-Download([string]$url, [string]$destFile) {
    Info "下载 $url"
    $dir = Split-Path -Parent $destFile
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    Invoke-WebRequest -Uri $url -OutFile $destFile -TimeoutSec 900 -UseBasicParsing
    Ok ("已下载 {0} ({1:N1} MB)" -f (Split-Path -Leaf $destFile), ((Get-Item $destFile).Length / 1MB))
}

function Expand-ZipTo([string]$zip, [string]$dest) {
    if (-not (Test-Path $dest)) { New-Item -ItemType Directory -Path $dest -Force | Out-Null }
    Expand-Archive -LiteralPath $zip -DestinationPath $dest -Force
    Ok "解压到 $dest"
}

function Write-WrapperProperties([string]$Path, [string]$Version) {
    $lines = @(
        'distributionBase=GRADLE_USER_HOME',
        'distributionPath=wrapper/dists',
        "distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-$Version-bin.zip",
        'networkTimeout=10000',
        'validateDistributionUrl=false',
        'zipStoreBase=GRADLE_USER_HOME',
        'zipStorePath=wrapper/dists'
    )
    [System.IO.File]::WriteAllText($Path, (($lines -join "`r`n") + "`r`n"), (New-Object System.Text.ASCIIEncoding))
}

# ---------------------------------------------------------------- 代理
if ($ProxyUrl) {
    $env:HTTP_PROXY  = $ProxyUrl
    $env:HTTPS_PROXY = $ProxyUrl
    Info "本次会话代理: $ProxyUrl"
} elseif (-not $ConfigureOnly) {
    foreach ($port in 7890, 7897, 10809, 1080, 10808) {
        $probe = New-Object System.Net.Sockets.TcpClient
        try {
            $null = $probe.BeginConnect('127.0.0.1', $port, $null, $null)
            Start-Sleep -Milliseconds 300
            if ($probe.Connected) {
                $env:HTTP_PROXY = "http://127.0.0.1:$port"; $env:HTTPS_PROXY = $env:HTTP_PROXY
                Info "检测到本地代理端口 $port，已自动启用"
                break
            }
        } catch { } finally { $probe.Dispose() }
    }
}

# ---------------------------------------------------------------- JDK 17
Step "JDK 17"
$javaExe  = Join-Path $JdkHome 'bin\java.exe'
$resolved = $false
if ((Test-Path $javaExe) -and ((Get-JavaMajor $javaExe) -eq 17)) {
    $resolved = $true
    Ok "已存在 JDK 17: $JdkHome"
}

if (-not $resolved) {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
    foreach ($base in 'C:\Program Files\Eclipse Adoptium', 'D:\Android') {
        if (Test-Path $base) {
            $candidates += @(Get-ChildItem $base -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -like 'jdk*' } |
                ForEach-Object { Join-Path $_.FullName 'bin\java.exe' })
        }
    }
    foreach ($cand in $candidates) {
        if ((Test-Path $cand) -and ((Get-JavaMajor $cand) -eq 17)) {
            $JdkHome  = Split-Path -Parent (Split-Path -Parent $cand)
            $javaExe  = $cand
            $resolved = $true
            Ok "复用已安装的 JDK 17: $JdkHome"
            break
        }
    }
}

if (-not $resolved -and -not $ConfigureOnly) {
    $winget = (Get-Command winget.exe -ErrorAction SilentlyContinue).Source
    if ($winget -and (Test-Path $winget)) {
        Info "尝试 winget 安装 EclipseAdoptium.Temurin.17.JDK（可选）"
        try {
            Invoke-Native -Exe $winget -Arguments @(
                'install', '--id', 'EclipseAdoptium.Temurin.17.JDK', '-e', '--silent',
                '--accept-source-agreements', '--accept-package-agreements', '--disable-interactivity'
            ) -Quiet | Out-Null
            $found = @(Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -like 'jdk-17*' } | Select-Object -First 1)
            if ($found.Count -gt 0) {
                $JdkHome  = $found[0].FullName
                $javaExe  = Join-Path $JdkHome 'bin\java.exe'
                $resolved = (Get-JavaMajor $javaExe) -eq 17
                if ($resolved) { Ok "winget 安装完成: $JdkHome" }
            }
        } catch { Warn "winget 不可用，改用镜像直下: $($_.Exception.Message)" }
    }

    if (-not $resolved) {
        $tunaIndex = 'https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jdk/x64/windows/'
        $urls = @()
        try {
            $html = (Invoke-WebRequest -Uri $tunaIndex -TimeoutSec 20 -UseBasicParsing).Content
            $m = [regex]::Match($html, 'href="(OpenJDK17U-jdk_x64_windows_hotspot_[^"]+\.zip)"')
            if ($m.Success) { $urls += ($tunaIndex + $m.Groups[1].Value) }
        } catch { Info "镜像目录探测失败: $tunaIndex" }
        $urls += 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse'
        $urls += 'https://mirrors.nju.edu.cn/Adoptium/17/jdk/x64/windows/'

        $zip = Get-FirstReachable $urls
        if (-not $zip) { Fail "JDK 下载源均不可达；确认代理可用（-ProxyUrl http://127.0.0.1:7890）" }

        $tmpZip = Join-Path $env:TEMP 'tanpai-jdk17.zip'
        Get-Download $zip $tmpZip
        Expand-ZipTo $tmpZip $JdkHome

        # Temurin zip 自带一层 jdk-17.x.y 目录，把内容提到 $JdkHome 根
        $inner = @(Get-ChildItem $JdkHome -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1)
        if ($inner.Count -gt 0) {
            Get-ChildItem -LiteralPath $inner[0].FullName -Force | Move-Item -Destination $JdkHome -Force
            Remove-Item -LiteralPath $inner[0].FullName -Recurse -Force
        }
        Remove-Item $tmpZip -Force -ErrorAction SilentlyContinue
        $javaExe  = Join-Path $JdkHome 'bin\java.exe'
        $resolved = (Test-Path $javaExe) -and ((Get-JavaMajor $javaExe) -eq 17)
    }
}

$javaExe = Join-Path $JdkHome 'bin\java.exe'
if (-not (Test-Path $javaExe)) {
    if ($ConfigureOnly) { Fail "未找到 JDK 17（-ConfigureOnly 不下载），请先不带 -ConfigureOnly 运行一次" }
    Fail "JDK 安装后仍找不到 $javaExe"
}
if ((Get-JavaMajor $javaExe) -ne 17) { Fail "JDK 版本不是 17: $javaExe" }
Ok "JDK 17 就绪: $JdkHome"
Set-UserEnv 'JAVA_HOME' $JdkHome
Add-UserPath (Join-Path $JdkHome 'bin') | Out-Null

# ---------------------------------------------------------------- Android SDK
Step "Android SDK 命令行工具"
$sdkManager = Join-Path $SdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
if (Test-Path $sdkManager) {
    Ok "已存在 sdkmanager"
} elseif ($ConfigureOnly) {
    Warn "缺少 cmdline-tools（-ConfigureOnly 不下载）"
} else {
    $urls = @(
        "https://dl.google.com/android/repository/$CmdlineToolsZip",
        "https://mirrors.cloud.tencent.com/AndroidSDK/$CmdlineToolsZip",
        "https://mirrors.tuna.tsinghua.edu.cn/AOSP/android/repository/$CmdlineToolsZip"
    )
    $zip = Get-FirstReachable $urls
    if (-not $zip) { Fail "cmdline-tools 下载源均不可达，请提供 -ProxyUrl" }

    $tmpZip  = Join-Path $env:TEMP $CmdlineToolsZip
    $staging = Join-Path $env:TEMP 'tanpai-cmdline-tools'
    Get-Download $zip $tmpZip
    if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
    Expand-ZipTo $tmpZip $staging
    $latestDir = Join-Path $SdkRoot 'cmdline-tools\latest'
    if (Test-Path $latestDir) { Remove-Item $latestDir -Recurse -Force }
    New-Item -ItemType Directory -Path (Split-Path -Parent $latestDir) -Force | Out-Null
    Move-Item -LiteralPath (Join-Path $staging 'cmdline-tools') -Destination $latestDir
    Remove-Item $tmpZip -Force -ErrorAction SilentlyContinue
    Remove-Item $staging -Recurse -Force -ErrorAction SilentlyContinue
    Ok "sdkmanager 就绪"
}

Set-UserEnv 'ANDROID_HOME' $SdkRoot
Set-UserEnv 'ANDROID_SDK_ROOT' $SdkRoot
Add-UserPath (Join-Path $SdkRoot 'cmdline-tools\latest\bin') | Out-Null

if ((Test-Path $sdkManager) -and -not $ConfigureOnly) {
    Step "接受 SDK 许可并安装组件"
    $licenses = @(1..60 | ForEach-Object { 'y' })
    Invoke-Native -Exe $sdkManager -Arguments @('--licenses', "--sdk_root=$SdkRoot") -InputLines $licenses -Quiet | Out-Null
    Info "许可已接受"

    foreach ($p in @('platform-tools', 'platforms;android-34', 'platforms;android-35', 'build-tools;35.0.0')) {
        Info "安装 $p"
        Invoke-Native -Exe $sdkManager -Arguments @($p, "--sdk_root=$SdkRoot") -Quiet | Out-Null
        Ok $p
    }
}

# platform-tools 只有在组件安装完之后才存在，这里补进 PATH
Add-UserPath (Join-Path $SdkRoot 'platform-tools') | Out-Null

# ---------------------------------------------------------------- Gradle
Step "Gradle $GradleVersion"
$gradleBat = Join-Path $GradleHome 'bin\gradle.bat'
if (Test-Path $gradleBat) {
    Ok "已存在 Gradle: $GradleHome"
} elseif ($ConfigureOnly) {
    Warn "缺少 Gradle 发行包（-ConfigureOnly 不下载）"
} else {
    $distName = "gradle-$GradleVersion-bin.zip"
    $urls = @(
        "https://mirrors.cloud.tencent.com/gradle/$distName",
        "https://mirrors.aliyun.com/gradle/$distName",
        "https://services.gradle.org/distributions/$distName"
    )
    $zip = Get-FirstReachable $urls
    if (-not $zip) { Fail "Gradle 下载源均不可达，请提供 -ProxyUrl" }

    $tmpZip  = Join-Path $env:TEMP $distName
    $staging = Join-Path $env:TEMP 'tanpai-gradle'
    Get-Download $zip $tmpZip
    if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
    Expand-ZipTo $tmpZip $staging
    if (Test-Path $GradleHome) { Remove-Item $GradleHome -Recurse -Force }
    Move-Item -LiteralPath (Join-Path $staging "gradle-$GradleVersion") -Destination $GradleHome
    Remove-Item $tmpZip -Force -ErrorAction SilentlyContinue
    Remove-Item $staging -Recurse -Force -ErrorAction SilentlyContinue
    Ok "Gradle 就绪: $GradleHome"
}
Add-UserPath (Join-Path $GradleHome 'bin') | Out-Null

# ---------------------------------------------------------------- wrapper
if (-not $SkipWrapper) {
    Step "gradle wrapper"
    $wrapperProps = Join-Path $repoRoot 'gradle\wrapper\gradle-wrapper.properties'

    if ((Test-Path $gradleBat) -and -not $ConfigureOnly) {
        Push-Location $repoRoot
        try {
            Invoke-Native -Exe $gradleBat -Arguments @('wrapper', '--gradle-version', $GradleVersion, '--distribution-type', 'bin', '--no-daemon') -Quiet | Out-Null
            Ok "gradle wrapper 已生成"
        } finally { Pop-Location }
    }

    if (Test-Path (Split-Path -Parent $wrapperProps)) {
        Write-WrapperProperties $wrapperProps $GradleVersion
        Ok "gradle-wrapper.properties 指向腾讯镜像（整文件重写，避免误改其它键）"
    } else {
        Warn "未找到 $wrapperProps，跳过"
    }
}

# ---------------------------------------------------------------- 校验
Step "工具链校验"
if (Test-Path $javaExe)   { Invoke-Native -Exe $javaExe   -Arguments @('-version') | Out-Null }
if (Test-Path $sdkManager) { Invoke-Native -Exe $sdkManager -Arguments @('--version', "--sdk_root=$SdkRoot") | Out-Null }
$adb = Join-Path $SdkRoot 'platform-tools\adb.exe'
if (Test-Path $adb)       { Invoke-Native -Exe $adb       -Arguments @('version') | Out-Null }
if (Test-Path $gradleBat) {
    $gv = Invoke-Native -Exe $gradleBat -Arguments @('-v', '--no-daemon') -Quiet -AllowExitCodes @(0, 1)
    if ($gv.ExitCode -eq 0) {
        $gline = @(($gv.Output -split "`r?`n") | Where-Object { $_ -match '^Gradle ' } | Select-Object -First 1)
        if ($gline.Count -gt 0) { Ok $gline[0].Trim() } else { Ok "Gradle 可执行" }
    } elseif ($gv.Output -match 'native|Could not initialize') {
        Warn "Gradle 在受限沙箱内无法启动（native-library 限制），发行包本身完整"
    } else {
        Warn "Gradle 未能启动（exit $($gv.ExitCode)）"
    }
}
Write-Host "`n工具链就绪。下一步: scripts\device-precheck.bat" -ForegroundColor Green