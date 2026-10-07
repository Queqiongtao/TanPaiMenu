package com.tanpai.menu.ui.client

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import coil.compose.AsyncImage
import com.tanpai.menu.R
import com.tanpai.menu.client.ClientCommand
import com.tanpai.menu.client.ClientConnectionState
import com.tanpai.menu.client.ClientMenuState
import com.tanpai.menu.client.toDraft
import com.tanpai.menu.data.db.Dish
import com.tanpai.menu.domain.PriceFormatter
import com.tanpai.menu.net.HostAddress
import com.tanpai.menu.net.protocol.CategoryDto
import com.tanpai.menu.net.protocol.DishDto
import com.tanpai.menu.net.protocol.toEntity
import com.tanpai.menu.ui.dialogs.CategoryEditDialog
import com.tanpai.menu.ui.dialogs.ConfirmDialog
import com.tanpai.menu.ui.dialogs.DishEditDialog
import com.tanpai.menu.ui.theme.ChineseFontFamily
import com.tanpai.menu.ui.theme.PriceColors
import com.tanpai.menu.ui.theme.StatusColors
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 客户端菜品缩略图边长；与展示页保持同一量级，便于店主一眼比对。 */
private val ClientThumbnailSize = 56.dp

/** 「正在重连」状态点颜色（琥珀，区别于在线绿与失效红）。 */
private val ReconnectingColor = Color(0xFFB26A00)

/** 列表底部为悬浮按钮预留的高度：按钮 56dp + 与内容的间距 16dp + 安全余量 16dp。 */
private val FAB_CLEARANCE = 88.dp

/**
 * 计算列表底部需要为悬浮按钮预留的高度：只在「管理模式 + 选中分类（按钮会出现）」时预留。
 *
 * 抽成纯函数以便单测覆盖——真机上按钮会盖住最后一行右对齐的「沽清 / 恢复」。
 */
internal fun dishListBottomClearance(adminMode: Boolean, hasSelectedCategory: Boolean): Dp =
    if (adminMode && hasSelectedCategory) FAB_CLEARANCE else 0.dp

/** 长按菜品弹出的操作菜单目标：菜品 + 弹出位置（相对窗口的像素）。 */
private data class DishMenuTarget(val dish: DishDto, val position: IntOffset)

