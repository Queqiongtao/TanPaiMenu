# TanPaiMenu

摆摊用的安卓电子菜单应用。平板作为顾客可见的电子菜单并充当主机，同一局域网下的安卓手机装同一个 APK 切客户端模式，通过一次性 6 位配对码接入；手机既可只读浏览，也可在平板允许时进入管理模式编辑菜单。

- 阶段一：平板单机可用（菜单展示 + 平板内管理 + 库存沽清 + 软 Kiosk）—— 已完成
- 阶段二：平板开启局域网服务（REST / WS、配对与鉴权、前台保活、在线设备）—— 已完成
- 阶段三：手机客户端（角色选择、一次性配对码、只读浏览、管理模式编辑）—— 已完成
- 阶段四：动画与打磨（推荐 / 新品、沽清动画、空闲自动滚动）—— 已完成（启动整页淡入按性能门禁回退为静态）

完整的开发计划、架构与接口契约、测试计划见 [docs/PLAN.md](docs/PLAN.md)。

设备实测基线见 [docs/device-baseline.md](docs/device-baseline.md)。

架构与关键逻辑见 [docs/architecture.md](docs/architecture.md)。

功能设计文档见 [docs/design/README.md](docs/design/README.md)。

> 当前状态：Phase 1-4 已完成；Android 9+ 局域网明文 HTTP 兼容问题已修复，Android 16 手机可正常配对和控制平板。

## 快速开始（工具链与真机）

首次换机或工具链缺失时先检查环境：

```bat
scripts\doctor.bat
```

若缺少 JDK / SDK / Gradle 或 PATH 未配置：

```bat
rem 普通终端即可，无需管理员；脚本会自动探测本地代理
scripts\setup-toolchain.bat
rem 需要手动指定代理时：scripts\setup-toolchain.bat -ProxyUrl http://127.0.0.1:7890
```

连接设备后确认状态：

```bat
adb devices -l
```

平板或手机显示 `unauthorized` 时，需要在设备屏幕上允许 USB 调试。当前工具链默认安装位置：

- JDK：`C:\Program Files\Eclipse Adoptium\jdk-17*`
- Android SDK：`D:\Android\Sdk`
- Gradle：`D:\Android\gradle-8.9`

## 构建与打包

标准流程会构建 Debug APK、安装到第一台在线设备并做启动自检：

```bat
scriptsuild-and-install.bat
```

只构建、不安装：

```bat
scriptsuild-and-install.bat -SkipInstall
```

也可以直接调用 Gradle wrapper：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME = 'D:\Android\Sdk'
$env:ANDROID_SDK_ROOT = 'D:\Android\Sdk'

.\gradlew.bat :app:assembleDebug
```

APK 产物：

```text
appuild\outputspk\debugpp-debug.apk
```

当前版本使用 debug 签名 APK 分发。`assembleRelease` 尚未配置正式 keystore，因此暂时不能生成可直接发布的正式签名包。

### 多设备安装

`build-and-install` 只选择 `adb devices` 中的第一台在线设备。手机和平板同时连接时，先只构建，再按序列号分别安装：

```bat
scriptsuild-and-install.bat -SkipInstall
adb devices -l
adb -s <serial> install -r appuild\outputspk\debugpp-debug.apk
```

覆盖安装参数 `-r` 会保留应用数据。换机、清数据或恢复菜单前，建议先在平板设置中导出备份。

## 测试

JVM 单测：

```bat
.\gradlew.bat :app:testDebugUnitTest
```

真机仪表测试：

```bat
set ANDROID_SERIAL=<serial>
.\gradlew.bat :app:connectedDebugAndroidTest
```

完整的构建、签名、安装、升级与排障规则见 [docs/design/0005-发布分发与现场排障.md](docs/design/0005-发布分发与现场排障.md)。

## 脚本说明

- `scripts\setup-toolchain.bat`：首次安装并配置 JDK / Android SDK / Gradle。
- `scripts\doctor.bat`：只读检查工具链、路径和 wrapper。
- `scripts\device-precheck.bat`：检查真机连接并采集设备基线。
- `scriptsuild-and-install.bat`：构建 Debug APK、安装到第一台设备并自检启动。
- `scripts\collect-logs.bat`：收集设备信息、logcat 和应用日志，默认输出到 `dist\logs\<时间戳>\`。

每个 `.bat` 只是同名 `.ps1` 的入口封装，参数会原样透传。
