# TanPaiMenu 摆摊电子菜单 —— 开发计划

> 版本：v1.10 ｜ 状态：Phase 2 已完成（M6-M9），Phase 3 已完成（M10、M11）｜ 更新日期：2026-10-07

修订历史：

| 版本 | 日期 | 变更 |
| --- | --- | --- |
| v1.1 | 2026-10-05 | Phase 0 收尾：设备基线归档与计划状态。 |
| v1.2 | 2026-10-05 | 接口 / 数据契约细节拆分到 `docs/design/`；新增各阶段文档产出清单；修订过时条目。 |
| v1.3 | 2026-10-05 | Phase 1 完成：数据层、展示页、管理页、软 Kiosk、图片与备份、日志与性能实测；设计文档回填实现位置。 |
| v1.4 | 2026-10-05 | Phase 2 启动：主机侧设计定稿（0002 转已确认），拆解 M6-M9 子阶段。 |
| v1.5 | 2026-10-05 | 展示页紧凑化（一屏 12 道）与默认菜单改写为大金馄饨 12 道；新增 0006 设计文档。 |
| v1.6 | 2026-10-06 | 展示页默认喜庆红背景（蒙版 25%）、整数价格省略小数位、内置默认微信二维码与设置弹窗排版修正；新增 0011 设计文档。 |
| v1.7 | 2026-10-06 | M7 完成：局域网 REST / WS、Bearer 鉴权与幂等、设备注册表与库 1→2 迁移、开机自启与管理页配对信息；请求体按 UTF-8 显式解码修复中文乱码；新增 0015 设计文档。 |
| v1.8 | 2026-10-07 | M8 完成：主机前台服务保活与启停开关、在线设备列表、WS 心跳修复；门禁补齐修改 / 关闭 / 重置管理密码；新增 0016、0017 设计文档。 |
| v1.9 | 2026-10-07 | Phase 2 收尾（M9 随 M10 交付）；Phase 3 拆分 M10 / M11：M10 交付角色选择、一次性 6 位配对码与手机客户端只读浏览；新增 0018、0019 设计文档。 |
| v1.10 | 2026-10-07 | M11 完成：手机端编辑（分类 / 菜品增删改、卖出 / 撤销 / 沽清、传图、恢复默认菜单）、主机「允许手机编辑」开关（`X-Device-Role` + 403）、`/state` 店名下发与撤销语义改为全局最后一条；新增 0020 设计文档。 |

## 1. 项目概述

TanPaiMenu 是一款面向移动摊位的安卓电子菜单应用。摊位上用一块平板作为顾客可见的电子菜单，摊主通过平板本身或同一局域网内的安卓手机维护菜名、价格、图片与库存余量。

核心形态：

- **顾客侧**：横竖屏都可展示（方向跟随重力感应），顶部店名招牌 + 菜品网格自动按屏幕宽高分档（单行三列 / 两行卡片），远距离可读；余量为 0 时自动灰化并在卡片行尾显示“已售完”徽标。见 `docs/design/0013-横竖屏与多尺寸自适应.md`。
- **摊主侧**：平板内管理页（PIN 保护）；局域网手机装同一个 APK 切“客户端模式”，扫码配对后可完整编辑菜单（含新增菜品、改价、传图、沽清与撤销）。
- **数据归属**：平板是唯一数据源，手机是镜像 + 遥控器；变更通过局域网实时同步。

## 2. 目标与范围

### 2.1 本期目标

1. 一块 Android 7 平板上能独立跑通“建菜单 → 展示 → 卖出减量 → 沽清”，离线可用、重启不丢数据。
2. 平板可锁定在展示页，顾客无法误退；摊主凭 PIN 才能进入管理。
3. 同一局域网下的安卓手机扫码接入，完整编辑菜单（含图片），改动秒级同步到平板。
4. 交付一份其他人可直接照着实施的计划文档（即本文档）。

### 2.2 明确的非目标（本期不做）

- 不做点单、下单、支付、收银、叫号、小票打印。
- 不做多摊位 / 多主机互备，不做云端账号，不做跨互联网的远程访问。
- 不做会员、营销、报表分析等经营系统功能。
- 规划阶段不产出业务代码；Phase 0 只交付最小 Hello World 工程骨架与运维脚本，不含 CI 与签名发布流程。

### 2.3 后续可扩展（不在本期承诺）

- 展示页动画与图片轮播（Phase 4 已预留，属于本期最后一个阶段）。
- 多平板同步展示、销量统计看板、图片素材库。

## 3. 环境与设备基线

### 3.1 已探明的现状

