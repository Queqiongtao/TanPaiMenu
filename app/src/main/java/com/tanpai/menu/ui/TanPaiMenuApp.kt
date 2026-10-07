package com.tanpai.menu.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanpai.menu.R
import com.tanpai.menu.backup.BackupManager
import com.tanpai.menu.data.MenuRepository
import com.tanpai.menu.data.PinManager
import com.tanpai.menu.data.SettingsStore
import com.tanpai.menu.host.HostStatusProvider
import com.tanpai.menu.imaging.ImageStore
import com.tanpai.menu.ui.admin.AdminScreen
import com.tanpai.menu.ui.admin.ConfirmDialog
import com.tanpai.menu.ui.admin.HoldToTriggerButton
import com.tanpai.menu.ui.admin.AdminViewModel
import com.tanpai.menu.ui.display.DisplayScreen
import com.tanpai.menu.ui.display.DisplayViewModel
import kotlinx.coroutines.delay

private const val SCREEN_DISPLAY = "display"

/** 「忘记密码」需长按的时长（毫秒）。 */
private const val FORGOT_HOLD_MS = 3_000L
private const val SCREEN_ADMIN = "admin"

private enum class PinDialog { None, Setup, Enter }

@Composable
fun TanPaiMenuApp(
    repository: MenuRepository,
    settings: SettingsStore,
    pinManager: PinManager,
    imageStore: ImageStore,
    backupManager: BackupManager,
    hostStatus: HostStatusProvider,
    onSwitchToClient: () -> Unit
) {
    var screen by rememberSaveable { mutableStateOf(SCREEN_DISPLAY) }
    var pinDialog by remember { mutableStateOf(PinDialog.None) }
    // 忘记密码重置后必须重新设置密码，此时「设置管理密码」不可取消（docs/design/0017 第 2.2 节）。
    var pinSetupForced by remember { mutableStateOf(false) }

    val displayViewModel: DisplayViewModel =
        viewModel(factory = DisplayViewModel.factory(repository))
    val adminViewModel: AdminViewModel =
        viewModel(
            factory = AdminViewModel.factory(
                repository, settings, imageStore, backupManager, hostStatus
            )
        )

    if (screen == SCREEN_ADMIN) {
        AdminScreen(
            viewModel = adminViewModel,
            pinManager = pinManager,
            onExit = { screen = SCREEN_DISPLAY },
            onSwitchToClient = onSwitchToClient
        )
    } else {
        DisplayScreen(
            viewModel = displayViewModel,
            storeName = settings.displayStoreName,
            showStockHint = settings.showStockHint,
            cardAlpha = settings.cardAlphaPercent / 100f,
            backgroundImagePath = settings.backgroundImagePath,
            backgroundStyleId = settings.backgroundStyleId,
            backgroundScrim = settings.backgroundScrimPercent / 100f,
            wechatQrImagePath = settings.wechatQrImagePath,
            onRequestAdmin = {
                pinDialog = if (pinManager.isPinSet()) PinDialog.Enter else PinDialog.Setup
                pinSetupForced = false
            }
        )
    }

    when (pinDialog) {
        PinDialog.Setup -> PinSetupDialog(
            cancellable = !pinSetupForced,
            onConfirm = { pin ->
                pinManager.setPin(pin)
                pinDialog = PinDialog.None
                pinSetupForced = false
                screen = SCREEN_ADMIN
            },
            onDismiss = {
                pinDialog = PinDialog.None
                pinSetupForced = false
            }
        )
        PinDialog.Enter -> PinEntryDialog(
            pinManager = pinManager,
            onSuccess = {
                pinDialog = PinDialog.None
                screen = SCREEN_ADMIN
            },
            onForgot = {
                pinManager.clearPin()
                pinSetupForced = true
                pinDialog = PinDialog.Setup
            },
            onDismiss = { pinDialog = PinDialog.None }
        )
        PinDialog.None -> Unit
    }
}

@Composable
private fun PinSetupDialog(
    cancellable: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    val valid = pin.length in 4..6 && pin.all(Char::isDigit)
    val matched = pin == confirmation

    AlertDialog(
        onDismissRequest = { if (cancellable) onDismiss() },
        title = { Text(stringResource(R.string.pin_setup_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                    label = { Text(stringResource(R.string.pin_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it.filter(Char::isDigit).take(6) },
                    label = { Text(stringResource(R.string.pin_confirm_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                if (showError) {
                    Text(
                        text = stringResource(
                            if (!valid) R.string.pin_error_format else R.string.pin_error_mismatch
                        ),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (valid && matched) onConfirm(pin) else showError = true
                }
            ) { Text(stringResource(R.string.admin_confirm)) }
        },
        dismissButton = {
            if (cancellable) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.admin_cancel)) }
            }
        }
    )
}

@Composable
private fun PinEntryDialog(
    pinManager: PinManager,
    onSuccess: () -> Unit,
    onForgot: () -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var lockedSeconds by remember { mutableIntStateOf(pinManager.lockRemainingSeconds()) }
    var confirmForgot by remember { mutableStateOf(false) }

    LaunchedEffect(lockedSeconds) {
        while (lockedSeconds > 0) {
            delay(1_000L)
            lockedSeconds = pinManager.lockRemainingSeconds()
        }
    }

    val wrongText = stringResource(R.string.pin_error_wrong)
    val lockedText = stringResource(R.string.pin_locked, lockedSeconds)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_entry_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                    label = { Text(stringResource(R.string.pin_label)) },
                    enabled = lockedSeconds == 0,
                    modifier = Modifier.fillMaxWidth()
                )
                val displayed = if (lockedSeconds > 0) lockedText else error
                displayed?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = lockedSeconds == 0,
                onClick = {
                    if (pinManager.verify(pin)) {
                        onSuccess()
                    } else {
                        error = wrongText
                        lockedSeconds = pinManager.lockRemainingSeconds()
                        pin = ""
                    }
                }
            ) { Text(stringResource(R.string.admin_confirm)) }
        },
        dismissButton = {
            Column {
                HoldToTriggerButton(
                    text = stringResource(R.string.pin_forgot),
                    holdMillis = FORGOT_HOLD_MS,
                    onTriggered = { confirmForgot = true }
                )
                Text(
                    text = stringResource(R.string.pin_forgot_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.admin_cancel)) }
            }
        }
    )

    if (confirmForgot) {
        ConfirmDialog(
            title = stringResource(R.string.pin_forgot_title),
            text = stringResource(R.string.pin_forgot_confirm),
            onConfirm = {
                confirmForgot = false
                onForgot()
            },
            onDismiss = { confirmForgot = false }
        )
    }
}
