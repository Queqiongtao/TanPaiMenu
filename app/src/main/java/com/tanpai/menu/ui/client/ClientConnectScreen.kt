package com.tanpai.menu.ui.client

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tanpai.menu.R
import com.tanpai.menu.client.PairOutcome
import com.tanpai.menu.client.PairingClient
import kotlinx.coroutines.launch

/** 配对码位数，与主机 `PairCodeManager.CODE_LENGTH` 一致。 */
private const val CODE_LENGTH = 6

/**
 * 配对页（`docs/design/0022` 第 2.3 节、`docs/design/0019` 第 2.2 节）。
 *
 * 默认打开相机扫平板二维码，识别成功即完成配对；「手动配对」（地址 + 6 位一次性配对码 + 本机名称）
 * 保留但默认收起。相机权限被拒、无摄像头或相机初始化失败时自动展开手动表单，
 * 保证任何情况下都能配对上；手动表单里也留了「扫码配对」入口用于切回相机。
 */
@Composable
fun ClientConnectScreen(
    pairingClient: PairingClient,
    initialAddress: String,
    initialDeviceName: String,
    onPaired: () -> Unit,
    onSwitchToHost: () -> Unit,
    /** 深链接带来的二维码内容：非空时自动完成一次扫码配对（`docs/design/0022`）。 */
    pairLink: String? = null
) {
    var manual by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf(initialAddress) }
    var code by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf(initialDeviceName) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val canSubmit = !busy && address.isNotBlank() && code.length == CODE_LENGTH

    /** 扫码结果直接落盘（跳过 `POST /pair`）；失败时返回中文提示，界面继续扫描。 */
    val onScanned: (String) -> String? = { content ->
        when (val outcome = pairingClient.pairByQr(content)) {
            PairOutcome.Success -> {
                onPaired()
                null
            }

            is PairOutcome.Failed -> outcome.message
        }
    }

    // 深链接（`tanpai://pair` 或二维码里的 http 地址）自动配对一次；成功后由 onPaired 切走本页。
    LaunchedEffect(pairLink) {
        if (pairLink.isNullOrBlank()) return@LaunchedEffect
        when (val outcome = pairingClient.pairByQr(pairLink)) {
            PairOutcome.Success -> onPaired()
            is PairOutcome.Failed -> error = outcome.message
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (manual) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.client_connect_title),
            style = MaterialTheme.typography.headlineSmall
        )
        if (manual) {
            Text(
                text = stringResource(R.string.client_connect_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text(stringResource(R.string.client_address_label)) },
                placeholder = { Text(stringResource(R.string.client_address_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(CODE_LENGTH) },
                label = { Text(stringResource(R.string.client_code_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text(stringResource(R.string.client_device_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(
                enabled = canSubmit,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        when (val outcome = pairingClient.pair(address, code, deviceName)) {
                            PairOutcome.Success -> onPaired()
                            is PairOutcome.Failed -> error = outcome.message
                        }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (busy) R.string.client_connecting else R.string.client_connect))
            }
            TextButton(onClick = { manual = false; error = null }) {
                Text(stringResource(R.string.client_scan_switch))
            }
        } else {
            CameraScanner(
                onScanned = onScanned,
                onCameraUnavailable = { message ->
                    error = message
                    manual = true
                },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.client_connect_desc_scan),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { manual = true; error = null }) {
                Text(stringResource(R.string.client_manual_pair))
            }
        }
        TextButton(onClick = onSwitchToHost) {
            Text(stringResource(R.string.client_switch_to_host))
        }
    }
}