| 项 | 现状 |
| --- | --- |
| 项目目录 `D:\Project\TanPaiMenu` | 已是 git 仓库（`main`），含最小 Compose 工程骨架与运维脚本 |
| JDK | Temurin 17.0.20.1，`C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot` |
| Android SDK | `D:\Android\Sdk`：platform-tools 37.0.1、platforms 34/35、build-tools 35.0.0、cmdline-tools |
| Gradle | 8.9，`D:\Android\gradle-8.9`，工程已带 wrapper |
| 环境变量 | 用户级 `JAVA_HOME` / `ANDROID_HOME` / `ANDROID_SDK_ROOT` 已写入；PATH 已含 jdk、platform-tools、cmdline-tools、gradle |
| 网络 | 直连 `dl.google.com` 不稳定；安装期走本地代理 `127.0.0.1:7890` + 阿里云 / 腾讯镜像 |
| 目标平板 | Teclast T10(E3C6)，Android 7.0（API 24），arm64-v8a，3955MB，1600x2560，密度 320→426 |

### 3.2 版本基线

| 配置项 | 取值 | 说明 |
| --- | --- | --- |
| `minSdk` | 24 | 平板是 Android 7；同时是 Compose 覆盖老设备的下限 |
| `targetSdk` | 34 | 减少新策略摩擦；运行期行为按设备 API 24 生效 |
| `compileSdk` | 35 | 仅编译用；对应 build-tools 35.0.0 已安装 |
| JDK | Temurin 17.0.20.1 | AGP 8.x 的硬性要求；已安装 |
| AGP / Gradle / Kotlin | 8.7.3 / 8.9 / 2.0.21 | 已落地到工程；Kotlin 2.x 自带 Compose 编译器插件 |
| UI | Jetpack Compose + Material 3 | 老平板需扁平化，见第 8 节性能门禁 |

### 3.3 环境搭建步骤

```powershell
# 一键完成（幂等，可重复执行；普通终端即可，无需管理员）
scripts\setup-toolchain.bat                  # JDK 17 + Android SDK + Gradle + wrapper
scripts\setup-toolchain.bat -ConfigureOnly   # 仅修 PATH / 环境变量 / wrapper 镜像
scripts\doctor.bat                           # 只读自检
scripts\device-precheck.bat                  # 设备能力 -> docs\device-baseline.md
scripts\build-and-install.bat                # 构建 + 装机 + 启动自检

# 直连不通时脚本会自动探测本地代理端口（默认 7890），也可显式指定：
scripts\setup-toolchain.bat -ProxyUrl http://127.0.0.1:7890
```

实际落盘：JDK `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`、SDK `D:\Android\Sdk`、Gradle `D:\Android\gradle-8.9`。
实际安装的 SDK 组件：`platform-tools`、`platforms;android-34`、`platforms;android-35`、`build-tools;35.0.0`、`cmdline-tools;latest`。

**镜像兜底**：Maven 依赖走阿里云镜像（写入 `settings.gradle.kts` 的 `pluginManagement` 与 `dependencyResolutionManagement`）；Android SDK 组件走腾讯 / 清华镜像。两者都不通时，需向用户申请联网授权后重试。

**预检产出**：已归档到 `docs/device-baseline.md`（API 24 / arm64-v8a / 3955MB / 1600x2560 / 密度 320→426），据此确定单 ABI 打包与展示页字号。

## 4. 系统架构与接口契约

### 4.1 角色模型

同一个 APK，首次启动选择角色，设置页可切换：

- **主机（平板）**：持有 Room 数据库、HTTP/WS 服务、前台服务保活；是唯一的数据真相与唯一写者。
- **客户端（手机）**：不落库，内存镜像主机状态；通过 REST 提交命令，通过 WS 接收推送。

两种角色共用同一套 Compose 展示页与管理页组件，差异只在传输层与权限入口。

### 4.2 通信设计

- **命令走 REST**：语义清晰、可用 `curl` 独立验证、失败易回滚。
- **推送走 WebSocket**：单向广播变更事件，客户端只订阅、不做双向合并。
- **单写者队列**：主机的所有写操作进入串行队列处理，天然消除并发写冲突，无需版本合并算法。
- **一致性**：`version` 为全局单调递增（存 `app_meta`，单写者事务内 +1），快照与 WS 事件都携带；客户端丢弃小于本地版本的过期消息。详见 `docs/design/0001-菜单数据模型与接口契约.md`。
- **配对与鉴权**：主机生成随机 token；二维码内容为 `http://<ip>:<port>/#pair=<token>`；客户端请求带 `Authorization: Bearer <token>`；主机设置页可一键换 token 以踢掉全部客户端。
- **服务发现**：`NsdManager` 自动发现 + 二维码扫码 + 手动输入 IP 三种方式兜底。
- **端口**：默认 8080，可配置（1024-65535）。

