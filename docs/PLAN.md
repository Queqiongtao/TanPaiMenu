# TanPaiMenu 摆摊电子菜单 —— 开发计划

> 版本：v1.0 ｜ 状态：待实施 ｜ 更新日期：2026-10-04

## 1. 项目概述

TanPaiMenu 是一款面向移动摊位的安卓电子菜单应用。摊位上用一块平板作为顾客可见的电子菜单，摊主通过平板本身或同一局域网内的安卓手机维护菜名、价格、图片与库存余量。

核心形态：

- **顾客侧**：横屏展示，左侧分类导航 + 右侧菜品网格，远距离可读；余量为 0 时自动灰化并盖“已售完”章。
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
- 本期只产出文档，不产出工程骨架代码、CI 与签名发布流程。

### 2.3 后续可扩展（不在本期承诺）

- 展示页动画与图片轮播（Phase 4 已预留，属于本期最后一个阶段）。
- 多平板同步展示、销量统计看板、图片素材库。

## 3. 环境与设备基线

### 3.1 已探明的现状

| 项 | 现状 |
| --- | --- |
| 项目目录 `D:\Project\TanPaiMenu` | 全新空目录，非 git 仓库 |
| JDK / Android SDK / Android Studio | 均未安装 |
| gradle / adb / flutter | 均不在 PATH |
| 已具备 | `winget`、Git、Node v24、.NET 10、Python 3.10 |
| 外网 | 访问 `dl.google.com` 失败，需镜像或联网授权 |
| 目标平板 | Android 7（API 24），已确认可走 USB 调试 |

### 3.2 版本基线

| 配置项 | 取值 | 说明 |
| --- | --- | --- |
| `minSdk` | 24 | 平板是 Android 7；同时是 Compose 覆盖老设备的下限 |
| `targetSdk` | 34 | 减少新策略摩擦；运行期行为按设备 API 24 生效 |
| `compileSdk` | 35 | 仅编译用，可按工具链可用版本微调 |
| JDK | Temurin 17 | AGP 8.x 的硬性要求 |
| AGP / Gradle / Kotlin | 8.x / 8.x / 2.x | Kotlin 2.x 自带 Compose 编译器插件 |
| UI | Jetpack Compose + Material 3 | 老平板需扁平化，见第 8 节性能门禁 |

### 3.3 环境搭建步骤

```powershell
# 1) JDK 17
winget install --id EclipseAdoptium.Temurin.17.JDK -e

# 2) Android 命令行工具：下载后解压到 %LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest
#    直连不通时使用镜像（如腾讯云 Android SDK 镜像 / 清华 AOSP 镜像）

# 3) 安装 SDK 组件并配置环境变量
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path += ";$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin"
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
adb version

# 4) 设备预检：记录平板真实能力
adb devices
adb shell getprop ro.build.version.sdk      # 预期 24
adb shell getprop ro.product.cpu.abi        # arm64-v8a 或 armeabi-v7a
adb shell cat /proc/meminfo | Select-String "MemTotal"
adb shell wm size ; adb shell wm density
```

**镜像兜底**：Maven 依赖走阿里云镜像（写入 `settings.gradle.kts` 的 `pluginManagement` 与 `dependencyResolutionManagement`）；Android SDK 组件走腾讯 / 清华镜像。两者都不通时，需向用户申请联网授权后重试。

**预检产出**：设备能力记录（API 版本、ABI、内存、分辨率、密度），用于决定是否需要按单 ABI 打包、以及展示页字号与卡片尺寸。
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
- **一致性**：每条变更带自增 `version`，客户端丢弃小于本地版本的过期消息。
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
| POST | `/images` | multipart 上传图片，返回可访问的图片 URL |
| GET | `/images/{name}` | 读取图片字节流 |
| GET | `/devices` | 在线客户端列表 |

### 4.4 WebSocket `/ws`

1. 连接建立后先下发一条全量 `snapshot`。
2. 之后单向下推事件：`dish.updated`、`dish.deleted`、`category.updated`、`category.deleted`、`device.joined`、`device.left`。
3. 每条事件携带递增 `version`；客户端丢弃过期消息，不做合并。

### 4.5 客户端行为

- 连接断开：顶部显示离线条幅（只读），所有编辑入口禁用；缓存上次主机 IP 并自动重试。
- 提交命令：先做乐观更新，主机返回失败时回滚并提示原因。
- 图片上传：手机侧压缩后再传，主机落盘并在广播中下发新 URL。

## 5. 数据模型（Room 实体）

### 5.1 实体

| 实体 | 字段 |
| --- | --- |
| `Category` | id、name、sortOrder、enabled、updatedAt |
| `Dish` | id、categoryId、name、priceCents、unit、imagePath、stockEnabled、stockCount、soldOut、description、sortOrder、enabled、updatedAt、version |
| `SaleRecord` | id、dishId、qty、createdAt、deviceId（支撑卖出与撤销，以及今日销量） |
| `ConnectedDevice` | id、name、role、lastSeen |

### 5.2 约定

