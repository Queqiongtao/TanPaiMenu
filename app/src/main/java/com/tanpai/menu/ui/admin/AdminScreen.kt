package com.tanpai.menu.ui.admin

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tanpai.menu.R
import com.tanpai.menu.backup.BackupFailure
import com.tanpai.menu.backup.BackupResult
import com.tanpai.menu.data.db.Category
import com.tanpai.menu.data.db.Dish
import com.tanpai.menu.domain.PriceFormatter
import com.tanpai.menu.domain.SellError
import com.tanpai.menu.domain.SellResult
import com.tanpai.menu.kiosk.exitToHome
import com.tanpai.menu.kiosk.openHomeSettings
import com.tanpai.menu.ui.theme.BackgroundStyle
import com.tanpai.menu.ui.theme.BackgroundStyles
import com.tanpai.menu.ui.theme.backgroundStyle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(viewModel: AdminViewModel, onExit: () -> Unit) {
    val menu by viewModel.menu.collectAsState()
    val backupResult by viewModel.backupResult.collectAsState()
    val resetDone by viewModel.resetDone.collectAsState()
    var selectedCategoryId by rememberSaveable { mutableLongStateOf(0L) }

    LaunchedEffect(menu.categories) {
        if (menu.categories.none { it.id == selectedCategoryId }) {
            selectedCategoryId = menu.categories.firstOrNull()?.id ?: 0L
        }
    }

    val selectedCategory = menu.categories.firstOrNull { it.id == selectedCategoryId }
    val dishes = menu.dishes.filter { it.categoryId == selectedCategoryId }

    var showNewCategory by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }
    var showNewDish by remember { mutableStateOf(false) }
    var editingDish by remember { mutableStateOf<Dish?>(null) }
    var deletingDish by remember { mutableStateOf<Dish?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val errSoldOut = stringResource(R.string.admin_err_sold_out)
    val errStockDisabled = stringResource(R.string.admin_err_stock_disabled)
    val errNoRevocable = stringResource(R.string.admin_err_no_revocable)
    val errNotFound = stringResource(R.string.admin_err_not_found)

    val onSellResult: (SellResult) -> Unit = { result ->
        message = when (result) {
            is SellResult.Success -> null
            is SellResult.Failure -> when (result.error) {
                SellError.SoldOut -> errSoldOut
                SellError.StockDisabled -> errStockDisabled
                SellError.NoRevocableRecord -> errNoRevocable
                SellError.NotFound -> errNotFound
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_title)) },
                actions = {
                    TextButton(onClick = { showSettings = true }) {
                        Text(stringResource(R.string.admin_settings))
                    }
                    TextButton(onClick = onExit) {
                        Text(stringResource(R.string.admin_back_to_display))
                    }
                }
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Surface(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Button(
                        onClick = { showNewCategory = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(stringResource(R.string.admin_add_category))
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(menu.categories, key = { it.id }) { category ->
                            val selected = category.id == selectedCategoryId
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCategoryId = category.id }
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Row {
                                    TextButton(onClick = { editingCategory = category }) {
                                        Text(stringResource(R.string.admin_edit))
                                    }
                                    TextButton(onClick = { deletingCategory = category }) {
                                        Text(stringResource(R.string.admin_delete))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = selectedCategory?.name ?: "",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { showNewDish = true },
                        enabled = selectedCategory != null
                    ) {
                        Text(stringResource(R.string.admin_add_dish))
                    }
                }
                message?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(dishes, key = { it.id }) { dish ->
                        DishRow(
                            dish = dish,
                            onSell = { viewModel.sell(dish.id, onSellResult) },
                            onUnsell = { viewModel.unsell(dish.id, onSellResult) },
                            onSoldOutChange = { viewModel.setSoldOut(dish, it) },
                            onEdit = { editingDish = dish },
                            onDelete = { deletingDish = dish }
                        )
                    }
                }
            }
        }
    }

    if (showNewCategory) {
        CategoryEditDialog(
            initial = null,
            onConfirm = { name, sortOrder -> viewModel.createCategory(name, sortOrder) },
            onDismiss = { showNewCategory = false }
        )
    }
    editingCategory?.let { category ->
        CategoryEditDialog(
            initial = category,
            onConfirm = { name, sortOrder -> viewModel.updateCategory(category.id, name, sortOrder) },
            onDismiss = { editingCategory = null }
        )
    }
    deletingCategory?.let { category ->
        ConfirmDialog(
            title = stringResource(R.string.admin_delete),
            text = stringResource(R.string.admin_delete_category_confirm, category.name),
            onConfirm = { viewModel.deleteCategory(category.id) },
            onDismiss = { deletingCategory = null }
        )
    }
    if (showNewDish && selectedCategory != null) {
        DishEditDialog(
            initial = null,
            categoryId = selectedCategory.id,
            onImportImage = { viewModel.importImage(it) },
            onCreateCaptureUri = { viewModel.createCaptureUri() },
            onConfirm = { viewModel.createDish(it) },
            onDismiss = { showNewDish = false }
        )
    }
    editingDish?.let { dish ->
        DishEditDialog(
            initial = dish,
            categoryId = dish.categoryId,
            onImportImage = { viewModel.importImage(it) },
            onCreateCaptureUri = { viewModel.createCaptureUri() },
            onConfirm = { viewModel.updateDish(it) },
            onDismiss = { editingDish = null }
        )
    }
    deletingDish?.let { dish ->
        ConfirmDialog(
            title = stringResource(R.string.admin_delete),
            text = stringResource(R.string.admin_delete_dish_confirm, dish.name),
            onConfirm = { viewModel.deleteDish(dish) },
            onDismiss = { deletingDish = null }
        )
    }
    if (showSettings) {
        SettingsDialog(
            storeName = viewModel.storeName,
            autostart = viewModel.autostartEnabled,
            showStockHint = viewModel.showStockHint,
            cardAlphaPercent = viewModel.cardAlphaPercent,
            backgroundImagePath = viewModel.backgroundImagePath,
            backgroundStyleId = viewModel.backgroundStyleId,
            backgroundScrimPercent = viewModel.backgroundScrimPercent,
            wechatQrImagePath = viewModel.wechatQrImagePath,
            onStoreNameChange = { viewModel.updateStoreName(it) },
            onAutostartChange = { viewModel.updateAutostart(it) },
            onShowStockHintChange = { viewModel.updateShowStockHint(it) },
            onCardAlphaChange = { viewModel.updateCardAlphaPercent(it) },
            onBackgroundScrimChange = { viewModel.updateBackgroundScrimPercent(it) },
            onBackgroundStyleChange = { viewModel.applyBackgroundStyle(it) },
            onImportBackgroundImage = { uri -> viewModel.applyBackgroundImage(uri) },
            onCreateCaptureUri = { viewModel.createCaptureUri() },
            onClearBackground = { viewModel.clearBackground() },
            onImportWechatQr = { uri -> viewModel.applyWechatQr(uri) },
            onClearWechatQr = { viewModel.clearWechatQr() },
            resetDone = resetDone,
            onResetSeed = { viewModel.resetToDefaultMenu() },
            onClearResetDone = { viewModel.clearResetDone() },
            backupResult = backupResult,
            onExportBackup = { viewModel.exportBackup(it) },
            onImportBackup = { viewModel.importBackup(it) },
            onClearBackupResult = { viewModel.clearBackupResult() },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun SettingsDialog(
    storeName: String,
    autostart: Boolean,
    showStockHint: Boolean,
    cardAlphaPercent: Int,
    backgroundImagePath: String?,
    backgroundStyleId: String?,
    backgroundScrimPercent: Int,
    wechatQrImagePath: String?,
    onStoreNameChange: (String) -> Unit,
    onAutostartChange: (Boolean) -> Unit,
    onShowStockHintChange: (Boolean) -> Unit,
    onCardAlphaChange: (Int) -> Unit,
    onBackgroundScrimChange: (Int) -> Unit,
    onBackgroundStyleChange: (String) -> Unit,
    onImportBackgroundImage: suspend (Uri) -> Boolean,
    onCreateCaptureUri: () -> Uri,
    onClearBackground: () -> Unit,
    onImportWechatQr: suspend (Uri) -> Boolean,
    onClearWechatQr: () -> Unit,
    resetDone: Boolean,
    onResetSeed: () -> Unit,
    onClearResetDone: () -> Unit,
    backupResult: BackupResult?,
    onExportBackup: (Uri) -> Unit,
    onImportBackup: (Uri) -> Unit,
    onClearBackupResult: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(storeName) }
    var auto by remember { mutableStateOf(autostart) }
    var showStock by remember { mutableStateOf(showStockHint) }
    var cardAlpha by remember { mutableStateOf(cardAlphaPercent / 100f) }
    var confirmExit by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var scrim by remember { mutableStateOf(backgroundScrimPercent / 100f) }
    var backgroundSet by remember { mutableStateOf(backgroundImagePath != null) }
    var selectedStyleId by remember { mutableStateOf(backgroundStyleId) }
    var qrSet by remember { mutableStateOf(wechatQrImagePath != null) }
    var failedTarget by remember { mutableStateOf<ImageTarget?>(null) }
    var deniedTarget by remember { mutableStateOf<ImageTarget?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var pendingTarget by remember { mutableStateOf<ImageTarget?>(null) }
    val scope = rememberCoroutineScope()
    val importFailedText = stringResource(R.string.settings_background_import_failed)
    val permissionDeniedText = stringResource(R.string.settings_camera_permission_denied)

    /** 按目标导入图片并刷新对应分区的状态与提示。 */
    val importForTarget: suspend (ImageTarget, Uri) -> Unit = { target, uri ->
        val ok = when (target) {
            ImageTarget.Background -> onImportBackgroundImage(uri)
            ImageTarget.WechatQr -> onImportWechatQr(uri)
        }
        if (ok) {
            when (target) {
                ImageTarget.Background -> {
                    backgroundSet = true
                    selectedStyleId = null
                }

                ImageTarget.WechatQr -> qrSet = true
            }
            failedTarget = null
        } else {
            failedTarget = target
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) onExportBackup(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> pendingImport = uri }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val target = pendingTarget
        if (uri != null && target != null) {
            scope.launch { importForTarget(target, uri) }
        }
    }
    val captureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCaptureUri
        val target = pendingTarget
        if (success && uri != null && target != null) {
            scope.launch { importForTarget(target, uri) }
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val target = pendingTarget
        if (granted && target != null) {
            val uri = onCreateCaptureUri()
            pendingCaptureUri = uri
            captureLauncher.launch(uri)
        } else {
            deniedTarget = target
        }
    }

    /** 已授权则直接拍照，否则先申请相机权限（目标留在状态里供权限回调使用）。 */
    fun startCapture(target: ImageTarget) {
        pendingTarget = target
        failedTarget = null
        deniedTarget = null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            val uri = onCreateCaptureUri()
            pendingCaptureUri = uri
            captureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_store_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.settings_autostart))
                    Switch(checked = auto, onCheckedChange = { auto = it })
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.settings_show_stock_hint))
                    Switch(checked = showStock, onCheckedChange = { showStock = it })
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.settings_card_alpha))
                    Text(text = (cardAlpha * 100).roundToInt().toString() + "%")
                }
                Slider(
                    value = cardAlpha,
                    onValueChange = { cardAlpha = it },
                    onValueChangeFinished = { onCardAlphaChange((cardAlpha * 100).roundToInt()) },
                    valueRange = 0.2f..1f,
                    steps = 15
                )
                Text(
                    text = stringResource(R.string.settings_background_style),
                    modifier = Modifier.padding(top = 16.dp)
                )
                BackgroundStyles.All.chunked(StyleGridColumns).forEach { rowStyles ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(StyleGridSpacing)
                    ) {
                        rowStyles.forEach { style ->
                            BackgroundStyleCard(
                                style = style,
                                selected = style.id == selectedStyleId,
                                onClick = {
                                    onBackgroundStyleChange(style.id)
                                    selectedStyleId = style.id
                                    backgroundSet = false
                                    failedTarget = null
                                    deniedTarget = null
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.settings_background_image))
                    Text(
                        stringResource(
                            if (backgroundSet) {
                                R.string.settings_background_set
                            } else {
                                R.string.settings_background_none
                            }
                        )
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            pendingTarget = ImageTarget.Background
                            failedTarget = null
                            deniedTarget = null
                            galleryLauncher.launch("image/*")
                        }
                    ) {
                        Text(stringResource(R.string.settings_background_choose))
                    }
                    TextButton(onClick = { startCapture(ImageTarget.Background) }) {
                        Text(stringResource(R.string.settings_background_capture))
                    }
                    TextButton(
                        enabled = backgroundSet || selectedStyleId != null,
                        onClick = {
                            onClearBackground()
                            backgroundSet = false
                            selectedStyleId = BackgroundStyles.DEFAULT_ID
                            failedTarget = null
                            deniedTarget = null
                        }
                    ) {
                        Text(stringResource(R.string.settings_background_clear))
                    }
                }
                if (failedTarget == ImageTarget.Background) {
                    Text(
                        text = importFailedText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (deniedTarget == ImageTarget.Background) {
                    Text(
                        text = permissionDeniedText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.settings_background_scrim))
                    Text(text = (scrim * 100).roundToInt().toString() + "%")
                }
                Slider(
                    value = scrim,
                    onValueChange = { scrim = it },
                    onValueChangeFinished = { onBackgroundScrimChange((scrim * 100).roundToInt()) },
                    valueRange = 0f..0.8f,
                    steps = 15
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.settings_wechat_qr))
                    Text(
                        stringResource(
                            if (qrSet) {
                                R.string.settings_wechat_qr_set
                            } else {
                                R.string.settings_wechat_qr_default
                            }
                        )
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            pendingTarget = ImageTarget.WechatQr
                            failedTarget = null
                            deniedTarget = null
                            galleryLauncher.launch("image/*")
                        }
                    ) {
                        Text(stringResource(R.string.settings_wechat_qr_choose))
                    }
                    TextButton(onClick = { startCapture(ImageTarget.WechatQr) }) {
                        Text(stringResource(R.string.settings_wechat_qr_capture))
                    }
                    TextButton(
                        enabled = qrSet,
                        onClick = {
                            onClearWechatQr()
                            qrSet = false
                            failedTarget = null
                            deniedTarget = null
                        }
                    ) {
                        Text(stringResource(R.string.settings_wechat_qr_clear))
                    }
                }
                if (failedTarget == ImageTarget.WechatQr) {
                    Text(
                        text = importFailedText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (deniedTarget == ImageTarget.WechatQr) {
                    Text(
                        text = permissionDeniedText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                TextButton(
                    onClick = { openHomeSettings(context) },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.settings_set_home))
                }
                TextButton(
                    onClick = { confirmExit = true },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(stringResource(R.string.settings_exit_home))
                }
                TextButton(
                    onClick = { exportLauncher.launch(null) },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.settings_export_backup))
                }
                TextButton(
                    onClick = { importLauncher.launch(null) },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(stringResource(R.string.settings_import_backup))
                }
                TextButton(
                    onClick = { confirmReset = true },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(stringResource(R.string.settings_reset_seed))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onStoreNameChange(name.trim())
                    onAutostartChange(auto)
                    onShowStockHintChange(showStock)
                    onDismiss()
                }
            ) { Text(stringResource(R.string.admin_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.admin_cancel)) }
        }
    )

    if (confirmExit) {
        ConfirmDialog(
            title = stringResource(R.string.settings_exit_home),
            text = stringResource(R.string.settings_exit_confirm),
            onConfirm = {
                onStoreNameChange(name.trim())
                onAutostartChange(auto)
                onShowStockHintChange(showStock)
                exitToHome(context)
                onDismiss()
            },
            onDismiss = { confirmExit = false }
        )
    }

    pendingImport?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.settings_import_backup),
            text = stringResource(R.string.settings_import_confirm),
            onConfirm = { onImportBackup(uri) },
            onDismiss = { pendingImport = null }
        )
    }

    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.settings_reset_seed),
            text = stringResource(R.string.settings_reset_seed_confirm),
            onConfirm = {
                confirmReset = false
                onResetSeed()
            },
            onDismiss = { confirmReset = false }
        )
    }

    if (resetDone) {
        ConfirmDialog(
            title = stringResource(R.string.settings_reset_seed),
            text = stringResource(R.string.settings_reset_seed_done),
            onConfirm = onClearResetDone,
            onDismiss = onClearResetDone
        )
    }

    backupResult?.let { result ->
        BackupResultDialog(result = result, onDismiss = onClearBackupResult)
    }
}

