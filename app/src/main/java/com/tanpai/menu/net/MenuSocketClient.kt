package com.tanpai.menu.net

import com.tanpai.menu.net.protocol.ProtocolJson
import com.tanpai.menu.net.protocol.WireEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** WS 收到的一条消息。 */
sealed interface SocketMessage {
    /** 契约事件报文。 */
    data class Event(val event: WireEvent) : SocketMessage

    /** 无法按契约解析的报文：调用方应断开重连并重新拉快照。 */
    data object Unparsable : SocketMessage
}

/**
 * 一条 WS 订阅连接（`docs/design/0019` 第 2.4 节）。
 *
 * [messages] 收到关闭帧即结束；[awaitClose] 给出关闭码（`4401` 表示配对码失效）。
 */
class SocketSession internal constructor(
    private val socket: WebSocket,
    val messages: ReceiveChannel<SocketMessage>,
    private val closed: CompletableDeferred<Int?>
) {
    /** 关闭码；未收到关闭帧（如网络中断）为 null。 */
    suspend fun awaitClose(): Int? = closed.await()

    /** 主动关闭连接；重复调用无副作用。 */
    fun close() {
        runCatching { socket.close(SOCKET_CLOSE_NORMAL, null) }
    }

    private companion object {
        const val SOCKET_CLOSE_NORMAL = 1000
    }
}

/** WS 订阅客户端：把 OkHttp 的 WebSocket 回调收敛成「消息流 + 关闭码」。 */
class MenuSocketClient(private val client: OkHttpClient) {

    fun open(url: String): SocketSession {
        val closed = CompletableDeferred<Int?>()
        val messages = Channel<SocketMessage>(Channel.UNLIMITED)
        val socket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val event = runCatching {
                        ProtocolJson.decodeFromString(WireEvent.serializer(), text)
                    }.getOrNull()
                    messages.trySend(
                        if (event == null) SocketMessage.Unparsable else SocketMessage.Event(event)
                    )
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    closed.complete(code)
                    messages.close()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    closed.complete(code)
                    messages.close()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    closed.complete(response?.code)
                    messages.close()
                }
            }
        )
        return SocketSession(socket, messages, closed)
    }
}