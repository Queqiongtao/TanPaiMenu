package com.tanpai.menu.ui.client

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tanpai.menu.R
import com.tanpai.menu.client.ClientMenuState

/**
 * 客户端设置弹窗（`docs/design/0018` 第 2.2 节）：查看连接信息、重新连接 / 重新配对 / 切回平板主机。
 */
@Composable
fun ClientSettingsDialog(
    state: ClientMenuState,
    onReconnect: () -> Unit,
    onRepair: () -> Unit,
    onSwitchToHost: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.client_settings_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        R.string.client_settings_host,
                        state.hostAddress ?: stringResource(R.string.client_settings_no_host)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(
                        R.string.client_settings_state,
                        connectionLabel(state.connection)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(
                        R.string.client_settings_edits,
                        stringResource(
                            if (state.clientEditsEnabled) {
                                R.string.client_edits_allowed
                            } else {
                                R.string.client_edits_denied
                            }
                        )
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!state.clientEditsEnabled) {
                    Text(
                        text = stringResource(R.string.client_edit_disabled_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state.error?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = stringResource(R.string.client_settings_read_only_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.client_confirm)) }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onReconnect) { Text(stringResource(R.string.client_reconnect)) }
                TextButton(onClick = onRepair) { Text(stringResource(R.string.client_repair)) }
                TextButton(onClick = onSwitchToHost) {
                    Text(stringResource(R.string.client_switch_to_host))
                }
            }
        }
    )
}
