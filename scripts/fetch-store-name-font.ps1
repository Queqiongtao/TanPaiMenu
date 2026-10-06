<#
.SYNOPSIS
    下载并裁剪展示页「店名」字体（霞鹜文楷 LXGW WenKai，SIL OFL 1.1）。
.DESCRIPTION
    产出物见 docs/design/0008-店名艺术化与顶栏布局.md：
      1. 下载 LXGW WenKai Medium 与 OFL 许可文本到 .toolchain\fonts（该目录已 gitignore）
      2. 用 GB2312 码表生成一级 + 二级常用字表（6763 字），另附 ASCII 与常用中文标点
      3. 用 fontTools pyftsubset 裁剪为 app\src\main\res\font\store_name_wenkai.ttf
      4. 因 OFL 声明了保留字体名（RFN），子集内部名称改写为 TanPai Store Name
      5. 校验子集包含「大金馄饨」等必备字形，并打印体积
    需要联网；首次运行会自动安装 fonttools（用户级 pip）。
    字表外的生僻字运行期由系统字体回退，属预期行为。
.EXAMPLE
    scripts\fetch-store-name-font.bat
#>
[CmdletBinding()]
param(
    [string]$Python = '',
    [string]$Version = 'v1.522',
    [switch]$Force
)

. "$PSScriptRoot\_common.ps1"

$repoRoot = Split-Path -Parent $PSScriptRoot
$workDir = Join-Path $repoRoot '.toolchain\fonts'
$sourceFont = Join-Path $workDir 'LXGWWenKai-Medium.ttf'
$charsetFile = Join-Path $workDir 'gb2312-charset.txt'
$outFont = Join-Path $repoRoot 'app\src\main\res\font\store_name_wenkai.ttf'
$licenseOut = Join-Path $repoRoot 'docs\third-party\LXGWWenKai-OFL.txt'

$fontUrl = "https://github.com/lxgw/LxgwWenKai/releases/download/$Version/LXGWWenKai-Medium.ttf"
$licenseUrl = 'https://raw.githubusercontent.com/lxgw/LxgwWenKai/main/OFL.txt'
$requiredGlyphs = '大金馄饨'
$subsetFamily = 'TanPai Store Name'

$renamePy = "import sys,os;from fontTools.ttLib import TTFont;" +
    "m={1:'$subsetFamily',2:'Medium',3:'$subsetFamily Medium;1.0',4:'$subsetFamily Medium'," +
    "6:'TanPaiStoreName-Medium',16:'$subsetFamily',17:'Medium'};f=TTFont(sys.argv[1]);" +
    "[setattr(r,'string',m[r.nameID]) for r in f['name'].names if r.nameID in m];" +
    "f.save(sys.argv[1]+'.tmp');os.replace(sys.argv[1]+'.tmp',sys.argv[1])"

$verifyPy = "import sys;from fontTools.ttLib import TTFont;c=TTFont(sys.argv[1]).getBestCmap();" +
    "m=[ch for ch in sys.argv[2] if ord(ch) not in c];print('glyphs=%d'%len(c));" +
    "print('missing=%s'%(''.join(m) or 'none'));sys.exit(1 if m else 0)"

Step '准备店名字体（霞鹜文楷 子集）'
New-Item -ItemType Directory -Force -Path $workDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outFont) | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $licenseOut) | Out-Null

if (-not $Python) {
    foreach ($candidate in 'python', 'py') {
        $cmd = Get-Command $candidate -ErrorAction SilentlyContinue
        if ($cmd) { $Python = $cmd.Source; break }
    }
}
if (-not $Python) { Fail '找不到 Python；请先安装 Python 3 或通过 -Python 指定路径' }
Info "使用 Python: $Python"

