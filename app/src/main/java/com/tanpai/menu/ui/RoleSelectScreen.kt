package com.tanpai.menu.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tanpai.menu.R
import com.tanpai.menu.data.Role

/**
 * 首启角色选择页（`docs/design/0018` 第 2.1、2.2 节）。
 *
 * 同一个 APK 两种形态：平板当主机（展示菜单 + 对外服务），手机当客户端（连主机看菜单）。
 * 只在 [Role] 尚未落盘时出现；选择后可在设置里修改。
 */
@Composable
fun RoleSelectScreen(onChosen: (Role) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.role_select_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = stringResource(R.string.role_select_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        RoleCard(
            titleRes = R.string.role_select_host_title,
            descRes = R.string.role_select_host_desc,
            modifier = Modifier.padding(top = 24.dp),
            onClick = { onChosen(Role.HOST) }
        )
        RoleCard(
            titleRes = R.string.role_select_client_title,
            descRes = R.string.role_select_client_desc,
            modifier = Modifier.padding(top = 16.dp),
            onClick = { onChosen(Role.CLIENT) }
        )
    }
}

@Composable
private fun RoleCard(
    titleRes: Int,
    descRes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(descRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}