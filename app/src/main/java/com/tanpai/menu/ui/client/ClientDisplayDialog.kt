package com.tanpai.menu.ui.client

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tanpai.menu.R
import com.tanpai.menu.data.DisplaySpec
import com.tanpai.menu.imaging.ImageCompressor
import com.tanpai.menu.net.protocol.DisplayPatchRequest
import com.tanpai.menu.net.protocol.DisplaySettingsDto
import com.tanpai.menu.ui.theme.BackgroundStyles
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 手机端上传背景图前先按长边 2048 压缩，主机侧再按背景图规格落盘（`docs/design/0023` 第 2.3 节）。 */
private const val BACKGROUND_UPLOAD_MAX_EDGE = 2048

/**
 * 手机端「展示设置」弹窗（`docs/design/0023` 第 2.2、2.3 节）。
 *
 * 可改：内置背景样式、自定义背景图（相册上传）、蒙版透明度、卡片透明度、有货提示。
 * 确认时只提交有变化的字段；上传图片先走 [onUploadBackground]（压缩 + 上传），成功后再一并提交。
 * 离线或平板关闭手机编辑时控件禁用，由外层保证 [enabled]。
 */
@Composable
fun ClientDisplayDialog(
    display: DisplaySettingsDto,
    enabled: Boolean,
    onUploadBackground: suspend (Uri) -> String?,
    onSubmit: suspend (DisplayPatchRequest) -> Result<Unit>,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit
) {
    var styleId by remember { mutableStateOf(display.backgroundStyleId) }
    var imagePath by remember { mutableStateOf(display.backgroundImagePath) }
    var scrim by remember { mutableFloatStateOf(display.backgroundScrimPercent.toFloat()) }
    var cardAlpha by remember { mutableFloatStateOf(display.cardAlphaPercent.toFloat()) }
    var showStock by remember { mutableStateOf(display.showStockHint) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uploadFailedText = stringResource(R.string.client_image_upload_failed)
    val savedText = stringResource(R.string.client_display_saved)

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val path = onUploadBackground(uri)
            busy = false
            if (path == null) {
                onMessage(uploadFailedText)
            } else {
                imagePath = path
                styleId = null
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.client_display_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.client_display_background),
                    style = MaterialTheme.typography.titleSmall
                )
                BackgroundStyleGrid(
                    selectedId = if (imagePath == null) styleId else null,
                    enabled = enabled && !busy,
                    onSelect = { id ->
                        styleId = id
                        imagePath = null
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (imagePath == null) {
                            stringResource(R.string.client_display_image_none)
                        } else {
                            stringResource(R.string.client_display_image_set)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        enabled = enabled && !busy
                    ) {
                        Text(stringResource(R.string.client_display_pick_image))
                    }
                    if (imagePath != null) {
                        TextButton(
                            onClick = { imagePath = null },
                            enabled = enabled && !busy
                        ) {
                            Text(stringResource(R.string.client_display_clear_image))
                        }
                    }
                }

                SliderRow(
                    label = stringResource(R.string.client_display_scrim, scrim.roundToInt()),
                    value = scrim,
                    valueRange = DisplaySpec.MIN_BACKGROUND_SCRIM_PERCENT.toFloat()..
                        DisplaySpec.MAX_BACKGROUND_SCRIM_PERCENT.toFloat(),
                    enabled = enabled && !busy,
                    onChange = { scrim = it }
                )
                SliderRow(
                    label = stringResource(R.string.client_display_card_alpha, cardAlpha.roundToInt()),
                    value = cardAlpha,
                    valueRange = DisplaySpec.MIN_CARD_ALPHA_PERCENT.toFloat()..100f,
                    enabled = enabled && !busy,
                    onChange = { cardAlpha = it }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.client_display_show_stock),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = showStock,
                        onCheckedChange = { showStock = it },
                        enabled = enabled && !busy
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = enabled && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val result = onSubmit(
                            DisplayPatchRequest(
                                backgroundStyleId = if (styleId != display.backgroundStyleId) styleId else null,
                                backgroundImagePath = if (imagePath != display.backgroundImagePath) {
                                    imagePath.orEmpty()
                                } else {
                                    null
                                },
                                backgroundScrimPercent = scrim.roundToInt()
                                    .takeIf { it != display.backgroundScrimPercent },
                                cardAlphaPercent = cardAlpha.roundToInt()
                                    .takeIf { it != display.cardAlphaPercent },
                                showStockHint = showStock.takeIf { it != display.showStockHint }
                            )
                        )
                        busy = false
                        result.fold(
                            onSuccess = {
                                onMessage(savedText)
                                onDismiss()
                            },
                            onFailure = { error ->
                                onMessage(
                                    context.getString(
                                        R.string.client_submit_failed,
                                        error.message.orEmpty()
                                    )
                                )
                            }
                        )
                    }
                }
            ) { Text(stringResource(R.string.client_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.admin_cancel))
            }
        }
    )
}

/** 内置背景样式色卡：两列网格，选中项加描边；[selectedId] 为 null 表示当前用自定义图。 */
@Composable
private fun BackgroundStyleGrid(
    selectedId: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    val styles = BackgroundStyles.All
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        styles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { style ->
                    val selected = style.id == selectedId
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = enabled) { onSelect(style.id) }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.linearGradient(
                                        if (style.gradient.size >= 2) {
                                            style.gradient
                                        } else {
                                            listOf(Color.Gray, Color.DarkGray)
                                        }
                                    )
                                )
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                )
                        )
                        Text(
                            text = stringResource(style.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                if (row.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onChange: (Float) -> Unit
) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange,
            enabled = enabled
        )
    }
}