Step '获取源字体与许可文本'
if ((Test-Path -LiteralPath $sourceFont) -and -not $Force) {
    Info "已存在源字体，跳过下载: $sourceFont"
} else {
    Info "下载源字体: $fontUrl"
    Invoke-WebRequest -Uri $fontUrl -OutFile $sourceFont -UseBasicParsing
    Ok ("源字体 {0:N0} 字节" -f (Get-Item -LiteralPath $sourceFont).Length)
}

if ((Test-Path -LiteralPath $licenseOut) -and -not $Force) {
    Info "已存在许可文本，跳过下载: $licenseOut"
} else {
    Info "下载许可文本: $licenseUrl"
    Invoke-WebRequest -Uri $licenseUrl -OutFile $licenseOut -UseBasicParsing
    Ok '许可文本已保存到 docs\third-party\LXGWWenKai-OFL.txt'
}
$licenseText = Get-Content -LiteralPath $licenseOut -Raw -Encoding UTF8
$hasRfn = $licenseText -match 'Reserved Font Name'
Info "保留字体名(RFN)声明: $(if ($hasRfn) { '有' } else { '无' })"

Step '生成 GB2312 常用字表'
$enc = [System.Text.Encoding]::GetEncoding('GB2312')
$sb = New-Object System.Text.StringBuilder
for ($hi = 0xB0; $hi -le 0xF7; $hi++) {
    for ($lo = 0xA1; $lo -le 0xFE; $lo++) {
        $ch = $enc.GetString([byte[]]@([byte]$hi, [byte]$lo))
        if ($ch -and $ch.Length -eq 1 -and $ch -ne '?') { [void]$sb.Append($ch) }
    }
}
$asciiChars = -join (0x20..0x7E | ForEach-Object { [char]$_ })
$extraPunct = '　、。·—…‘’“”《》〈〉「」『』【】（）〔〕？！：；，．％－＋＝￥'
$charsetText = $asciiChars + $extraPunct + $sb.ToString()
[System.IO.File]::WriteAllText($charsetFile, $charsetText, (New-Object System.Text.UTF8Encoding($false)))
Ok "字表已生成: $charsetFile（$($charsetText.Length) 字符）"

Step '检查 fontTools'
$probe = Invoke-Native -Exe $Python -Arguments @('-c', 'import fontTools; print(fontTools.version)') -AllowExitCodes @(0, 1) -Quiet
if ($probe.ExitCode -ne 0) {
    Info '未检测到 fontTools，执行用户级安装'
    Invoke-Native -Exe $Python -Arguments @('-m', 'pip', 'install', '--user', '--disable-pip-version-check', 'fonttools') -Tail 5 | Out-Null
    $probe = Invoke-Native -Exe $Python -Arguments @('-c', 'import fontTools; print(fontTools.version)') -Quiet
}
Ok "fontTools $($probe.Output.Trim())"

Step '裁剪字体子集'
Invoke-Native -Exe $Python -Arguments @(
    '-m', 'fontTools.subset', $sourceFont,
    "--text-file=$charsetFile",
    "--output-file=$outFont",
    '--layout-features=*',
    '--no-hinting',
    '--desubroutinize',
    '--drop-tables+=DSIG',
    '--name-IDs=*'
) -Quiet | Out-Null
Ok ("子集字体 {0:N0} 字节" -f (Get-Item -LiteralPath $outFont).Length)

if ($hasRfn) {
    Step '改写子集内部字体名（规避保留字体名）'
    Invoke-Native -Exe $Python -Arguments @('-c', $renamePy, $outFont) -Quiet | Out-Null
    Ok "内部名称已改为 $subsetFamily"
}

Step '校验子集'
Invoke-Native -Exe $Python -Arguments @('-c', $verifyPy, $outFont, $requiredGlyphs) | Out-Null
Ok "必备字形齐全: $requiredGlyphs"

Step '完成'
Info '产物: app\src\main\res\font\store_name_wenkai.ttf'
Info '许可: docs\third-party\LXGWWenKai-OFL.txt'
Info '说明: 子集来自霞鹜文楷 Medium，仅覆盖 GB2312 常用字；生僻字运行期回退系统字体。'