### 4.3 REST 接口（v1，前缀 `/api/v1`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/state` | 全量快照（分类 + 菜品 + 版本号），客户端连接后首先调用 |
| GET / POST | `/categories` | 列出 / 新建分类 |
| PATCH / DELETE | `/categories/{id}` | 修改（含排序）/ 删除分类 |
| GET / POST | `/dishes` | 列出 / 新建菜品 |
| PATCH / DELETE | `/dishes/{id}` | 修改 / 删除菜品 |
| POST | `/dishes/{id}/sell` | 余量 -1；减到 0 自动置为沽清 |
| POST | `/dishes/{id}/unsell` | 撤销最近一次卖出（余量 +1，并清除自动沽清） |
| POST | `/pair` | 用一次性 6 位配对码换取长期 token；**无需** Bearer（见 `docs/design/0019`） |
| POST | `/images` | multipart 上传图片，返回可访问的图片 URL |
| GET | `/images/{name}` | 读取图片字节流 |
| GET | `/devices` | 在线客户端列表 |

完整契约（请求 / 响应示例、字段与校验、错误码、幂等）见 [`docs/design/0001-菜单数据模型与接口契约.md`](design/0001-菜单数据模型与接口契约.md)。

### 4.4 WebSocket `/ws`

1. 连接建立后先下发一条全量 `snapshot`。
2. 之后单向下推事件：`dish.updated`、`dish.deleted`、`category.updated`、`category.deleted`、`device.joined`、`device.left`。
3. 每条事件携带递增 `version`；客户端丢弃过期消息，不做合并。
4. 断线重连：重新调用 `/state` 取全量快照；菜单 ≤ 30 道，不做增量补齐。

报文字段与重连规则见 [`docs/design/0001-菜单数据模型与接口契约.md`](design/0001-菜单数据模型与接口契约.md)。

### 4.5 客户端行为

- 接入：首启选「手机客户端」，输入平板地址（`IP:端口` 或 `http://IP:端口`）与一次性 6 位配对码换取长期 token；不做扫码与 `NsdManager` 发现（列入后续可扩展）。
- 同步：配对后先拉 `GET /state` 全量快照，再连 `/ws` 订阅增量；`version` 不大于本地的事件直接丢弃。
- 连接断开：顶部显示只读条幅并保留最后一次同步的菜单，按 1/2/4/8/15 秒退避后固定 15 秒重试；配对失效（401 / WS 4401）停止重试并提示重新配对。
- 编辑（M11 已实现）：菜单页「管理模式」开关；分类增删改（长按改 / 删）、菜品增删改、卖出 / 撤销 / 沽清、恢复默认菜单；图片在手机侧压缩后经 `POST /images` 上传。提交采用「提交即禁用 + 等广播对齐」，**不做乐观更新与回滚**；离线或主机 403 时给出中文提示。平板可用「允许手机编辑」开关随时收回手机写权限。

## 5. 数据模型（Room 实体）

### 5.1 实体

| 实体 | 字段 |
| --- | --- |
| `Category` | id、name、sortOrder、enabled、updatedAt |
| `Dish` | id、categoryId、name、priceCents、unit、imagePath、stockEnabled、stockCount、soldOut、description、sortOrder、enabled、updatedAt |
| `SaleRecord` | id、dishId、qty、createdAt、deviceId（支撑卖出与撤销，以及今日销量） |
| `ConnectedDevice` | id、name、role、lastSeen |

### 5.2 约定

- **价格以分为单位存整数**，避免浮点误差；展示层再格式化为 `¥12`（整数省略小数位，有角分时保留两位）。
- **沽清优先于库存**：`soldOut = true` 时展示为已售完；库存为 0 时自动置 `soldOut`。
- **撤销粒度**：以 `SaleRecord` 最后一条为准，支持连续撤销。
- **图片**：压缩至长边不超过 1280px、JPEG 质量 80，存 `filesDir/images/<uuid>.jpg`；菜品图非必填，无图时显示菜名首字占位卡。
- **设置项**（SharedPreferences）：店名、adminPin（加盐 SHA-256，不存明文）、pairToken、端口、开机自启开关。
- **备份**：支持整库导出 / 导入 JSON 文件，用于换机与误操作恢复。
- **主键与时间**：主键为自增 `Long`；时间字段存 UTC epoch 毫秒，JSON 传输用 ISO-8601 UTC 字符串。
- **版本号**：全局单调递增 `version` 存于 `app_meta` 表，业务实体不再各自维护。
- **删除级联**：删除分类时其下菜品软删除（`enabled = false`）；历史 `SaleRecord` 保留以支持撤销与销量统计。

字段类型与约束、完整 REST / WS 报文见 [`docs/design/0001-菜单数据模型与接口契约.md`](design/0001-菜单数据模型与接口契约.md)。

