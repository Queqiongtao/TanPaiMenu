package com.tanpai.menu.ui.admin

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tanpai.menu.R
import com.tanpai.menu.data.PinManager
import com.tanpai.menu.ui.dialogs.ConfirmDialog
import kotlin.math.roundToInt

/**
 * 管理页专用的 PIN 相关弹窗。
 *
 * 分类 / 菜品 / 确认等通用弹窗已移到 `ui/dialogs/MenuDialogs.kt`，由管理页与手机客户端共用
 * （`docs/design/0020` 第 2.1 节）。
 */

/**
 * 需要长按 [holdMillis] 毫秒才触发的按钮（`docs/design/0017` 第 2.2 节）；
 * 用于「忘记密码」这类高风险操作，避免误触。
 */
@Composable
fun HoldToTriggerButton(
    text: String,
    holdMillis: Long,
    onTriggered: () -> Unit,
    modifier: Modifier = Modifier
) {
    var progress by remember { mutableFloatStateOf(0f) }
    TextButton(
        onClick = { },
        modifier = modifier.pointerInput(holdMillis) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val start = System.currentTimeMillis()
                var triggered = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (!change.pressed) break
                    val held = System.currentTimeMillis() - start
                    progress = (held.toFloat() / holdMillis).coerceIn(0f, 1f)
                    if (held >= holdMillis) triggered = true
                }
                progress = 0f
                if (triggered) onTriggered()
            }
        }
    ) {
        Text(if (progress > 0f) "$text ${(progress * 100).roundToInt()}%" else text)
    }
}

/**
 * 修改管理密码：校验旧密码后设置新密码（4-6 位数字，两次一致）。
 *
 * 旧密码错误沿用 `PinManager` 的失败计数与锁定策略。
 */
@Composable
fun PinChangeDialog(
    pinManager: PinManager,
    onDone: () -> Unit,
    onDismiss: () -> Unit
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }

    val wrongText = stringResource(R.string.pin_error_wrong)
    val lockedText = stringResource(R.string.pin_locked, pinManager.lockRemainingSeconds())
    val valid = newPin.length in 4..6 && newPin.all(Char::isDigit)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_change_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = oldPin,
                    onValueChange = { oldPin = it.filter(Char::isDigit).take(6) },
                    label = { Text(stringResource(R.string.pin_old_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter(Char::isDigit).take(6) },
                    label = { Text(stringResource(R.string.pin_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it.filter(Char::isDigit).take(6) },
                    label = { Text(stringResource(R.string.pin_confirm_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                error?.let {
                    Text(
                        text = when (it) {
                            R.string.pin_locked -> lockedText
                            R.string.pin_error_wrong -> wrongText
                            else -> stringResource(it)
                        },
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        !pinManager.verify(oldPin) ->
                            error = if (pinManager.isLocked()) {
                                R.string.pin_locked
                            } else {
                                R.string.pin_error_wrong
                            }

                        !valid -> error = R.string.pin_error_format
                        newPin != confirmation -> error = R.string.pin_error_mismatch
                        else -> {
                            pinManager.setPin(newPin)
                            onDone()
                        }
                    }
                }
            ) { Text(stringResource(R.string.admin_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.admin_cancel)) }
        }
    )
}

/** 关闭密码保护：校验旧密码后清除管理密码；由调用方负责二次确认与提示。 */
@Composable
fun PinDisableDialog(
    pinManager: PinManager,
    onDone: () -> Unit,
    onDismiss: () -> Unit
) {
    var oldPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var confirming by remember { mutableStateOf(false) }

    val wrongText = stringResource(R.string.pin_error_wrong)
    val lockedText = stringResource(R.string.pin_locked, pinManager.lockRemainingSeconds())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_pin_disable)) },
        text = {
            Column {
                OutlinedTextField(
                    value = oldPin,
                    onValueChange = { oldPin = it.filter(Char::isDigit).take(6) },
                    label = { Text(stringResource(R.string.pin_old_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(
                        text = if (it == R.string.pin_locked) lockedText else wrongText,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (pinManager.verify(oldPin)) confirming = true
                    else {
                        error = if (pinManager.isLocked()) {
                            R.string.pin_locked
                        } else {
                            R.string.pin_error_wrong
                        }
                    }
                }
            ) { Text(stringResource(R.string.admin_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.admin_cancel)) }
        }
    )

    if (confirming) {
        ConfirmDialog(
            title = stringResource(R.string.settings_pin_disable),
            text = stringResource(R.string.settings_pin_disable_confirm),
            onConfirm = {
                pinManager.clearPin()
                onDone()
            },
            onDismiss = { confirming = false }
        )
    }
}