@Composable
private fun BackupResultDialog(result: BackupResult, onDismiss: () -> Unit) {
    val message = when (result) {
        is BackupResult.Success -> stringResource(
            R.string.backup_done,
            result.categories,
            result.dishes,
            result.images
        )
        is BackupResult.Failure -> when (result.failure) {
            BackupFailure.NotFound -> stringResource(R.string.backup_error_not_found)
            BackupFailure.UnsupportedFormat -> stringResource(R.string.backup_error_format)
            BackupFailure.Corrupt -> stringResource(R.string.backup_error_corrupt)
            BackupFailure.Io -> stringResource(R.string.backup_error_io)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_result_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.admin_confirm)) }
        }
    )
}

@Composable
private fun DishRow(
    dish: Dish,
    onSell: () -> Unit,
    onUnsell: () -> Unit,
    onSoldOutChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        val parts = mutableListOf("${PriceFormatter.formatYuan(dish.priceCents)} / ${dish.unit}")
        if (dish.stockEnabled) {
            parts += stringResource(R.string.display_stock_remaining, dish.stockCount)
        }
        if (dish.soldOut) {
            parts += stringResource(R.string.display_sold_out)
        } else if (!dish.stockEnabled) {
            parts += stringResource(R.string.display_in_stock)
        }
        val details = parts.joinToString(separator = "    ")
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = dish.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = details,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (dish.stockEnabled) {
                Button(onClick = onSell, enabled = !dish.soldOut) {
                    Text(stringResource(R.string.admin_sell))
                }
                TextButton(onClick = onUnsell) {
                    Text(stringResource(R.string.admin_unsell))
                }
            }
            TextButton(onClick = { onSoldOutChange(!dish.soldOut) }) {
                Text(
                    stringResource(
                        if (dish.soldOut) R.string.admin_restore else R.string.admin_sold_out_action
                    )
                )
            }
            TextButton(onClick = onEdit) { Text(stringResource(R.string.admin_edit)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.admin_delete)) }
        }
    }
}

/** 样式色卡每行个数（3 列 × 2 行铺满 6 款样式）。 */
private const val StyleGridColumns = 3

/** 样式色卡之间的横向间距。 */
private val StyleGridSpacing = 8.dp

/** 样式色卡缩略图高度。 */
private val StyleCardHeight = 64.dp

/**
 * 内置背景样式色卡：缩略渐变 + 中文名。
 *
 * 选中态用 2dp 主色描边 + 主色圆形对勾，未选中用 1dp 描边；两者形状与高度一致，切换不跳动。
 */
@Composable
private fun BackgroundStyleCard(
    style: BackgroundStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(StyleCardHeight)
                .clip(shape)
                .backgroundStyle(style)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = shape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.settings_background_style_check),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
        Text(
            text = stringResource(style.labelRes),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1
        )
    }
}

/** 设置弹窗中需要通过「相册 / 拍照」导入图片的目标分区。 */
private enum class ImageTarget {
    /** 展示页背景图。 */
    Background,

    /** 展示页微信二维码。 */
    WechatQr
}
