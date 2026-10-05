# TanPaiMenu

摆摊用的安卓电子菜单应用。平板作为顾客可见的电子菜单并充当主机，同一局域网下的安卓手机装同一个 APK 切客户端模式，扫码配对后即可编辑菜单。

- 阶段一：平板单机可用（菜单展示 + 平板内管理 + 库存沽清 + 软 Kiosk）
- 阶段二：平板开启局域网服务，手机端实时编辑并同步
- 阶段三：动画与打磨

完整的开发计划、架构与接口契约、测试计划见 [docs/PLAN.md](docs/PLAN.md)。
设备实测基线见 [docs/device-baseline.md](docs/device-baseline.md)。

> 进度：**Phase 0 已完成**（工具链就绪 + 真机预检 + Hello World 装机验证）。

## 快速开始（Phase 0：工具链与真机预检）

仓库当前已包含最小 Compose 工程骨架与运维脚本；工具链尚未安装，需先在放开联网的终端执行：

```bat
rem 普通终端即可，无需管理员；脚本会自动探测本地代理（默认优先 127.0.0.1:7890）

rem 1) 装工具链：JDK 17 + Android SDK + Gradle（幂等，可重复执行）
scripts\setup-toolchain.bat
rem 需要手动指定代理时：scripts\setup-toolchain.bat -ProxyUrl http://127.0.0.1:7890

rem 2) 平板插 USB 并允许调试后，采集设备能力 -> docs\device-baseline.md
scripts\device-precheck.bat

rem 3) 构建 + 安装 + 启动自检
scripts\build-and-install.bat
```

每个 `.bat` 只是同名 `.ps1` 的入口封装（可直接双击），参数原样透传。
默认落盘位置：JDK 由 winget 装到 `C:\Program Files\Eclipse Adoptium\jdk-17*`（找不到时才用 `D:\Android\jdk-17`）、
Android SDK `D:\Android\Sdk`、Gradle `D:\Android\gradle-8.9`。

### 自检与修复

```bat
rem 只读自检：不改任何东西，报告缺什么
scripts\doctor.bat

rem 只修配置：补 PATH、重写 wrapper 镜像，不下载
scripts\setup-toolchain.bat -ConfigureOnly
```

真机连上但 `adb devices` 显示 `unauthorized` 时，需要在平板屏幕上点「允许 USB 调试」。
