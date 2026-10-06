# TanPaiMenu 架构与关键逻辑

> 本文件是工程架构与关键逻辑的权威技术文档。
> 计划、阶段与进度见 `docs/PLAN.md`；功能设计文档见 `docs/design/README.md`。

## 1. 系统概览

- 角色模型：同一个 APK，首次启动选择角色。平板＝主机（Room + HTTP/WS 服务 + 前台服务），是唯一数据源与唯一写者；手机＝客户端（不落库，内存镜像 + REST 命令 + WS 订阅）。详见 `docs/PLAN.md` 第 4.1 节。
- 运行形态：平板长期驻留展示页（自启、常亮、沉浸、Home 拦截），手机为随身编辑端。
- 部署：单主机；最多约 5 台客户端；局域网内明文 HTTP + Bearer token。

## 2. 分层与模块

分层约定（与 `AGENTS.md` 第 4 节一致）：UI（Compose）→ 领域/状态 → 数据与网络；只允许上层依赖下层。

```text
app/src/main/java/com/tanpai/menu/
├─ data/        # Room 实体、DAO、Repository、设置、AppMeta（全局 version）
├─ domain/      # 纯业务规则（卖出 / 撤销策略、价格格式化）
├─ host/        # 主机侧：NanoHTTPD REST/WS、单写者队列、配对与鉴权
├─ client/      # 客户端侧：发现、连接、镜像状态、重连
├─ net/protocol/ # 契约 DTO、ISO-8601、实体 ↔ DTO 映射（主机与客户端共用）
├─ net/         # REST 客户端、WS 客户端（Phase 3）
├─ ui/display/  # 顾客展示页（2 列 × 6 行横卡；自定义背景图 / 内置样式与白色蒙版；店名左侧微信二维码（店主上传或内置默认）与点击放大浮层；内置字体见 ui/theme）
├─ ui/admin/    # 管理与设置页（双端共用）
├─ ui/theme/    # 字体（Noto Sans SC Bold + 店名专用霞鹜文楷子集）、字型、展示页状态语义色、店名招牌配色与内置背景样式
├─ ui/client/   # 手机客户端管理页（复用 admin 组件）
├─ imaging/     # 压缩、存储、加载
├─ backup/      # 备份格式（format 1）与导出 / 导入
├─ logging/     # TpLog 落盘日志
└─ kiosk/       # 自启、常亮、沉浸、PIN 门禁
```

模块边界：`host` 与 `client` 不互相引用；跨模块只经 `net` / `data` 的接口与 DTO 交互。

Phase 1 已落地的包：`data`、`domain`、`imaging`、`backup`、`logging`、`kiosk`、`ui/display`、`ui/admin`；Phase 2 新增 `net/protocol`、`host`（进行中）；`net`（客户端）、`client`、`ui/client` 为 Phase 3 预留。

## 3. 关键流程与数据流