## 6. 分阶段实施与验收

### Phase 0：环境与预检 —— 已完成（2026-10-05）

- 工具链就绪：JDK 17.0.20.1、Android SDK（platform-tools 37.0.1 / platforms 34+35 / build-tools 35.0.0）、Gradle 8.9，wrapper 已生成并指向国内镜像。
- 设备预检完成：Teclast T10(E3C6) / API 24 / arm64-v8a / 3955MB / 1600x2560 / 密度 320→426，归档于 `docs/device-baseline.md`。
- 最小工程跑通：Compose Hello World 构建出 `app-debug.apk`（约 9.2MB），`adb install` 返回 Success，启动后进程存活且 crash 缓冲区无 `FATAL EXCEPTION`。
- 运维脚本：`scripts\setup-toolchain.bat`、`doctor.bat`、`device-precheck.bat`、`build-and-install.bat`（实现为同名 `.ps1`）。

**文档产出**：`docs/device-baseline.md`（已完成）、`docs/architecture.md`（骨架）。

**验收**：`adb install` 成功、平板能启动应用；设备能力记录已归档 → 达成。

### Phase 1：平板单机 MVP —— 已完成（2026-10-05）

- Room 数据层：实体、DAO、Repository，首启写入一份示例菜单。
- 展示页：顶部条（店名 + 分类胶囊）+ `LazyVerticalGrid` 菜品卡（图、名、价、余量角标）；沽清卡片灰化并在行尾显示「已售完」徽标。
- 管理页（PIN 进入）：分类增删改与排序、菜品增删改、改价、传图（相册 / 拍照）、一键卖出、沽清与恢复、撤销上一次卖出。
- 软 Kiosk：开机自启（`BOOT_COMPLETED`）、屏幕常亮、沉浸式全屏隐藏系统栏、可选注册为 Home 以拦截 Home 键、长按店名 2 秒弹出 PIN 才能退出。
- 菜单 JSON 导出 / 导入备份。

**文档产出**：`docs/design/0001-菜单数据模型与接口契约.md`、`0003-平板运行形态与门禁.md`、`0004-图片处理与数据备份.md`；更新 `docs/architecture.md` 分层与关键流程章节。

**验收**：平板断网状态下可完成建菜单到沽清的完整流程（建菜单 → 展示 → 卖出减量 → 沽清），重启后数据完整；顾客无法通过 Home / 返回键退出。

**实现与验证（2026-10-05，真机 Teclast T10 / API 24）**：

- 数据层 `data/`：`Entities.kt`、`TpDatabase.kt`、各 DAO、`MenuRepository.kt`、`SeedMenu.kt`；Room schema 导出到 `app/schemas/`。
- 展示页 `ui/display/`：顶部条 + `LazyVerticalGrid` 菜品卡；沽清灰化并在行尾显示「已售完」徽标，无图时显示菜名首字占位。
- 管理页 `ui/admin/`：PIN 门禁、分类与菜品增删改、传图（相册 / 拍照）、卖出 / 撤销 / 沽清。
- 软 Kiosk `kiosk/`：`BOOT_COMPLETED` 自启、常亮、沉浸式、可选 Home、退出到桌面、长按店名 2 秒进入 PIN。
- 图片与备份：`imaging/ImageStore.kt`（长边 1280、JPEG 80）、`backup/`（`backup.json` + `images/`，format 1）。
- 日志与排障：`logging/TpLog.kt`、`scripts/collect-logs.ps1|.bat`。
- 单测 24 项全通过；真机实测冷启动 502–545ms、网格滚动 p95 16ms、内存稳定在 78MB（详见第 8 节）。

**展示页修订（2026-10-05，见 `docs/design/0006-展示页紧凑布局与默认菜单.md`）**：

- 侧栏 240dp → 150dp → 移除（改为 44dp 顶部条，2026-10-05）；菜品区 2 列 × 6 行横向长卡（56dp 左方图 + 菜名 22sp / 价格 20sp / 有货标识一行），12 道一屏，>12 道滚动。
- 有货标识可在设置里整体隐藏；未启用库存的菜品在管理页隐藏「卖出 / 撤销」。
- 默认店名「大金馄饨」，默认菜单为单分类「馄饨」12 道（不启用库存）；设置页新增「恢复默认菜单」。
- 内置 Noto Sans SC Bold（OFL 1.1，8.5MB）用于店名、分类名与菜名。
- 验证（2026-10-05）：JVM 单测 39 项、仪表测试 12 项通过；真机 2560×1472 一屏 12 道无滚动；开关与恢复默认菜单实测生效；APK 18,713,564 字节。
- 沽清标识改为卡片行尾「已售完」徽标，不再用居中印章覆盖菜名；该徽标不随「显示是否有货」开关隐藏。
- 卡内三列定宽左对齐（名称弹性 / 价格 100dp / 状态 60dp），菜名超宽时用跑马灯滚动显示，价格与状态不动。
### Phase 2：局域网主机 —— 进行中（M6 起）

