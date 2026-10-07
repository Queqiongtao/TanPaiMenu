package com.tanpai.menu.ui.client

import android.Manifest
import android.content.pm.PackageManager
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.tanpai.menu.R
import com.tanpai.menu.qrcode.QrScanController

/**
 * 扫码取景组件（`docs/design/0022` 第 2.3 节）。
 *
 * 负责相机权限申请、[QrScanController] 生命周期与预览层；识别到的内容交给 [onScanned]：
 * 返回中文错误信息表示配对失败（清除错误后继续扫描），返回 null 表示配对成功（停止解码）。
 * 相机不可用时经 [onCameraUnavailable] 通知外层展开手动配对表单，其余功能不受影响。
 *
 * 取景窗口形状固定（16:9），纹理在窗口内做等比裁切（center-crop）：
 * 无论相机输出是横是竖、无论屏幕方向，画面都不会被拉伸，只可能裁掉边缘。
 */
@Composable
fun CameraScanner(
    onScanned: (String) -> String?,
    onCameraUnavailable: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }
    var granted by remember { mutableStateOf(hasPermission) }
    var error by remember { mutableStateOf<String?>(null) }

    val permissionDeniedText = stringResource(R.string.client_scan_permission_denied)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { allowed ->
        granted = allowed
        if (!allowed) onCameraUnavailable(permissionDeniedText)
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!granted) {
        Text(
            text = stringResource(R.string.client_scan_permission_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }

    // 控制器存活于整个组合期，用 latest 引用保证回调读到最新的 lambda。
    val latestScanned by rememberUpdatedState(onScanned)
    val latestUnavailable by rememberUpdatedState(onCameraUnavailable)
    val controller = remember {
        QrScanController(
            context = context.applicationContext,
            onDecoded = { content ->
                error = null
                val failure = latestScanned(content)
                if (failure != null) error = failure
                failure == null
            },
            onError = { message -> latestUnavailable(message) }
        )
    }

    DisposableEffect(Unit) {
        controller.start()
        onDispose { controller.stop() }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.client_scan_hint),
            style = MaterialTheme.typography.bodyMedium
        )
        // 固定 16:9 的取景窗口：宽度占满，高度上限 [PREVIEW_MAX_HEIGHT]，再高也不会挤掉下方说明。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = PREVIEW_MAX_HEIGHT)
                .aspectRatio(PREVIEW_ASPECT)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
        ) {
            QrPreview(controller = controller, modifier = Modifier.fillMaxSize())
        }
        error?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** `TextureView` 预览层；视图附加到窗口后才交给控制器，避免拿到未就绪的 Surface。 */
@Composable
private fun QrPreview(controller: QrScanController, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).also { view ->
                view.addOnAttachStateChangeListener(
                    object : android.view.View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(attached: android.view.View) {
                            (attached as? TextureView)?.let(controller::attach)
                        }

                        override fun onViewDetachedFromWindow(detached: android.view.View) = Unit
                    }
                )
            }
        }
    )
}

/** 取景窗口形状与高度上限：固定 16:9，避免竖屏被拉成长条、横屏占满整屏。 */
private const val PREVIEW_ASPECT = 16f / 9f
private val PREVIEW_MAX_HEIGHT = 260.dp