- 展示：`data` 读 Room → `ui/display` 渲染；沽清卡片灰化并在行尾显示「已售完」徽标。
- 展示布局：顶部条 72dp（店名 48sp 居中的渐变招牌——`StoreNameColors` 紫罗兰横向渐变底 + 1dp 半透明白描边 + 8dp 投影，`StoreNameFontFamily` 楷体子集，超 6 字降档 36sp，长按店名 2 秒进管理；多分类时靠右的分类胶囊 16sp；店名左侧为微信二维码入口：小码 56dp + 「加微信」13sp，点击弹出全屏放大浮层、5 秒自动复原或点击任意处立即关闭，店主未上传时用内置默认二维码）+ `GridCells.Fixed(2)` 横卡（左 56dp 方图 + 定宽三列：名称弹性 / 价格 100dp / 状态 68dp，均左对齐；菜名超宽跑马灯滚动）；卡片固定 68dp、列距 16dp、行距 12dp、底部留白 24dp，12 道一屏、超出滚动；卡片底色按设置的不透明度（`SettingsStore.cardAlphaPercent`，默认 75%）叠加；价格用 `domain/PriceFormatter.formatYuan` 渲染，整数省略小数位（`¥10`），有角分保留两位（`¥10.50`）。字体 `ui/theme/Type.kt`（菜名等 Noto Sans SC Bold，OFL 1.1；店名霞鹜文楷子集，OFL 1.1，见 `docs/third-party/`）。
- 卖出：`ui/admin` 命令（乐观更新）→ REST → `host` 单写者队列 → 事务（写 `SaleRecord` + 扣库存 + 全局 `version` +1）→ WS 广播 → 各端应用。
- 展示背景：`DisplayScreen` 在内容层下叠一层背景 + 一层白色蒙版（`SettingsStore.backgroundScrimPercent`，默认 25%）；背景按优先级二选一——自定义图（`backgroundImagePath`，`AsyncImage(Crop)` 整屏铺满）> 内置样式（`backgroundStyleId` 经 `BackgroundStyles.byIdOrDefault` 解析，未设置或未知 id 回退 `DEFAULT_ID` 喜庆红）。两者互斥，由管理页写入时保证；内置样式不参与孤儿图清理与备份。见 `docs/design/0007-展示页背景图片.md`、`docs/design/0009-展示页内置背景样式.md`、`docs/design/0011-展示页默认外观与整数价格.md`。
- 展示微信二维码：`SettingsStore.wechatQrImagePath`（key `wechat_qr_image`）指向 `images/<uuid>.png`；店主在管理页设置弹窗经 `ImageStore.importLosslessFromUri`（PNG 无损、长边 1024）导入，可相册或拍照；放大浮层在 `ui/display/WechatQrOverlay.kt`（`WECHAT_QR_AUTO_COLLAPSE_MILLIS = 5000`）；`DisplayScreen` 计算图片模型 `Any`——已上传且文件存在的 `File` 优先，否则回退内置资源 `R.drawable.wechat_qr_default`（`res/drawable-nodpi`，入口恒显示）；与背景图同为「非菜品图」但内置图是资源，故只需保留店主上传的那张，须加入启动孤儿图清理与「恢复默认菜单」保留集合；不参与备份（`backup.json` 仍 format 1）。见 `docs/design/0010-展示页微信二维码.md`。
- 同步：客户端 `/state` 全量对齐 + WS 事件增量；用 `version` 判新旧；断线重连重取快照。
- 配对：主机生成 token → 二维码 / NSD / 手输 → 客户端带 token 请求。

Phase 2 主机写管线（M6 起实现）：客户端 / 本机命令 → `host` 路由与鉴权 → `net/protocol` 解析 → `MenuRepository` 单写者锁 → 事务提交 → `MenuChange` 事件 → WS 广播。

实现位置：`data/MenuRepository.kt`（`writeMutex` 串行化 + `events` 事件流，提交后按序发出）、`data/MenuChange.kt`（变更类型）、`net/protocol/Contract.kt`（DTO / ISO-8601 / 实体映射）。
验证：JVM 单测 36 项通过（含 `ContractTest` 12 项）；真机 Teclast T10 / API 24 仪表测试 11 项通过。

Phase 1 已实现的单机流程（含实现位置）：

- 启动：`App.onCreate` → `TpLog.init` → `MenuRepository.seedIfEmpty` → `ImageStore.deleteOrphans(菜品图 ∪ 背景图 ∪ 微信二维码)`。背景图与微信二维码不属于任何菜品，必须显式加入引用集合，否则每次冷启动会被当孤儿图删除。
- 卖出 / 撤销：`ui/admin/AdminViewModel` → `MenuRepository.sell/unsell`（事务内 `SellPolicy` / `UnsellPolicy`，并令 `version` +1）。
- 图片：`ui/admin/AdminDialogs`（菜品图）与设置弹窗（背景图 / 微信二维码）相册 / 拍照 → `ImageStore.importFromUri` → `filesDir/images/<uuid>.jpg`；菜品图长边 1280 / 质量 80，背景图长边 2048 / 质量 85；微信二维码走 `importLosslessFromUri`（PNG 无损、长边 1024）→ `images/<uuid>.png`。
- 备份：`backup/BackupManager` 按 format 1 导出 `backup.json` + `images/`；导入先校验后整库覆盖（`MenuRepository.replaceAll`）。
- 门禁：`ui/TanPaiMenuApp`（长按店名 2 秒 + PIN）、`data/PinManager`（失败锁定）、`kiosk/Kiosk`（沉浸 / 常亮 / 退出到桌面）。

## 4. 接口与数据模型

- 契约细节：`docs/design/0001-菜单数据模型与接口契约.md`。
- 配对与同步：`docs/design/0002-局域网配对与同步.md`。
- 本节只维护契约与本工程实现位置（包名/类名）的映射，契约字段不在此重复。

## 5. 错误处理与日志