/**
 * 客户端菜单页（`docs/design/0019` 第 2.2 节、`docs/design/0020` 第 2.1 节）。
 *
 * 数据全部来自 [state]（`ClientSession` 的内存镜像快照），本页不直接收发网络报文：
 * 写操作一律通过 [onSubmit] 交给 `ClientSession`，提交期间禁用管理入口，
 * 失败由 Snackbar 展示主机返回的中文错误。
 *
 * [adminMode] 由 `ClientApp` 持有：进入管理模式后每行显示操作按钮、底部出现「新增菜品」。
 * 「展示设置 / 恢复默认菜单」由 `ClientApp` 的设置弹窗承载，本页只负责菜单列表与二次确认。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientMenuScreen(
    state: ClientMenuState,
    adminMode: Boolean,
    snackbarHostState: SnackbarHostState,
    onToggleAdminMode: () -> Unit,
    onOpenSettings: () -> Unit,
    /** 顶栏「⋯ 更多」已取消；本页只保留二次确认，真正的提交由 [onResetMenu] 交给 `ClientApp`。 */
    onResetMenu: () -> Unit,
    onCreateCaptureUri: () -> Uri,
    onImportImage: suspend (Uri) -> String?,
    onSubmit: suspend (ClientCommand) -> Result<Unit>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedCategoryId by rememberSaveable { mutableLongStateOf(0L) }
    var busy by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryDto?>(null) }
    var deletingCategory by remember { mutableStateOf<CategoryDto?>(null) }
    var actionsCategory by remember { mutableStateOf<CategoryDto?>(null) }
    var showNewDish by remember { mutableStateOf(false) }
    var editingDish by remember { mutableStateOf<Dish?>(null) }

    /** 待确认删除的菜品：直接持有契约 DTO（长按菜单传入的也是 DTO）。 */
    var deletingDish by remember { mutableStateOf<DishDto?>(null) }

    /** 长按弹出的菜品操作菜单：记录菜品与整行位置（按行定位 Popup）。 */
    var dishMenu by remember { mutableStateOf<DishMenuTarget?>(null) }

    /** 各菜品行在窗口中的位置；长按菜单按行定位。 */
    val rowOrigins = remember { mutableStateMapOf<Long, Offset>() }
    var showResetConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.categories) {
        if (state.categories.none { it.id == selectedCategoryId }) {
            selectedCategoryId = state.categories.firstOrNull()?.id ?: 0L
        }
    }

    /** 提交一条写命令：期间禁用全部管理入口，失败提示主机中文错误（`docs/design/0020` 第 2.2 节）。 */
    fun submit(command: ClientCommand) {
        scope.launch {
            busy = true
            val error = onSubmit(command).exceptionOrNull()
            busy = false
            if (error != null) {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.client_submit_failed, error.message.orEmpty())
                )
            }
        }
    }

    val selectedCategory = state.categories.firstOrNull { it.id == selectedCategoryId }
    val editingEnabled = adminMode && state.canEdit && !busy
    val imagePreview: (String) -> Any? = { path -> HostAddress.imageUrl(state.hostAddress.orEmpty(), path) }

    Scaffold(
        topBar = {
            ClientTopBar(
                state = state,
                adminMode = adminMode,
                canManage = state.canEdit,
                onToggleAdminMode = onToggleAdminMode,
                onOpenSettings = onOpenSettings
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (editingEnabled && selectedCategory != null) {
                ExtendedFloatingActionButton(onClick = { showNewDish = true }) {
                    Text(stringResource(R.string.client_admin_add_dish))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.readOnly) {
                ReadOnlyBanner(state)
            } else if (!state.clientEditsEnabled) {
                InfoBanner(stringResource(R.string.client_edit_disabled))
            }
            if (state.categories.isEmpty()) {
                CenterHint(stringResource(R.string.display_no_categories))
            } else {
                CategoryRow(
                    categories = state.categories,
                    selectedId = selectedCategoryId,
                    adminMode = editingEnabled,
                    onSelect = { selectedCategoryId = it },
                    onAddCategory = { showNewCategory = true },
                    onLongPress = { actionsCategory = it },
                    onRequestDelete = { deletingCategory = it }
                )
                val dishes = state.dishes.filter { it.categoryId == selectedCategoryId }
                if (dishes.isEmpty()) {
                    CenterHint(stringResource(R.string.display_no_dishes))
                } else {
                    // 管理模式下每行末尾的「沽清 / 恢复」右对齐，会落在右下角悬浮按钮附近，
                    // 因此列表底部按按钮高度 + 间距留白，避免遮挡（`docs/design/0023` 第 2.1 节）。
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = dishListBottomClearance(editingEnabled, selectedCategory != null)
                        )
                    ) {
                        items(dishes, key = { it.id }) { dish ->
                            ClientDishRow(
                                dish = dish,
                                hostAddress = state.hostAddress,
                                adminMode = adminMode,
                                busy = busy,
                                canEdit = state.canEdit,
                                onEdit = { editingDish = dish.toEntity() },
                                onSell = { submit(ClientCommand.Sell(dish.id)) },
                                onUnsell = { submit(ClientCommand.Unsell(dish.id)) },
                                onSoldOutChange = { soldOut ->
                                    submit(ClientCommand.SetSoldOut(dish.id, soldOut))
                                },
                                onPositioned = { position -> rowOrigins[dish.id] = position },
                                onLongPress = {
                                    val origin = rowOrigins[dish.id] ?: Offset.Zero
                                    dishMenu = DishMenuTarget(
                                        dish = dish,
                                        position = IntOffset(origin.x.roundToInt(), origin.y.roundToInt())
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 菜品长按菜单：只放「删除菜品」，删除仍走二次确认（`docs/design/0023` 第 2.1 节）。
    dishMenu?.let { target ->
        Popup(
            alignment = Alignment.TopStart,
            offset = target.position,
            onDismissRequest = { dishMenu = null }
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 3.dp,
                shadowElevation = 6.dp
            ) {
                TextButton(
                    onClick = {
                        dishMenu = null
                        deletingDish = target.dish
                    }
                ) {
                    Text(stringResource(R.string.client_dish_delete))
                }
            }
        }
    }

    if (showNewCategory) {
        CategoryEditDialog(
            initial = null,
            onConfirm = { name, sortOrder -> submit(ClientCommand.CreateCategory(name, sortOrder)) },
            onDismiss = { showNewCategory = false }
        )
    }

    editingCategory?.let { category ->
        CategoryEditDialog(
            initial = category.toEntity(),
            onConfirm = { name, sortOrder ->
                submit(ClientCommand.UpdateCategory(category.id, name, sortOrder))
            },
            onDismiss = { editingCategory = null }
        )
    }

    actionsCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { actionsCategory = null },
            title = { Text(category.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        actionsCategory = null
                        editingCategory = category
                    }
                ) { Text(stringResource(R.string.client_admin_category_edit)) }
            },
            dismissButton = {
                Column {
                    TextButton(
                        onClick = {
                            actionsCategory = null
                            deletingCategory = category
                        }
                    ) { Text(stringResource(R.string.admin_delete)) }
                    TextButton(onClick = { actionsCategory = null }) {
                        Text(stringResource(R.string.admin_cancel))
                    }
                }
            }
        )
    }

    deletingCategory?.let { category ->
        ConfirmDialog(
            title = stringResource(R.string.admin_delete),
            text = stringResource(R.string.admin_delete_category_confirm, category.name),
            onConfirm = { submit(ClientCommand.DeleteCategory(category.id)) },
            onDismiss = { deletingCategory = null }
        )
    }

    if (showNewDish && selectedCategory != null) {
        DishEditDialog(
            initial = null,
            categoryId = selectedCategory.id,
            onImportImage = onImportImage,
            onCreateCaptureUri = onCreateCaptureUri,
            onConfirm = { dish -> submit(ClientCommand.CreateDish(selectedCategory.id, dish.toDraft())) },
            onDismiss = { showNewDish = false },
            imagePreview = imagePreview
        )
    }

    editingDish?.let { dish ->
        DishEditDialog(
            initial = dish,
            categoryId = dish.categoryId,
            onImportImage = onImportImage,
            onCreateCaptureUri = onCreateCaptureUri,
            onConfirm = { edited -> submit(ClientCommand.UpdateDish(edited.id, edited.toDraft())) },
            onDismiss = { editingDish = null },
            imagePreview = imagePreview
        )
    }

    deletingDish?.let { dish ->
        ConfirmDialog(
            title = stringResource(R.string.admin_delete),
            text = stringResource(R.string.admin_delete_dish_confirm, dish.name),
            onConfirm = { submit(ClientCommand.DeleteDish(dish.id)) },
            onDismiss = { deletingDish = null }
        )
    }

    if (showResetConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.settings_reset_seed),
            text = stringResource(R.string.settings_reset_seed_confirm),
            onConfirm = {
                showResetConfirm = false
                onResetMenu()
            },
            onDismiss = { showResetConfirm = false }
        )
    }
}

@Composable
private fun ClientTopBar(
    state: ClientMenuState,
    adminMode: Boolean,
    canManage: Boolean,
    onToggleAdminMode: () -> Unit,
    onOpenSettings: () -> Unit
) {
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
                    text = state.storeName ?: stringResource(R.string.client_menu_title),
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
            if (canManage) {
                TextButton(onClick = onToggleAdminMode) {
                    Text(
                        stringResource(
                            if (adminMode) R.string.client_admin_exit else R.string.client_admin_enter
                        )
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

/** 在线但不可编辑（平板关闭了「允许手机编辑」）时的提示条。 */
@Composable
private fun InfoBanner(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CategoryRow(
    categories: List<CategoryDto>,
    selectedId: Long,
    adminMode: Boolean,
    onSelect: (Long) -> Unit,
    onAddCategory: () -> Unit,
    onLongPress: (CategoryDto) -> Unit,
    onRequestDelete: (CategoryDto) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { category ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = category.id == selectedId,
                    onClick = { onSelect(category.id) },
                    label = { Text(category.name) },
                    modifier = Modifier.combinedClickable(
                        onClick = { onSelect(category.id) },
                        onLongClick = if (adminMode) ({ onLongPress(category) }) else null
                    )
                )
                // 管理模式下给每个分类一个显式删除入口：长按在 MIUI 等系统上不可靠（`docs/design/0023` 第 2.1 节）。
                if (adminMode) {
                    TextButton(
                        onClick = { onRequestDelete(category) },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("×", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        if (adminMode) {
            AssistChip(
                onClick = onAddCategory,
                label = { Text(stringResource(R.string.client_admin_add_category)) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClientDishRow(
    dish: DishDto,
    hostAddress: String?,
    adminMode: Boolean,
    busy: Boolean,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onSell: () -> Unit,
    onUnsell: () -> Unit,
    onSoldOutChange: (Boolean) -> Unit,
    /** 整行在窗口中的位置（长按菜单定位用）。 */
    onPositioned: (Offset) -> Unit,
    /** 长按整行：弹出操作菜单（删除入口放在这里，见 `docs/design/0023` 第 2.1 节）。 */
    onLongPress: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 记录整行在窗口中的位置，长按菜单才能贴着这一行弹出。
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInWindow()
                onPositioned(Offset(position.x.toFloat(), position.y.toFloat()))
            }
            .combinedClickable(
                enabled = adminMode && canEdit && !busy,
                onClick = onEdit,
                onLongClick = onLongPress
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
        if (adminMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (busy) {
                    Text(
                        text = stringResource(R.string.client_submitting),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    if (dish.stockEnabled) {
                        TextButton(onClick = onSell, enabled = !dish.soldOut && canEdit) {
                            Text(stringResource(R.string.admin_sell))
                        }
                        TextButton(onClick = onUnsell, enabled = canEdit) {
                            Text(stringResource(R.string.admin_unsell))
                        }
                    }
                    TextButton(onClick = { onSoldOutChange(!dish.soldOut) }, enabled = canEdit) {
                        Text(
                            stringResource(
                                if (dish.soldOut) R.string.admin_restore else R.string.admin_sold_out_action
                            )
                        )
                    }
                }
            }
        }
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
