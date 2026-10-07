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
 * 配对页（`docs/design/0019` 第 2.2 节）：输入主机地址 + 6 位一次性配对码 + 本机名称。
 *
 * 只在用户点击「连接」时发起一次 `POST /pair`；失败按主机返回的中文 message 就地提示。
 */
@Composable
fun ClientConnectScreen(
    pairingClient: PairingClient,
    initialAddress: String,
    initialDeviceName: String,
    onPaired: () -> Unit,
    onSwitchToHost: () -> Unit
) {
    var address by remember { mutableStateOf(initialAddress) }
    var code by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf(initialDeviceName) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val canSubmit = !busy && address.isNotBlank() && code.length == CODE_LENGTH

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.client_connect_title),
            style = MaterialTheme.typography.headlineSmall
        )
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
        TextButton(onClick = onSwitchToHost) {
            Text(stringResource(R.string.client_switch_to_host))
        }
    }
}