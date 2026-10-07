package com.tanpai.menu.ui.client

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tanpai.menu.client.ClientSession
import com.tanpai.menu.client.PairingClient
import com.tanpai.menu.data.SettingsStore

/**
 * 手机客户端运行形态的根界面（`docs/design/0018` 第 2.2 节）。
 *
 * 未配对时显示配对页；配对成功后显示只读菜单页，并提供「设置」用于重新配对或切回平板主机。
 * 连接与镜像由 [session] 持有，本文件只负责界面路由，不直接收发网络报文。
 */
@Composable
fun ClientApp(
    session: ClientSession,
    pairingClient: PairingClient,
    settings: SettingsStore,
    onSwitchToHost: () -> Unit
) {
    val state by session.state.collectAsState()
    var paired by remember { mutableStateOf(session.isPaired) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(paired) {
        if (paired) session.start() else session.stop()
    }

    if (!paired) {
        ClientConnectScreen(
            pairingClient = pairingClient,
            initialAddress = settings.clientHostAddress.orEmpty(),
            initialDeviceName = settings.clientDeviceName,
            onPaired = { paired = true },
            onSwitchToHost = onSwitchToHost
        )
        return
    }

    ClientMenuScreen(
        state = state,
        onOpenSettings = { showSettings = true }
    )

    if (showSettings) {
        ClientSettingsDialog(
            state = state,
            onReconnect = { session.reconnect() },
            onRepair = {
                showSettings = false
                session.unpair()
                paired = false
            },
            onSwitchToHost = onSwitchToHost,
            onDismiss = { showSettings = false }
        )
    }
}