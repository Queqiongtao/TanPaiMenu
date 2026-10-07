package com.tanpai.menu.ui.client

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tanpai.menu.R
import com.tanpai.menu.client.ClientConnectionState
import com.tanpai.menu.client.ClientMenuState
import com.tanpai.menu.domain.PriceFormatter
import com.tanpai.menu.net.HostAddress
import com.tanpai.menu.net.protocol.CategoryDto
import com.tanpai.menu.net.protocol.DishDto
import com.tanpai.menu.ui.theme.ChineseFontFamily
import com.tanpai.menu.ui.theme.PriceColors
import com.tanpai.menu.ui.theme.StatusColors

/** 客户端菜品缩略图边长；与展示页保持同一量级，便于店主一眼比对。 */
private val ClientThumbnailSize = 56.dp

/** 「正在重连」状态点颜色（琥珀，区别于在线绿与失效红）。 */
private val ReconnectingColor = Color(0xFFB26A00)

/**
 * 客户端只读菜单页（`docs/design/0019` 第 2.2、2.3 节）。
 *
 * 数据全部来自 [state]（`ClientSession` 的内存镜像快照）；本页不发起任何写请求，
 * 断线时以只读条幅提示，菜单内容保留最后一次同步结果。
 */
@Composable
fun ClientMenuScreen(
    state: ClientMenuState,
    onOpenSettings: () -> Unit
) {
    var selectedCategoryId by rememberSaveable { mutableLongStateOf(0L) }

    LaunchedEffect(state.categories) {
        if (state.categories.none { it.id == selectedCategoryId }) {
            selectedCategoryId = state.categories.firstOrNull()?.id ?: 0L
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ClientTopBar(state = state, onOpenSettings = onOpenSettings)
        if (state.readOnly) ReadOnlyBanner(state)
        if (state.categories.isEmpty()) {
            CenterHint(stringResource(R.string.display_no_categories))
        } else {
            CategoryRow(
                categories = state.categories,
                selectedId = selectedCategoryId,
                onSelect = { selectedCategoryId = it }
            )
            val dishes = state.dishes.filter { it.categoryId == selectedCategoryId }
            if (dishes.isEmpty()) {
                CenterHint(stringResource(R.string.display_no_dishes))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(dishes, key = { it.id }) { dish ->
                        ClientDishRow(dish = dish, hostAddress = state.hostAddress)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientTopBar(state: ClientMenuState, onOpenSettings: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(connectionColor(state.connection))
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.client_menu_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = connectionLabel(state.connection),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.hostAddress?.let { address ->
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.client_settings))
            }
        }
    }
}

@Composable
private fun ReadOnlyBanner(state: ClientMenuState) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            state.error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(
                text = stringResource(R.string.client_read_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryRow(
    categories: List<CategoryDto>,
    selectedId: Long,
    onSelect: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { category ->
            FilterChip(
                selected = category.id == selectedId,
                onClick = { onSelect(category.id) },
                label = { Text(category.name) }
            )
        }
    }
}

@Composable
private fun ClientDishRow(dish: DishDto, hostAddress: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClientDishThumbnail(dish = dish, hostAddress = hostAddress)
        Spacer(Modifier.width(12.dp))
        Text(
            text = dish.name,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = ChineseFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = PriceFormatter.formatYuan(dish.priceCents),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = PriceColors.Content
        )
        ClientStatusBadge(dish = dish, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ClientDishThumbnail(dish: DishDto, hostAddress: String?) {
    val url = dish.imagePath?.let { HostAddress.imageUrl(hostAddress.orEmpty(), it) }
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = dish.name,
            modifier = Modifier
                .size(ClientThumbnailSize)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(ClientThumbnailSize)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dish.name.take(1),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/** 状态徽标：售完（红底白字）与余量（浅绿）两态，样式与展示页保持一致。 */
@Composable
private fun ClientStatusBadge(dish: DishDto, modifier: Modifier = Modifier) {
    val soldOut = dish.soldOut || (dish.stockEnabled && dish.stockCount <= 0)
    val showStock = dish.stockEnabled && !soldOut
    if (!soldOut && !showStock) return

    Surface(
        modifier = modifier,
        color = if (soldOut) MaterialTheme.colorScheme.error else StatusColors.InStockContainer,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = if (soldOut) {
                stringResource(R.string.display_sold_out)
            } else {
                stringResource(R.string.display_stock_remaining, dish.stockCount)
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (soldOut) MaterialTheme.colorScheme.onError else StatusColors.InStockContent,
            maxLines = 1
        )
    }
}

@Composable
private fun CenterHint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun connectionColor(connection: ClientConnectionState): Color = when (connection) {
    ClientConnectionState.ONLINE -> StatusColors.InStockContent
    ClientConnectionState.CONNECTING,
    ClientConnectionState.RECONNECTING -> ReconnectingColor

    ClientConnectionState.UNAUTHORIZED,
    ClientConnectionState.DISCONNECTED -> Color(0xFFB3261E)
}

/** 连接状态中文文案；顶栏状态点与设置弹窗共用。 */
@Composable
internal fun connectionLabel(connection: ClientConnectionState): String = stringResource(
    when (connection) {
        ClientConnectionState.ONLINE -> R.string.client_state_online
        ClientConnectionState.CONNECTING -> R.string.client_state_connecting
        ClientConnectionState.RECONNECTING -> R.string.client_state_reconnecting
        ClientConnectionState.UNAUTHORIZED -> R.string.client_state_unauthorized
        ClientConnectionState.DISCONNECTED -> R.string.client_state_disconnected
    }
)