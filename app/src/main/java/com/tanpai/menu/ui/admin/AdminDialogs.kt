package com.tanpai.menu.ui.admin

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.tanpai.menu.R
import com.tanpai.menu.data.PinManager
import com.tanpai.menu.data.db.Category
import com.tanpai.menu.data.db.Dish
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun CategoryEditDialog(
    initial: Category?,
    onConfirm: (name: String, sortOrder: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var sortOrder by remember { mutableStateOf((initial?.sortOrder ?: 0).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial == null) R.string.admin_add_category else R.string.admin_edit))
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.admin_category_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sortOrder,
                    onValueChange = { sortOrder = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.admin_sort_order)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onConfirm(name.trim(), sortOrder.toIntOrNull() ?: 0)
                    onDismiss()
                }
            ) { Text(stringResource(R.string.admin_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.admin_cancel)) }
        }
    )
}

@Composable
fun DishEditDialog(
    initial: Dish?,
    categoryId: Long,
    onImportImage: suspend (Uri) -> String?,
    onCreateCaptureUri: () -> Uri,
    onConfirm: (Dish) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var price by remember { mutableStateOf(initial?.let { it.priceCents / 100.0 }?.toString() ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "") }
    var stockEnabled by remember { mutableStateOf(initial?.stockEnabled ?: false) }
    var stockCount by remember { mutableStateOf((initial?.stockCount ?: 0).toString()) }
    var sortOrder by remember { mutableStateOf((initial?.sortOrder ?: 0).toString()) }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var imagePath by remember { mutableStateOf(initial?.imagePath) }
    var permissionDenied by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch { onImportImage(uri)?.let { imagePath = it } }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCaptureUri
        if (success && uri != null) {
            scope.launch { onImportImage(uri)?.let { imagePath = it } }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = onCreateCaptureUri()
            pendingCaptureUri = uri
            cameraLauncher.launch(uri)
        } else {
            permissionDenied = true
        }
    }

    val priceCents = price.toDoubleOrNull()?.let { (it * 100).roundToInt() } ?: 0
    val defaultUnit = stringResource(R.string.dish_default_unit)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial == null) R.string.admin_add_dish else R.string.admin_edit))
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.admin_dish_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.admin_price_yuan)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text(stringResource(R.string.admin_unit)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.admin_stock_enabled))
                    Switch(checked = stockEnabled, onCheckedChange = { stockEnabled = it })
                }
                OutlinedTextField(
                    value = stockCount,
                    onValueChange = { stockCount = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.admin_stock_count)) },
                    enabled = stockEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = sortOrder,
                    onValueChange = { sortOrder = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.admin_sort_order)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.admin_description)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { galleryLauncher.launch("image/*") }) {
                        Text(stringResource(R.string.admin_pick_image))
                    }
                    TextButton(
                        onClick = {
                            permissionDenied = false
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                val uri = onCreateCaptureUri()
                                pendingCaptureUri = uri
                                cameraLauncher.launch(uri)
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(stringResource(R.string.admin_take_photo))
                    }
                    if (imagePath != null) {
                        TextButton(onClick = { imagePath = null }) {
                            Text(stringResource(R.string.admin_clear_image))
                        }
                    }
                }
                if (permissionDenied) {
                    Text(
                        text = stringResource(R.string.admin_permission_denied),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                imagePath?.let { path ->
                    AsyncImage(
                        model = File(context.filesDir, path),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onConfirm(
                        Dish(
                            id = initial?.id ?: 0L,
                            categoryId = initial?.categoryId ?: categoryId,
                            name = name.trim(),
                            priceCents = priceCents,
                            unit = unit.trim().ifBlank { defaultUnit },
                            imagePath = imagePath,
                            stockEnabled = stockEnabled,
                            stockCount = stockCount.toIntOrNull() ?: 0,
                            soldOut = initial?.soldOut ?: false,
                            description = description.trim(),
                            sortOrder = sortOrder.toIntOrNull() ?: 0,
                            enabled = initial?.enabled ?: true,
                            updatedAt = initial?.updatedAt ?: 0L
                        )
                    )
                    onDismiss()
                }
            ) { Text(stringResource(R.string.admin_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.admin_cancel)) }
        }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                }
            ) { Text(stringResource(R.string.admin_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.admin_cancel)) }
        }
    )
}

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