- 集成 NanoHTTPD：实现 4.3 与 4.4 的全部接口，写操作进入单写者队列。
- 配对页：展示当前 IP、端口与配对二维码，支持换 token。
- 图片上传通道与静态图片读取。
- 前台服务保活，避免息屏后被系统回收；平板设置页展示启停开关与在线设备列表（M8 已交付）。

**子阶段拆解（详见 `docs/design/0002-局域网配对与同步.md` 第 8 节）**：

| 子阶段 | 内容 | 状态 |
| --- | --- | --- |
| M6 | 契约 DTO / 映射 / ISO-8601（`net/protocol`）、单写者锁与 `MenuChange` 事件流（`data`） | 已完成 |
| M7 | NanoHTTPD REST + WS、鉴权与幂等缓存、设备注册表与库迁移 | 已完成 |
| M8 | 前台服务保活、启停开关、在线设备列表（配对信息展示已随 M7 交付） | 已完成 |
| M9 | JVM 契约测试与真机多端验收、文档回填 | 已完成（随 M10 一并交付） |

**M6 实现与验证（2026-10-05）**：

- 契约层 `net/protocol/`：`Contract.kt`、`Iso8601.kt`、`Mapping.kt`。
- 单写者 `data/MenuRepository.kt`（`writeMutex`）与事件流 `data/MenuChange.kt`（提交后按序发出，回滚不发）。
- 验证：JVM 单测 36 项通过；真机 Teclast T10 / API 24 仪表测试 11 项通过（含 10 并发卖出串行化）。
**M7 实现与验证（2026-10-06）**：

- 主机服务 `host/`：`HostApi`（路由 / 鉴权 / 幂等 / 校验 / 错误码）、`HostServer`（NanoWSD HTTP + WS 适配、端口顺延）、`WsHub`（连接注册表与广播）、`RepositoryMenuBackend`、`HostInfo`、`HostStatus`。
- 数据与设置：`connected_device` 表 + `DeviceDao`、库 1 → 2 迁移；`SettingsStore.pairToken` / `serverPort`；`ImageStore.importFromBytes`。
- 装配与 UI：`App.onCreate` 自启服务并写日志；管理页设置弹窗「手机控制端」显示地址、配对码、二维码与「更换配对码」。
- 依赖：`nanohttpd` + `nanohttpd-websocket` 2.3.1、`zxing core` 3.5.3；新增 `INTERNET` / `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` 权限。
- 验证：JVM 单测 86 项通过（新增 29 项）；平板仪表测试 41 项通过（新增 `HostServerTest` 9、迁移 1）。

**M8 实现与验证（2026-10-07）**：

- 保活：`host/HostService.kt` 前台服务（`START_STICKY` + 常驻通知）拉起并维持 `HostServer`；`App.onCreate` 按 `SettingsStore.hostEnabled` 启停；关闭开关即停监听、关闭全部 WS（码 1001）并移除通知；服务启动后清理 7 天前的设备记录。
- 在线设备：设置弹窗「手机控制端」新增启停开关与「在线设备（N）」列表（数据源为实时 WS 连接快照，弹窗可见期间每 2 秒刷新）；关闭时显示「已关闭」。
- 修复：WS 读超时 5s → 60s 并新增 20 秒周期 ping（真机上空闲连接此前约 1 分钟内静默掉线）；`HostApi` / `HostServer` 日志回调拆为 `logInfo` / `logError`。
- 门禁（0017）：管理页可修改管理密码、关闭密码保护；忘记密码长按 3 秒 → 二次确认 → 清除密码并强制重设，菜单与图片不受影响。
- 验证：JVM 单测 88 项、平板仪表测试 46 项全绿；真机确认前台服务常驻（`isForeground=true`）、通知随开关增减、`/state` 鉴权正常、在线列表随 WS 连接增减、空闲 80 秒不掉线。

**文档产出**：`docs/design/0016-主机服务保活与在线设备列表.md`、`docs/design/0017-管理密码重置与修改.md`；更新 `0002`（M8 实现位置与在线设备数据源修正）、`0003`（门禁修订）与 `docs/architecture.md`。

**文档产出**：`docs/design/0015-主机服务接口实现.md`；更新 `0001`（WS 连接参数）、`0002`（M7 实现位置）与 `docs/architecture.md`。

**文档产出**：`docs/design/0002-局域网配对与同步.md`；更新 `docs/architecture.md` 关键流程与数据流章节。

