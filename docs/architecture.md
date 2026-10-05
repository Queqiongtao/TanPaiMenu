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
├─ host/        # 主机侧：NanoHTTPD REST/WS、单写者队列、配对与鉴权
├─ client/      # 客户端侧：发现、连接、镜像状态、重连
├─ net/         # REST 客户端、WS 客户端、DTO 与序列化
├─ ui/display/  # 顾客展示页
├─ ui/admin/    # 管理与设置页（双端共用）
├─ ui/client/   # 手机客户端管理页（复用 admin 组件）
├─ imaging/     # 压缩、存储、加载
└─ kiosk/       # 自启、常亮、沉浸、PIN 门禁
```

模块边界：`host` 与 `client` 不互相引用；跨模块只经 `net` / `data` 的接口与 DTO 交互。

## 3. 关键流程与数据流

- 展示：`data` 读 Room → `ui/display` 渲染；沽清卡片灰化并盖已售完章。
- 卖出：`ui/admin` 命令（乐观更新）→ REST → `host` 单写者队列 → 事务（写 `SaleRecord` + 扣库存 + 全局 `version` +1）→ WS 广播 → 各端应用。
- 同步：客户端 `/state` 全量对齐 + WS 事件增量；用 `version` 判新旧；断线重连重取快照。
- 配对：主机生成 token → 二维码 / NSD / 手输 → 客户端带 token 请求。

## 4. 接口与数据模型

- 契约细节：`docs/design/0001-菜单数据模型与接口契约.md`。
- 配对与同步：`docs/design/0002-局域网配对与同步.md`。
- 本节只维护契约与本工程实现位置（包名/类名）的映射，契约字段不在此重复。

## 5. 错误处理与日志

- 统一错误码与响应格式：见 `docs/design/0001` 第 3.1 节。
- 日志：`TpLog` 写 `filesDir/logs/app-<date>.log`，保留 7 天；收集脚本 `scripts/collect-logs.*`（Phase 1 交付）。

## 6. 数据迁移

- Room `exportSchema = true`，schema JSON 入库 `app/schemas/`。
- 每次 schema 变更必须提供 Migration 与迁移测试；升级安装保留数据。

## 7. 构建与发布

- 构建运维：Gradle wrapper + `scripts/*.ps1`（`.bat` 为入口封装）；不手工修改生成物。
- v1：debug 签名 APK、单 ABI（arm64-v8a）、关闭 minify；分发与排障见 `docs/design/0005-发布分发与现场排障.md`。
- 性能门禁见 `docs/PLAN.md` 第 8 节。

## 8. 变更记录

| 日期 | 变更 | 关联设计文档 |
| --- | --- | --- |
| 2026-10-05 | 建立架构文档骨架 | — |
| 2026-10-05 | 填充分层与模块、关键流程、错误与日志、数据迁移、构建与发布 | 0001、0002、0005 |
