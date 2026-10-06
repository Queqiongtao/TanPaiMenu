package com.tanpai.menu.ui.display

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tanpai.menu.R
import com.tanpai.menu.data.db.Category
import com.tanpai.menu.data.db.Dish
import com.tanpai.menu.domain.PriceFormatter
import com.tanpai.menu.kiosk.KeepScreenOn
import com.tanpai.menu.ui.theme.BackgroundStyles
import com.tanpai.menu.ui.theme.ChineseFontFamily
import com.tanpai.menu.ui.theme.StatusColors
import com.tanpai.menu.ui.theme.StoreNameColors
import com.tanpai.menu.ui.theme.StoreNameFontFamily
import com.tanpai.menu.ui.theme.backgroundStyle
import java.io.File
import kotlinx.coroutines.withTimeoutOrNull

/** 顶部条高度：店名招牌与分类胶囊共用，高度固定不随内容伸缩。 */
private val TopBarHeight = 72.dp
/** 店名最大宽度，超长省略，避免挤占分类胶囊位置。 */
private val StoreNameMaxWidth = 320.dp
/** 店名最大宽度占顶栏宽的比例（居中后左右各占 20%）。 */
private const val StoreNameWidthFraction = 0.4f
/** 分类胶囊区最大宽度占顶栏宽的比例，与店名限宽之和不超过 100%。 */
private const val ChipRowWidthFraction = 0.3f
/** 分类胶囊之间的间距。 */
private val ChipSpacing = 8.dp
/** 长按店名进入管理的时长。 */
private const val AdminLongPressMillis = 2_000L

/** 店名招牌固定高度，垂直居中于顶部条内。 */
private val StoreNameCapsuleHeight = 68.dp
/** 店名招牌横向内边距。 */
private val StoreNameHorizontalPadding = 20.dp
/** 店名字号与行高（字数不超过 [StoreNameLongThreshold] 时）。 */
private val StoreNameLargeSize = 48.sp
private val StoreNameLargeLineHeight = 56.sp
/** 店名过长时的降档字号与行高，避免在限宽内被省略。 */
private val StoreNameSmallSize = 36.sp
private val StoreNameSmallLineHeight = 44.sp
/** 店名字数超过该值时降档。 */
private const val StoreNameLongThreshold = 6

/** 顶栏微信二维码入口：小码边长。 */
private val WechatQrBadgeSize = 56.dp
/** 小码白底容器的内边距，四周留白便于扫码识别。 */
private val WechatQrBadgePadding = 4.dp
/** 二维码入口块的可点区域内边距。 */
private val WechatQrEntryPadding = 4.dp

private val ContentPadding = 12.dp
/** 顶部条与网格之间的间距（顶栏加高后收紧）。 */
private val TopBarGap = 8.dp
/** 卡片固定高度：56dp 缩略图 + 上下各 6dp 内边距。 */
private val CardHeight = 68.dp
/** 网格列间距，保持卡片宽度与既有版式一致。 */
private val GridColumnSpacing = 16.dp
/** 网格行间距（顶栏加高后收紧，保证 12 道一屏不滚动）。 */
private val GridRowSpacing = 12.dp
/** 网格底部留白。 */
private val GridBottomPadding = 24.dp
private val ThumbnailSize = 56.dp

/** 卡片内横向内边距。 */
private val CardHorizontalPadding = 10.dp
/** 卡片内纵向内边距，与 56dp 缩略图共同决定卡片高度。 */
private val CardVerticalPadding = 6.dp
/** 缩略图与文字之间的间距。 */
private val ThumbnailGap = 8.dp
/** 菜名 / 价格 / 状态三列之间的间距。 */
private val ColumnGap = 5.dp
/** 价格列定宽，保证跨卡片左边界一致。 */
private val PriceColumnWidth = 100.dp
/** 状态列定宽（有货 / 余 N / 已售完徽标共用）。 */
private val StatusColumnWidth = 68.dp