**验收**：用 `curl` 与浏览器逐条验证接口契约；token 错误返回 401；两台设备连接后均能收到广播。

### Phase 3：手机客户端 —— 已完成（M10、M11）

同一个 APK 增加「手机客户端」角色：首启选角色，客户端不落库、不启前台服务，凭平板地址 + 一次性 6 位配对码接入，只读浏览菜单并实时跟随平板变更。

**子阶段拆解**：

| 子阶段 | 内容 | 状态 |
| --- | --- | --- |
| M10 | 角色选择与运行形态分流（0018）、一次性 6 位配对码与 `POST /pair`、客户端只读浏览与断线重连（0019） | 已完成 |
| M11 | 客户端编辑：新增 / 改价 / 传图 / 卖出 / 撤销 / 沽清、恢复默认菜单；提交即禁用 + 等广播对齐（不做乐观更新与回滚） | 已完成 |

**M10 实现与验证（2026-10-07）**：

- 角色与分流：`data/Role.kt` + `SettingsStore.role`；`App.applyRole` / `enterHostMode`；`MainActivity` 按角色渲染 `RoleSelectScreen` / `TanPaiMenuApp` / `ui/client/ClientApp`，并按角色切换系统栏；`BootReceiver` 仅主机形态自启。
- 配对：`host/PairCodeManager`（6 位、5 分钟有效、一次性、连续失败 5 次锁定 60 秒）与 `HostApi.handlePair`（显式跳过鉴权）；管理页设置弹窗新增「显示配对码」区块（大号码 + 剩余时间 + 最近配对设备），原 64 位长期令牌改名保留。
- 客户端栈：`net/HostAddress`、`net/HostRestClient`、`net/MenuSocketClient`、`client/ClientMirror`、`client/ClientSession`、`client/PairingClient`、`client/ReconnectionPolicy`；界面 `ui/client/`（配对页 / 只读菜单页 / 设置弹窗）。
- 依赖：OkHttp 由 Coil 传递依赖升为直接依赖 `libs.okhttp.core`（无新增下载）。
- 验证：JVM 单测 128 项通过（新增 `PairCodeManagerTest`、`HostAddressTest`、`EventPayloadTest`、`ClientMirrorTest`、`ReconnectionPolicyTest`，以及 `HostApiTest` 的 `/pair` 用例）；平板仪表测试 47 项通过（新增 `ClientSessionTest`：真机连 `127.0.0.1` 跑通「配对 → 快照 → 改价收 `dish.updated` → 停服离线只读 → 重启自愈」）。
- 修复：`HostRestClient` 统一在内部规范化主机地址（此前 `GET /state` 直接拼接用户输入原文，导致真机上「缺 scheme」连接失败，由 `ClientSessionTest` 捕获）。

**文档产出**：`docs/design/0018-角色选择与客户端运行形态.md`、`docs/design/0019-手机客户端配对与只读浏览.md`；更新 `0001`（`POST /pair` 契约）、`0002`（客户端接入）、`0015`（路由与非鉴权端点）与 `docs/architecture.md`。

**验收**：平板显示 6 位配对码，手机输入平板地址 + 码即看到 12 道菜；平板改价手机 1 秒内更新；手机断网出现只读条幅、恢复后自动重连；平板关闭「手机控制端」手机离线、重新打开自愈；平板在线设备列表可见该手机。

**M11 实现与验证（2026-10-07）**：

- 主机侧：`POST /menu/reset`（`MenuBackend.resetMenu` → `MenuRepository.resetToSeedMenu` → `FullReload` → 广播 `snapshot`）；「允许手机编辑」判定（写方法 + `X-Device-Role: client` + `SettingsStore.clientEditsEnabled` 关闭 → 403 `forbidden`，拒绝前先丢弃请求体以保护 keep-alive 连接）；`/state` 增加 `storeName` / `clientEditsEnabled`；管理页设置弹窗新增开关，`HostStatusProvider.setClientEditsEnabled` 落盘并广播快照。
- 客户端：`client/ClientCommand`（命令 → 契约请求，带唯一 `requestId`）、`client/ClientCommandExecutor`、`ClientSession.submit/uploadImage`（提交即禁用、等广播对齐，超时拉 `/state` 兜底）、`imaging/ImageCompressor` 与 `imaging/Bitmaps`、`net/HostRestClient` 全部写方法与 `POST /images` 上传。
- 界面：`ui/client/ClientMenuScreen`（管理模式：分类与菜品增删改、卖出 / 撤销 / 沽清、新增菜品、恢复默认菜单、失败 Snackbar）、`ui/client/ClientApp`、`ui/client/ClientSettingsDialog`；通用对话框迁到 `ui/dialogs/MenuDialogs.kt`，由 `ui/admin` 与 `ui/client` 共用。
- 撤销语义变更：改为「全局最后一条可撤销记录」，平板与手机可互相撤销（`SaleRecordDao.getActiveFor` 去掉 deviceId 过滤）。
- 验证：JVM 单测 139 项通过（新增 `ClientCommandTest` 9 项与 `HostApiTest` 2 项）；平板仪表测试 53 项通过（`ClientSessionTest` 跑通「改价 → 沽清 → 卖出 → 撤销 → 新增菜品 → 分类改名 / 新增 / 删除 → 上传图片 → 恢复默认菜单」、离线提交失败、开关关闭后 403；`HostServerTest` 覆盖 403 判定与 `/menu/reset` 广播）。
- 实现中修复（仪表测试暴露）：① 图片上传原用 OkHttp 默认的 `multipart/mixed`，NanoHTTPD 只认 `multipart/form-data`，导致恒返回「缺少 file 字段」；② 403 未读取请求体，客户端复用同一条 keep-alive 连接时残留字节被解析成脏请求行（一次 403 之后该连接上的请求全部 400 语法错误）。

