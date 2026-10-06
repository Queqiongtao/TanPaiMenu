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
import com.tanpai.menu.imaging.ImageStore
import com.tanpai.menu.ui.admin.AdminScreen
import com.tanpai.menu.ui.admin.AdminViewModel
import com.tanpai.menu.ui.display.DisplayScreen
import com.tanpai.menu.ui.display.DisplayViewModel
import kotlinx.coroutines.delay

private const val SCREEN_DISPLAY = "display"
private const val SCREEN_ADMIN = "admin"

private enum class PinDialog { None, Setup, Enter }

@Composable
fun TanPaiMenuApp(
    repository: MenuRepository,
    settings: SettingsStore,
    pinManager: PinManager,
    imageStore: ImageStore,
    backupManager: BackupManager
) {
    var screen by rememberSaveable { mutableStateOf(SCREEN_DISPLAY) }
    var pinDialog by remember { mutableStateOf(PinDialog.None) }

    val displayViewModel: DisplayViewModel =
        viewModel(factory = DisplayViewModel.factory(repository))
    val adminViewModel: AdminViewModel =
        viewModel(factory = AdminViewModel.factory(repository, settings, imageStore, backupManager))

    if (screen == SCREEN_ADMIN) {
        AdminScreen(viewModel = adminViewModel, onExit = { screen = SCREEN_DISPLAY })
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
            }
        )
    }

    when (pinDialog) {
        PinDialog.Setup -> PinSetupDialog(
            onConfirm = { pin ->
                pinManager.setPin(pin)
                pinDialog = PinDialog.None
                screen = SCREEN_ADMIN
            },
            onDismiss = { pinDialog = PinDialog.None }
        )
        PinDialog.Enter -> PinEntryDialog(
            pinManager = pinManager,
            onSuccess = {
                pinDialog = PinDialog.None
                screen = SCREEN_ADMIN
            },
            onDismiss = { pinDialog = PinDialog.None }
        )
        PinDialog.None -> Unit
    }
}

@Composable
private fun PinSetupDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    val valid = pin.length in 4..6 && pin.all(Char::isDigit)
    val matched = pin == confirmation

    AlertDialog(
        onDismissRequest = onDismiss,
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.admin_cancel)) }
        }
    )
}

@Composable
private fun PinEntryDialog(
    pinManager: PinManager,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var lockedSeconds by remember { mutableIntStateOf(pinManager.lockRemainingSeconds()) }

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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.admin_cancel)) }
        }
    )
}