@Composable
fun DisplayScreen(
    viewModel: DisplayViewModel,
    storeName: String,
    showStockHint: Boolean,
    cardAlpha: Float = 1f,
    backgroundImagePath: String? = null,
    backgroundStyleId: String? = null,
    backgroundScrim: Float = 0.3f,
    wechatQrImagePath: String? = null,
    onRequestAdmin: () -> Unit,
    modifier: Modifier = Modifier
) {
    KeepScreenOn()
    val menu by viewModel.menu.collectAsState()
    val categories = menu.categories
    var selectedCategoryId by rememberSaveable { mutableLongStateOf(0L) }

    LaunchedEffect(categories) {
        if (categories.none { it.id == selectedCategoryId }) {
            selectedCategoryId = categories.firstOrNull()?.id ?: 0L
        }
    }

    val dishes = remember(menu.dishes, selectedCategoryId) {
        menu.dishes.filter { it.categoryId == selectedCategoryId }
    }

    val context = LocalContext.current
    val backgroundFile = backgroundImagePath?.let { File(context.filesDir, it) }
    val hasBackgroundImage = backgroundFile != null && backgroundFile.exists()
    val backgroundStyle = BackgroundStyles.byId(backgroundStyleId)
    val wechatQrFile = wechatQrImagePath
        ?.let { File(context.filesDir, it) }
        ?.takeIf { it.exists() }
    var qrExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // 背景优先级：自定义图 > 内置样式 > 不渲染（回退 M3 纯色底）。
        when {
            hasBackgroundImage -> AsyncImage(
                model = backgroundFile,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            backgroundStyle != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .backgroundStyle(backgroundStyle)
            )
        }
        if (hasBackgroundImage || backgroundStyle != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = backgroundScrim))
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ContentPadding)
        ) {
            DisplayTopBar(
                storeName = storeName,
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                wechatQrFile = wechatQrFile,
                onWechatQrClick = { qrExpanded = true },
                onSelect = { selectedCategoryId = it },
                onRequestAdmin = onRequestAdmin
            )
            Spacer(modifier = Modifier.height(TopBarGap))
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    categories.isEmpty() -> CenterHint(stringResource(R.string.display_no_categories))
                    dishes.isEmpty() -> CenterHint(stringResource(R.string.display_no_dishes))
                    else -> DishGrid(
                        dishes = dishes,
                        showStockHint = showStockHint,
                        cardAlpha = cardAlpha
                    )
                }
            }
        }
        if (qrExpanded && wechatQrFile != null) {
            WechatQrOverlay(qrFile = wechatQrFile, onDismiss = { qrExpanded = false })
        }
    }
}

/**
 * 顶部条：店名做成水平居中的渐变招牌（紫罗兰渐变底 + 半透明白描边 + 投影，长按 2 秒进入管理），
 * 店名与胶囊分别限宽（0.4 / 0.3 顶栏宽），最大宽度之和不超过顶栏宽，保证不重叠。
 * 左侧为微信二维码入口（小码 + 「加微信」，未设置时不渲染），点击弹出放大浮层。
 * 始终渲染，保证分类为空时管理入口仍可达。
 */
