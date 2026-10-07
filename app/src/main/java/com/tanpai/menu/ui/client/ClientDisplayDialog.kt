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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 手机端上传背景图前先按长边 2048 压缩，主机侧再按背景图规格落盘（`docs/design/0023` 第 2.3 节）。 */
private const val BACKGROUND_UPLOAD_MAX_EDGE = 2048

/**
 * 手机端「展示设置」弹窗（`docs/design/0023` 第 2.2、2.3、2.6 节）。
 *
 * 可改：内置背景样式、自定义背景图（相册上传）、蒙版透明度、卡片透明度、有货提示。
 * 「恢复默认菜单」放在**客户端设置**弹窗（`ClientSettingsDialog`），见同文档第 2.5 节。
 *
 * **改动即时生效**：没有「确定」按钮——点样式、切开关立刻提交；滑条在拖动时只更新本地显示，
 * 松手后再提交一次（避免拖动过程刷出几十个请求）。提交按 [changeSeq] 串行化，
 * 前面排队的补丁若已被后续改动取代就跳过，保证最终落库的是最后一次操作。
 * 上传图片先走 [onUploadBackground]（压缩 + 上传），成功后再提交新路径。
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
    /** 弹窗内的编辑副本：用户操作即时改它，远端回包到达后再对齐。 */
    var draft by remember { mutableStateOf(display) }
    var busy by remember { mutableStateOf(false) }
    /** 每次本地改动自增，作为提交串行化的序号。 */
    var changeSeq by remember { mutableStateOf(0L) }
    var pendingPatch by remember { mutableStateOf<DisplayPatchRequest?>(null) }
    val submitMutex = remember { Mutex() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uploadFailedText = stringResource(R.string.client_image_upload_failed)
    val savedText = stringResource(R.string.client_display_saved)
    val failedText = stringResource(R.string.client_display_failed)

    /** 远端值变化时对齐本地副本：只覆盖用户没有正在改的字段。 */
    LaunchedEffect(display) {
        val patch = pendingPatch
        draft = draft.copy(
            backgroundStyleId = if (patch?.backgroundStyleId != null) {
                draft.backgroundStyleId
            } else {
                display.backgroundStyleId
            },
            backgroundImagePath = if (patch?.backgroundImagePath != null) {
                draft.backgroundImagePath
            } else {
                display.backgroundImagePath
            },
            backgroundScrimPercent = if (patch?.backgroundScrimPercent != null) {
                draft.backgroundScrimPercent
            } else {
                display.backgroundScrimPercent
            },
            cardAlphaPercent = if (patch?.cardAlphaPercent != null) {
                draft.cardAlphaPercent
            } else {
                display.cardAlphaPercent
            },
            showStockHint = if (patch?.showStockHint != null) {
                draft.showStockHint
            } else {
                display.showStockHint
            }
        )
    }

    /** 提交一次改动：串行执行，若期间又有新改动（序号前进）就让位给后者。 */
    LaunchedEffect(changeSeq) {
        val seq = changeSeq
        val patch = pendingPatch ?: return@LaunchedEffect
        submitMutex.withLock {
            // 已有更新的改动在排队：本次直接跳过，最终落库的是最后一次操作。
            if (seq != changeSeq) return@withLock
            val result = onSubmit(patch)
            if (seq != changeSeq) return@withLock
            pendingPatch = null
            result.fold(
                onSuccess = { onMessage(savedText) },
                onFailure = { error ->
                    // 失败时回到主机当前值，避免界面显示一个并未生效的设置。
                    draft = display
                    onMessage(
                        if (error.message.isNullOrBlank()) {
                            failedText
                        } else {
                            context.getString(R.string.client_submit_failed, error.message.orEmpty())
                        }
                    )
                }
            )
        }
    }

    /** 记录一次本地改动并触发提交。 */
    fun change(draftNext: DisplaySettingsDto, patch: DisplayPatchRequest) {
        draft = draftNext
        pendingPatch = patch
        changeSeq += 1
    }

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
                change(
                    draft.copy(backgroundImagePath = path, backgroundStyleId = null),
                    DisplayPatchRequest(backgroundImagePath = path)
                )
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
                    selectedId = if (draft.backgroundImagePath == null) draft.backgroundStyleId else null,
                    enabled = enabled && !busy,
                    onSelect = { id ->
                        change(
                            draft.copy(backgroundStyleId = id, backgroundImagePath = null),
                            DisplayPatchRequest(backgroundStyleId = id)
                        )
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (draft.backgroundImagePath == null) {
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
                    if (draft.backgroundImagePath != null) {
                        // 清除自定义图 = 回到默认内置样式（喜庆红），与平板侧语义一致。
                        TextButton(
                            onClick = {
                                change(
                                    draft.copy(
                                        backgroundImagePath = null,
                                        backgroundStyleId = BackgroundStyles.DEFAULT_ID
                                    ),
                                    DisplayPatchRequest(
                                        backgroundStyleId = BackgroundStyles.DEFAULT_ID
                                    )
                                )
                            },
                            enabled = enabled && !busy
                        ) {
                            Text(stringResource(R.string.client_display_clear_image))
                        }
                    }
                }

                // 滑条拖动期间只改本地值，松手后才提交（onValueChangeFinished）。
                var scrimDraft by remember { mutableFloatStateOf(draft.backgroundScrimPercent.toFloat()) }
                SliderRow(
                    label = stringResource(R.string.client_display_scrim, scrimDraft.roundToInt()),
                    value = scrimDraft,
                    valueRange = DisplaySpec.MIN_BACKGROUND_SCRIM_PERCENT.toFloat()..
                        DisplaySpec.MAX_BACKGROUND_SCRIM_PERCENT.toFloat(),
                    enabled = enabled && !busy,
                    onChange = { scrimDraft = it },
                    onFinished = {
                        val value = scrimDraft.roundToInt()
                        if (value != draft.backgroundScrimPercent) {
                            change(
                                draft.copy(backgroundScrimPercent = value),
                                DisplayPatchRequest(backgroundScrimPercent = value)
                            )
                        }
                    }
                )

                var cardAlphaDraft by remember {
                    mutableFloatStateOf(draft.cardAlphaPercent.toFloat())
                }
                SliderRow(
                    label = stringResource(
                        R.string.client_display_card_alpha,
                        cardAlphaDraft.roundToInt()
                    ),
                    value = cardAlphaDraft,
                    valueRange = DisplaySpec.MIN_CARD_ALPHA_PERCENT.toFloat()..100f,
                    enabled = enabled && !busy,
                    onChange = { cardAlphaDraft = it },
                    onFinished = {
                        val value = cardAlphaDraft.roundToInt()
                        if (value != draft.cardAlphaPercent) {
                            change(
                                draft.copy(cardAlphaPercent = value),
                                DisplayPatchRequest(cardAlphaPercent = value)
                            )
                        }
                    }
                )

                // 远端改过透明度时，把滑条本地值对齐（用户没在拖的时候）。
                LaunchedEffect(display.backgroundScrimPercent) {
                    if (pendingPatch?.backgroundScrimPercent == null) {
                        scrimDraft = display.backgroundScrimPercent.toFloat()
                    }
                }
                LaunchedEffect(display.cardAlphaPercent) {
                    if (pendingPatch?.cardAlphaPercent == null) {
                        cardAlphaDraft = display.cardAlphaPercent.toFloat()
                    }
                }

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
                        checked = draft.showStockHint,
                        onCheckedChange = { checked ->
                            change(
                                draft.copy(showStockHint = checked),
                                DisplayPatchRequest(showStockHint = checked)
                            )
                        },
                        enabled = enabled && !busy
                    )
                }
                Text(
                    text = stringResource(R.string.client_display_live_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        // 即时生效后不再需要「确定」，只留关闭。
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.client_close))
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
    onChange: (Float) -> Unit,
    onFinished: () -> Unit
) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = valueRange,
            enabled = enabled
        )
    }
}