- **价格以分为单位存整数**，避免浮点误差；展示层再格式化为 `¥12.00`。
- **沽清优先于库存**：`soldOut = true` 时展示为已售完；库存为 0 时自动置 `soldOut`。
- **撤销粒度**：以 `SaleRecord` 最后一条为准，支持连续撤销。
- **图片**：压缩至长边不超过 1280px、JPEG 质量 80，存 `filesDir/images/<uuid>.jpg`；菜品图非必填，无图时显示菜名首字占位卡。
- **设置项**（SharedPreferences）：店名、adminPin（加盐 SHA-256，不存明文）、pairToken、端口、开机自启开关。
- **备份**：支持整库导出 / 导入 JSON 文件，用于换机与误操作恢复。

## 6. 分阶段实施与验收

### Phase 0：环境与预检

- 安装 JDK 17、Android SDK 命令行工具、platform-tools、`platforms;android-34`、`build-tools;34.0.0`，配置好镜像源。
- 用 `adb` 读取平板 API 版本、ABI、内存、分辨率与密度并记录。
- 建最小工程，跑通 Hello World 安装到真机。

**验收**：`adb install` 成功、平板能启动应用；设备能力记录归档到文档。

### Phase 1：平板单机 MVP

- Room 数据层：实体、DAO、Repository，首启写入一份示例菜单。
- 展示页：左侧分类侧边栏（大字号，可选中高亮）+ 右侧 `LazyVerticalGrid` 菜品卡（图、名、价、余量角标）；沽清卡片灰化并盖已售完章。
- 管理页（PIN 进入）：分类增删改与排序、菜品增删改、改价、传图（相册 / 拍照）、一键卖出、沽清与恢复、撤销上一次卖出。
- 软 Kiosk：开机自启（`BOOT_COMPLETED`）、屏幕常亮、沉浸式全屏隐藏系统栏、可选注册为 Home 以拦截 Home 键、长按标题 5 秒弹出 PIN 才能退出。
- 菜单 JSON 导出 / 导入备份。

**验收**：平板断网状态下可完成建菜单到沽清的完整流程（建菜单 → 展示 → 卖出减量 → 沽清），重启后数据完整；顾客无法通过 Home / 返回键退出。

### Phase 2：局域网主机

- 集成 NanoHTTPD：实现 4.3 与 4.4 的全部接口，写操作进入单写者队列。
- 配对页：展示当前 IP、端口与配对二维码，支持换 token。
- 图片上传通道与静态图片读取。
- 前台服务保活，避免息屏后被系统回收；平板设置页展示在线设备列表。

**验收**：用 `curl` 与浏览器逐条验证接口契约；token 错误返回 401；两台设备连接后均能收到广播。

### Phase 3：手机客户端

- 角色选择页；扫码（zxing-android-embedded）、`NsdManager` 自动发现、手动输入 IP 三种接入方式。
- 客户端管理 UI：完整菜单编辑（含图片上传），复用 Phase 1 的 Compose 组件。
- 乐观更新 + 失败回滚；断线只读条幅与自动重连。

**验收**：手机上改价 / 传图，平板展示页 1 秒内更新；拔网后手机变只读，恢复网络后自动重连并回到可编辑状态。

### Phase 4：动画与打磨（最后做）

- 沽清盖章动画、新品 / 推荐高亮、无操作时的图片轮播或轻量自动滚动、开机过渡。
- 每一项动画都必须通过性能门禁，不达标即回退为静态效果。

**验收**：加入动画后滚动帧率与冷启动时间仍满足第 8 节门禁。

### 横切工作

- 统一日志与错误提示；服务异常自恢复。
- `adb` 一键安装脚本，降低迭代成本。

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

## 8. 性能门禁（Android 7 平板实测）

| 指标 | 门槛 | 不达标时的降级动作 |
| --- | --- | --- |
| 冷启动首屏 | 小于 1.5s | 延迟加载图片、减少首屏卡片数 |
| 菜品网格滚动 | 不低于 50fps 且无掉帧尖峰 | 关闭阴影 / 圆角裁剪、降低图片解码尺寸 |
| 滑动时内存 | 稳定不持续增长 | 收紧 Coil 缓存、释放离屏位图 |

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
- 仓库当前不是 git 仓库；是否 `git init` 由用户决定，本期不擅自初始化。

## 附录 A：未来工程目录结构（规划，本期不创建）

```text
TanPaiMenu/
├─ app/
│  └─ src/main/java/com/tanpai/menu/
│     ├─ data/        # Room 实体、DAO、Repository、设置
│     ├─ host/        # 主机侧：HTTP/WS 服务、单写者队列、配对
│     ├─ client/      # 客户端侧：发现、连接、镜像状态
│     ├─ ui/display/  # 顾客展示页
│     ├─ ui/admin/    # 管理与设置页（双端共用）
│     ├─ imaging/     # 压缩、存储、加载
│     └─ kiosk/       # 自启、常亮、沉浸、PIN 门禁
├─ docs/
└─ scripts/           # adb 一键安装等
```

## 附录 B：待确认事项（实施前需落实）

1. 平板 ABI 与内存实测值（Phase 0 产出），决定打包 ABI 与图片规格。
2. 平板是否能开热点；若不能，需确保摊位现场有稳定 WiFi。
3. 是否需要 `git init` 并建立首次提交。