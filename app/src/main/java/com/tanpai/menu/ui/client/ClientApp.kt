package com.tanpai.menu.ui.client

import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tanpai.menu.R
import com.tanpai.menu.client.ClientSession
import com.tanpai.menu.client.PairingClient
import com.tanpai.menu.data.SettingsStore
import com.tanpai.menu.imaging.ImageCompressor
import kotlinx.coroutines.launch

/** 手机端上传背景图前的长边上限，与主机侧背景图规格一致（`docs/design/0023` 第 2.3 节）。 */
private const val BACKGROUND_UPLOAD_MAX_EDGE = 2048

/**
 * 手机客户端运行形态的根界面（`docs/design/0018` 第 2.2 节、`docs/design/0020` 第 2.1 节）。
 *
 * 未配对时显示配对页；配对成功后显示菜单页（浏览 + 管理模式），并提供「设置」用于重新配对
 * 或切回平板主机。连接与镜像由 [session] 持有，本文件只负责界面路由与图片上传编排，
 * 不直接收发网络报文。
 */
@Composable
fun ClientApp(
    session: ClientSession,
    pairingClient: PairingClient,
    settings: SettingsStore,
    onCreateCaptureUri: () -> Uri,
    onSwitchToHost: () -> Unit,
    /** 深链接带来的配对内容（`tanpai://pair?content=...`），非空时自动配对一次。 */
    pairLink: String? = null
) {
    val state by session.state.collectAsState()
    var paired by remember { mutableStateOf(session.isPaired) }
    var showSettings by remember { mutableStateOf(false) }
    var showDisplay by remember { mutableStateOf(false) }
    var adminMode by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uploadFailedText = stringResource(R.string.client_image_upload_failed)
    val backgroundUploadFailedText = stringResource(R.string.client_display_upload_failed)

    LaunchedEffect(paired) {
        if (paired) session.start() else session.stop()
    }

    // 断线或平板关闭手机编辑时退出管理模式，避免停留在不可提交的界面。
    LaunchedEffect(state.canEdit) {
        if (!state.canEdit) adminMode = false
    }

    if (!paired) {
        ClientConnectScreen(
            pairingClient = pairingClient,
            initialAddress = settings.clientHostAddress.orEmpty(),
            initialDeviceName = settings.clientDeviceName,
            onPaired = { paired = true },
            onSwitchToHost = onSwitchToHost,
            pairLink = pairLink
        )
        return
    }

    /** 相册 / 拍照结果 → 本机压缩 → 上传到主机，返回主机上的图片路径；任一步失败提示并返回 null。 */
    val onImportImage: suspend (Uri) -> String? = { uri ->
        val bytes = ImageCompressor.compressToJpegBytes(context, uri)
        val path = bytes?.let { session.uploadImage(it).getOrNull() }
        if (path == null) scope.launch { snackbarHostState.showSnackbar(uploadFailedText) }
        path
    }

    /** 相册选背景图 → 本机压到长边 2048 → 走 `?kind=background` 上传，返回主机路径。 */
    val onUploadBackground: suspend (Uri) -> String? = { uri ->
        val bytes = ImageCompressor.compressToJpegBytes(
            context,
            uri,
            maxEdge = BACKGROUND_UPLOAD_MAX_EDGE
        )
        val path = bytes?.let { session.uploadBackgroundImage(it).getOrNull() }
        if (path == null) scope.launch { snackbarHostState.showSnackbar(backgroundUploadFailedText) }
        path
    }

    ClientMenuScreen(
        state = state,
        adminMode = adminMode,
        snackbarHostState = snackbarHostState,
        onToggleAdminMode = { adminMode = !adminMode },
        onOpenSettings = { showSettings = true },
        onCreateCaptureUri = onCreateCaptureUri,
        onImportImage = onImportImage,
        onSubmit = { command -> session.submit(command) }
    )

    if (showSettings) {
        ClientSettingsDialog(
            state = state,
            onOpenDisplay = {
                showSettings = false
                showDisplay = true
            },
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

    if (showDisplay) {
        ClientDisplayDialog(
            display = state.display,
            enabled = state.canEdit,
            onUploadBackground = onUploadBackground,
            onSubmit = { patch -> session.updateDisplay(patch) },
            onDismiss = { showDisplay = false },
            onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
        )
    }
}