@Composable
private fun DisplayTopBar(
    storeName: String,
    categories: List<Category>,
    selectedCategoryId: Long,
    wechatQrFile: File?,
    onWechatQrClick: () -> Unit,
    onSelect: (Long) -> Unit,
    onRequestAdmin: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(TopBarHeight)
    ) {
        val storeNameMaxWidth = minOf(StoreNameMaxWidth, maxWidth * StoreNameWidthFraction)
        val displayName = storeName.ifBlank { stringResource(R.string.app_name) }
        val nameTooLong = displayName.length > StoreNameLongThreshold
        val nameStyle = MaterialTheme.typography.headlineSmall.copy(
            fontSize = if (nameTooLong) StoreNameSmallSize else StoreNameLargeSize,
            lineHeight = if (nameTooLong) StoreNameSmallLineHeight else StoreNameLargeLineHeight
        )
        val capsuleShape = RoundedCornerShape(50)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = storeNameMaxWidth)
                .height(StoreNameCapsuleHeight)
                .shadow(
                    elevation = 8.dp,
                    shape = capsuleShape,
                    clip = false,
                    ambientColor = StoreNameColors.Shadow,
                    spotColor = StoreNameColors.Shadow
                )
                .clip(capsuleShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(StoreNameColors.GradientStart, StoreNameColors.GradientEnd)
                    )
                )
                .border(width = 1.dp, color = StoreNameColors.Border, shape = capsuleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            val released = withTimeoutOrNull(AdminLongPressMillis) { awaitRelease() }
                            if (released == null) onRequestAdmin()
                        }
                    )
                }
                .padding(horizontal = StoreNameHorizontalPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayName,
                style = nameStyle,
                fontFamily = StoreNameFontFamily,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (wechatQrFile != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onWechatQrClick)
                    .padding(WechatQrEntryPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = wechatQrFile,
                    contentDescription = stringResource(R.string.display_wechat_qr_hint),
                    modifier = Modifier
                        .size(WechatQrBadgeSize)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .padding(WechatQrBadgePadding),
                    contentScale = ContentScale.Fit
                )
                Text(
                    text = stringResource(R.string.display_wechat_qr_hint),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                    fontFamily = ChineseFontFamily,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }
        if (categories.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .widthIn(max = maxWidth * ChipRowWidthFraction)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                categories.forEach { category ->
                    CategoryChip(
                        name = category.name,
                        selected = category.id == selectedCategoryId,
                        onClick = { onSelect(category.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
            fontFamily = ChineseFontFamily,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 2 列网格：卡片固定 [CardHeight] 高、列距 [GridColumnSpacing]、行距 [GridRowSpacing]，
 * 12 道一屏铺满，超过 12 道则滚动。
 */
@Composable
private fun DishGrid(dishes: List<Dish>, showStockHint: Boolean, cardAlpha: Float) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(GridColumnSpacing),
        verticalArrangement = Arrangement.spacedBy(GridRowSpacing),
        contentPadding = PaddingValues(bottom = GridBottomPadding)
    ) {
        items(dishes, key = { it.id }) { dish ->
            DishCard(
                dish = dish,
                showStockHint = showStockHint,
                cardAlpha = cardAlpha,
                modifier = Modifier.height(CardHeight)
            )
        }
    }
}

@Composable
fun DishCard(
    dish: Dish,
    showStockHint: Boolean = true,
    cardAlpha: Float = 1f,
    modifier: Modifier = Modifier
) {
    val contentAlpha = if (dish.soldOut) 0.45f else 1f
    val baseCardColors = CardDefaults.cardColors()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = baseCardColors.copy(
            containerColor = baseCardColors.containerColor.copy(alpha = cardAlpha)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = CardHorizontalPadding, vertical = CardVerticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.alpha(contentAlpha)) { DishThumbnail(dish) }
            Spacer(modifier = Modifier.width(ThumbnailGap))
            Text(
                text = dish.name,
                modifier = Modifier
                    .weight(1f)
                    .basicMarquee(initialDelayMillis = 1200, repeatDelayMillis = 1600)
                    .alpha(contentAlpha),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = ChineseFontFamily,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
            Spacer(modifier = Modifier.width(ColumnGap))
            Text(
                text = "${PriceFormatter.formatYuan(dish.priceCents)} / ${dish.unit}",
                modifier = Modifier
                    .width(PriceColumnWidth)
                    .alpha(contentAlpha),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                fontFamily = ChineseFontFamily,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(ColumnGap))
            Box(
                modifier = Modifier.width(StatusColumnWidth),
                contentAlignment = Alignment.CenterStart
            ) {
                if (dish.soldOut) {
                    SoldOutBadge()
                } else if (showStockHint) {
                    val status = stockStatus(dish)
                    StatusBadge(
                        text = status.text,
                        containerColor = status.containerColor,
                        contentColor = status.contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun stockStatus(dish: Dish): StockStatus = when {
    dish.stockEnabled -> StockStatus(
        text = stringResource(R.string.display_stock_remaining, dish.stockCount),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    else -> StockStatus(
        text = stringResource(R.string.display_in_stock),
        containerColor = StatusColors.InStockContainer,
        contentColor = StatusColors.InStockContent
    )
}

private data class StockStatus(
    val text: String,
    val containerColor: Color,
    val contentColor: Color
)

@Composable
private fun DishThumbnail(dish: Dish) {
    val context = LocalContext.current
    val file = dish.imagePath?.let { File(context.filesDir, it) }
    if (file != null && file.exists()) {
        AsyncImage(
            model = file,
            contentDescription = dish.name,
            modifier = Modifier
                .size(ThumbnailSize)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(ThumbnailSize)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dish.name.take(1),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = ChineseFontFamily,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * 状态徽标：有货 / 余 N / 已售完共用，保证三态形状、内边距与字号完全一致，
 * 仅容器色与文字色不同。
 */
@Composable
private fun StatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = ChineseFontFamily,
            color = contentColor,
            maxLines = 1
        )
    }
}

/** 售完徽标：红底白字，独立于「显示是否有货」开关，始终显示。 */
@Composable
private fun SoldOutBadge(modifier: Modifier = Modifier) {
    StatusBadge(
        text = stringResource(R.string.display_sold_out),
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
        modifier = modifier
    )
}

@Composable
private fun CenterHint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 200)
@Composable
private fun DishCardPreview() {
    MaterialTheme {
        DishCard(
            dish = Dish(
                id = 1L,
                categoryId = 1L,
                name = "芝士玉米鲜肉",
                priceCents = 1400,
                unit = "份",
                stockEnabled = false,
                updatedAt = 0L
            )
        )
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 200)
@Composable
private fun SoldOutDishCardPreview() {
    MaterialTheme {
        DishCard(
            dish = Dish(
                id = 2L,
                categoryId = 1L,
                name = "全家福",
                priceCents = 2000,
                unit = "份",
                stockEnabled = true,
                stockCount = 0,
                soldOut = true,
                updatedAt = 0L
            )
        )
    }
}