**文档产出**：`docs/design/0020-手机客户端编辑.md`；更新 `0001`（`/menu/reset`、`forbidden`、`/state` 新字段、身份头、撤销语义）、`0002`（同步语义改为提交即禁用、编辑开关）、`0015`（角色判定与新增路由）、`0019`（店名偏差已修复）与 `docs/architecture.md`。

**验收**：手机进管理模式完成日常操作（改价 / 沽清 / 卖出 / 撤销 / 增删菜 / 传图），平板 1 秒内同步；平板关闭「允许手机编辑」后手机提交被拒并提示、重开即恢复；恢复默认菜单两端一致显示 12 道菜。

### Phase 4：动画与打磨（最后做）

- 沽清盖章动画、新品 / 推荐高亮、无操作时的图片轮播或轻量自动滚动、开机过渡。
- 每一项动画都必须通过性能门禁，不达标即回退为静态效果。

**文档产出**：`docs/design/0005-发布分发与现场排障.md`；更新 `docs/architecture.md` 构建与发布章节。

**验收**：加入动画后滚动帧率与冷启动时间仍满足第 8 节门禁。

### 横切工作

- 统一日志与错误提示；服务异常自恢复。
- `adb` 一键安装脚本，降低迭代成本。
- 日志落盘与一键收集脚本（`scripts/collect-logs.*`，Phase 1 交付）；日志格式与错误码见 `docs/design/0001`、`docs/architecture.md`。

## 7. 测试计划

### 7.1 单元测试

- 库存扣减边界：0 份时卖出被拒、连续减到 0 自动沽清、沽清后卖出被拒、`unsell` 恢复语义。
- 撤销语义：连续撤销、无记录时撤销的错误处理。
- 价格格式化：分转元、超大值与 0 的显示。
- 序列化契约：REST / WS 报文的往返一致性。
- 鉴权：token 正确 / 错误 / 缺失；换 token 后旧 token 立即失效。

### 7.2 集成测试（JVM）

- 把 HTTP 服务起在随机端口，对 4.3 全部接口跑契约测试。
- 并发 `sell` 同一菜品：验证串行化生效、最终余量准确、无丢单。
- WS 广播：连接两个客户端，验证变更同时到达且 `version` 单调递增。

### 7.3 Android 仪表测试

- Room DAO 与数据库迁移。
- 展示页与管理页 Compose UI 测试。
- 沉浸式模式与 PIN 门禁行为。

### 7.4 真机手工验收清单

1. 重启平板后应用自启并停在展示页。
2. 长时间常亮不熄屏；顾客乱点无法退出到桌面。
3. 手机改价 / 沽清 / 传图，平板展示页秒级同步。
4. 断开 WiFi：手机显示只读；恢复后自动重连。
5. 图片上传后展示正确、无拉伸变形。
6. 导出备份 → 清除数据 → 导入备份，菜单完整还原。
7. 平板重启、应用被杀后重启，数据不丢。
8. 手机控制端：平板设置页可见常驻通知与「在线设备」列表；关闭开关后手机立即断连，重新打开即恢复。
9. 忘记管理密码时，长按「忘记密码」3 秒并确认后重设密码，菜单与图片不丢。

## 8. 性能门禁（Android 7 平板实测）

| 指标 | 门槛 | 实测（Teclast T10 / API 24） | 不达标时的降级动作 |
| --- | --- | --- | --- |
| 冷启动首屏 | 小于 1.5s | 502-545ms（60 菜品数据集） | 延迟加载图片、减少首屏卡片数 |
| 菜品网格滚动 | 不低于 50fps 且无掉帧尖峰 | 1099 帧，p50 8ms / p95 16ms，janky 4.73%，无慢位图 | 关闭阴影 / 圆角裁剪、降低图片解码尺寸 |
| 滑动时内存 | 稳定不持续增长 | PSS 约 78MB，三档采样稳定 | 收紧 Coil 缓存、释放离屏位图 |

