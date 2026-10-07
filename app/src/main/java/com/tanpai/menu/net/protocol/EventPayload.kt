package com.tanpai.menu.net.protocol

import kotlinx.serialization.json.decodeFromJsonElement

/**
 * WS 事件 payload 解码（`docs/design/0019` 第 2.2 节）。
 *
 * 按 [ProtocolJson] 严格解析（未知字段报错）；失败返回 null，调用方据此断开重连并重新拉快照，
 * 避免把「解释不了的变更」静默丢掉。
 */
inline fun <reified T> WireEvent.payloadAs(): T? = runCatching {
    ProtocolJson.decodeFromJsonElement<T>(payload)
}.getOrNull()