- 统一错误码与响应格式：见 `docs/design/0001` 第 3.1 节。
- 日志：`TpLog` 写 `filesDir/logs/app-<date>.log`，保留 7 天；收集脚本 `scripts/collect-logs.*`（Phase 1 交付）。

实现：`logging/TpLog.kt`（异步落盘；崩溃同步落盘并挂默认异常处理器）与 `scripts/collect-logs.ps1|.bat`（产出 device.txt、logcat.txt、app-logs/）。已实测：按天切分、UTF-8 中文正常、保留 7 天。

## 6. 数据迁移

- Room `exportSchema = true`，schema JSON 入库 `app/schemas/`。
- 每次 schema 变更必须提供 Migration 与迁移测试；升级安装保留数据。

## 7. 构建与发布

- 构建运维：Gradle wrapper + `scripts/*.ps1`（`.bat` 为入口封装）；不手工修改生成物。
- v1：debug 签名 APK、单 ABI（arm64-v8a）、关闭 minify；分发与排障见 `docs/design/0005-发布分发与现场排障.md`。
- 内置字体 Noto Sans SC Bold 约 8.5MB，APK 体积约 20MB；如需瘦身可改子集化，但会限制自定义店名/菜名的字形。
- 性能门禁见 `docs/PLAN.md` 第 8 节。

## 8. 变更记录

| 日期 | 变更 | 关联设计文档 |
| --- | --- | --- |
| 2026-10-05 | 建立架构文档骨架 | — |
| 2026-10-05 | 填充分层与模块、关键流程、错误与日志、数据迁移、构建与发布 | 0001、0002、0005 |
| 2026-10-05 | 回填 Phase 1 实现位置：图片、备份、日志、门禁与单机流程 | 0001、0003、0004、0005 |
| 2026-10-05 | Phase 2 启动：新增 `net/protocol` 契约层与主机写管线说明 | 0002 |
| 2026-10-05 | M6 落地：契约层与单写者事件流实现，附验证结果 | 0002 |
| 2026-10-05 | 展示页紧凑化与默认菜单改写；新增内置字体与 `ui/theme` | 0006 |
| 2026-10-05 | 展示页修订验证收尾：设置弹窗内容可滚动，末尾项在 553dp 横屏可达 | 0006 |
| 2026-10-05 | 展示页沽清标识改为行尾徽标，不再覆盖菜名 | 0006 |
| 2026-10-05 | 展示页卡片三列定宽对齐，菜名超宽改跑马灯 | 0006 |
| 2026-10-05 | 展示页状态列三态统一为色块徽标（有货 / 余 N 加框），状态列宽 60dp → 68dp | 0006 |
| 2026-10-05 | 新增 `ui/theme/StatusColors.kt`，有货徽标改用浅绿语义色 | 0006 |
| 2026-10-05 | 展示页移除 150dp 侧栏，改为 44dp 顶部条（店名 + 多分类胶囊）；长按进管理 5 秒 → 2 秒 | 0006、0003 |
| 2026-10-05 | 去掉展示页顶部条的长按提示（删除 `display_long_press_hint`） | 0006 |
| 2026-10-05 | 展示页顶部条店名改为水平居中（店名 / 胶囊限宽 0.4 / 0.3 顶栏宽） | 0006 |
| 2026-10-05 | 展示页店名放大到 28sp 并加淡紫圆角胶囊底 | 0006 |
| 2026-10-05 | 展示页卡片固定 68dp（行距 16dp、底部留白 24dp），卡片底透明度可在设置中调整（`card_alpha`） | 0006 |
| 2026-10-06 | 展示页支持自定义背景图（整屏铺满裁切 + 可调白色蒙版），启动孤儿图清理与恢复默认菜单保留背景图 | 0007 |
| 2026-10-06 | 展示页店名 48sp 渐变招牌（描边 + 投影）并换用霞鹜文楷子集字体；顶栏 72dp、行距 12dp，12 道仍一屏 | 0008 |
| 2026-10-06 | 展示页新增 6 款代码生成的中式暖色内置背景样式，与自定义图互斥单选，蒙版同样生效 | 0009 |
| 2026-10-06 | 展示页店名左侧新增微信二维码入口（无损 PNG 导入、点击放大、5 秒自动复原），孤儿图清理与恢复默认菜单保留二维码 | 0010 |
| 2026-10-06 | 默认背景改为喜庆红（`BackgroundStyles.DEFAULT_ID`）、蒙版默认 30% → 25%、价格整数省略小数位、新增内置默认微信二维码并修正设置弹窗滑条顺序 | 0011 |
