# 设备能力基线（Phase 0 产出）

> 采集时间：2026-10-05 10:09:35
> 采集命令：`scripts\device-precheck.bat`
> 设备序列号：0123456789ABCDEF
> adb：Android Debug Bridge version 1.0.41 / Version 37.0.1-15733141

| 项目 | 取值 |
| --- | --- |
| 品牌 / 型号 | Teclast / T10(E3C6) |
| Android 版本 | 7.0（API 24） |
| 主 ABI | arm64-v8a |
| ABI 列表 | arm64-v8a,armeabi-v7a,armeabi |
| 内存总量 | 3955 MB |
| 屏幕 | Physical size: 1600x2560 |
| 密度 | Physical density: 320
Override density: 426 |
| /data 可用 | 52224 MB |
| Build 指纹 | Teclast/Teclast/t10_e3c6:7.0/NRD90M/1513246712:user/test-keys |

## 原始输出

`
cat /proc/meminfo        -> MemTotal:        4050284 kB
wm size                  -> Physical size: 1600x2560
wm density               -> Physical density: 320
Override density: 426
df -h /data              -> Filesystem      Size  Used Avail Use% Mounted on
/dev/block/dm-1  54G  2.0G   51G   4% /data
`

## 对打包与 UI 的影响

- ABI：主 ABI 为 arm64-v8a —— 若为 64 位则单打 arm64-v8a，否则只打 arm64-v8a。
- 内存：3955 MB —— 小于 2048 MB 时收紧 Coil 缓存与首屏卡片数。
- 分辨率与密度：Physical size: 1600x2560 / Physical density: 320
Override density: 426 —— 决定展示页字号与卡片尺寸。