实测数据集为「60 菜品 + 1 图片」的备份导入结果，测完已用原始备份还原；每次发版前用同一数据集复测。

若连续降级仍不达标，则把展示页从 Compose 改写为 View 系统（管理页与客户端不受影响）。

## 9. 风险与对策

| 风险 | 影响 | 对策 |
| --- | --- | --- |
| Android 7 老平板 GPU 弱 | Compose 掉帧、体验差 | 全程扁平 UI、禁用模糊与大阴影、设性能门禁、必要时降级到 View |
| 后台被系统回收 | 服务断、同步停 | 前台服务 + 屏幕常亮 + 引导关闭电池优化（API 24 策略较宽松） |
| 工具链下载不通 | 无法开工 | 腾讯 / 清华 SDK 镜像 + 阿里云 Maven 镜像；必要时申请联网授权 |
| 平板 WiFi 掉线或换 IP | 手机连不上 | 二维码实时显示当前 IP；客户端缓存上次 IP 并重试；可引导使用平板热点 |
| 多台手机同时编辑 | 数据冲突 | 主机单写者队列串行化；客户端乐观更新失败即回滚提示 |
| 无 Google 服务 / WebView 老旧 | 扫码与界面不可用 | 界面全原生、扫码用 ZXing 自带解码，不依赖 GMS 与系统 WebView |
| 误删菜单数据 | 摊主营业中断 | JSON 导出备份 + 导入恢复；删除操作二次确认 |

## 10. 假设与默认值

- 单主机（一块平板）；最多约 5 台客户端同时连接；不做多平板互备。
- 菜单规模不超过 30 道菜、3 到 5 个分类，因此不做搜索与分页，只做分类切换与滚动。
- 界面中文，货币人民币，价格以分整数存储。
- 图片支持相册选择与拍照两种来源，菜品图非必填。
- 管理权限：平板端用 PIN 进入；手机端凭配对 token 授权（可选再开 PIN）。
- 只做展示 + 管理，明确不做点单、收银、叫号、打印。
- 仓库为 git 仓库（`main` 分支，远端 GitHub）；协作规则见 `AGENTS.md`。
- 局域网传输为明文 HTTP + Bearer token，未启用 TLS；建议摊位使用专用网络或平板热点（见 `docs/design/0002`）。
- v1 使用 debug 签名 APK 分发；正式签名与渠道留待 Phase 4（见 `docs/design/0005`）。

## 附录 A：工程目录结构（Phase 0 已创建 app / docs / scripts 三个顶层目录）

```text
TanPaiMenu/
├─ app/
│  └─ src/main/java/com/tanpai/menu/
│     ├─ data/        # Room 实体、DAO、Repository、设置
│     ├─ domain/      # 纯业务规则（卖出 / 撤销策略、价格格式化）
│     ├─ host/        # 主机侧：HTTP/WS 服务、单写者队列、配对
│     ├─ client/      # 客户端侧：发现、连接、镜像状态
│     ├─ net/         # REST 客户端、WS 客户端、DTO 与序列化
│     ├─ ui/display/  # 顾客展示页
│     ├─ ui/admin/    # 管理与设置页（双端共用）
│     ├─ ui/client/   # 手机客户端管理页（复用 admin 组件）
│     ├─ imaging/     # 压缩、存储、加载
│     └─ kiosk/       # 自启、常亮、沉浸、PIN 门禁
├─ docs/
│  ├─ PLAN.md          # 计划与阶段（本文件）
│  ├─ architecture.md  # 架构与关键逻辑
│  ├─ design/          # 功能设计文档（NNNN-中文简述.md）
│  └─ device-baseline.md
└─ scripts/           # adb 一键安装等
```

各模块职责与依赖方向见 `docs/architecture.md` 第 2 节。

## 附录 B：待确认事项（实施前需落实）

1. ~~平板 ABI 与内存实测值~~ 已落实：arm64-v8a / 3955MB / 1600x2560 / 密度 426，见 `docs/device-baseline.md`；打包只出 arm64-v8a。
2. 平板能否开热点；若不能，需确保摊位现场有稳定 WiFi。**（仍待确认，Phase 2 前必须落实）**
3. ~~`git init` 与首次提交~~ 已落实：仓库建于 `main` 分支，远端 `git@github.com:Queqiongtao/TanPaiMenu.git`。
4. 手机端是否在 token 之外再加 PIN。**（待确认，默认不加，见 `docs/design/0002`）**
