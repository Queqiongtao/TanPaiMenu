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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.withStyle
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
import com.tanpai.menu.ui.theme.PriceColors
import com.tanpai.menu.ui.theme.StatusColors
import com.tanpai.menu.ui.theme.StoreNameColors
import com.tanpai.menu.ui.theme.StoreNameFontFamily
import com.tanpai.menu.ui.theme.backgroundStyle
import java.io.File
import kotlinx.coroutines.withTimeoutOrNull

/** 店名字数超过该值时降档。 */
private const val StoreNameLongThreshold = 6
/** 店名最大宽度占顶栏宽的比例（居中后左右各占 20%）。 */
private const val StoreNameWidthFraction = 0.4f
/** 分类胶囊区最大宽度占顶栏宽的比例，与店名限宽之和不超过 100%。 */
private const val ChipRowWidthFraction = 0.3f
/** 分类胶囊之间的间距。 */
private val ChipSpacing = 8.dp
/** 长按店名进入管理的时长。 */
private const val AdminLongPressMillis = 2_000L

/** 顶栏与二维码小码圆角。 */
private val TopBarCorner = 12.dp
private val WechatQrBadgeCorner = 10.dp
/** 二维码入口块的可点区域内边距。 */
private val WechatQrEntryPadding = 4.dp
/** 小码白底容器的内边距，四周留白便于扫码识别。 */
private val WechatQrBadgePadding = 4.dp

/** 顶部条与网格之间的间距（顶栏加高后收紧）。 */
private val TopBarGap = 8.dp
/** 网格底部留白。 */
private val GridBottomPadding = 24.dp
/** 展示页内容内边距（单行 / 两行档一致）。 */
private val ContentPadding = 12.dp

/** 价格胶囊圆角与内边距（两档一致，配色见 `ui/theme/PriceColors.kt`）。 */
private val PriceTagCorner = 10.dp
private val PriceTagHorizontalPadding = 10.dp
private val PriceTagVerticalPadding = 4.dp
/** 单位文字相对价格数字的透明度，避免抢焦点。 */
private const val PriceUnitContentAlpha = 0.72f

/** 卡片与缩略图圆角。 */
private val CardCorner = 14.dp
private val ThumbnailCorner = 12.dp
/** 状态徽标圆角与内边距。 */
private val StatusBadgeCorner = 8.dp
private val StatusBadgeHorizontalPadding = 8.dp
private val StatusBadgeVerticalPadding = 4.dp
/** 售完卡片的内容透明度。 */
private const val SoldOutContentAlpha = 0.45f
/** 菜名跑马灯的启动与循环延迟。 */
private const val MarqueeInitialDelayMillis = 1_200
private const val MarqueeRepeatDelayMillis = 1_600

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
    val backgroundStyle = BackgroundStyles.byIdOrDefault(backgroundStyleId)
    // 二维码：店主上传的图优先；未上传或文件已被删除时回退到内置默认二维码。
    val wechatQrModel: Any = wechatQrImagePath
        ?.let { File(context.filesDir, it) }
        ?.takeIf { it.exists() }
        ?: R.drawable.wechat_qr_default
    var qrExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // 背景优先级：自定义图 > 内置样式（未设置过背景时回退默认样式「喜庆红」）。
        if (hasBackgroundImage) {
            AsyncImage(
                model = backgroundFile,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .backgroundStyle(backgroundStyle)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = backgroundScrim))
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ContentPadding)
        ) {
            DisplayTopBar(
                storeName = storeName,
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                wechatQrModel = wechatQrModel,
                onWechatQrClick = { qrExpanded = true },
                onSelect = { selectedCategoryId = it },
                onRequestAdmin = onRequestAdmin
            )
            Spacer(modifier = Modifier.height(TopBarGap))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val spec = displaySpecFor(maxWidth, maxHeight)
                when {
                    categories.isEmpty() -> CenterHint(stringResource(R.string.display_no_categories))
                    dishes.isEmpty() -> CenterHint(stringResource(R.string.display_no_dishes))
                    else -> DishGrid(
                        dishes = dishes,
                        showStockHint = showStockHint,
                        cardAlpha = cardAlpha,
                        spec = spec
                    )
                }
            }
        }
        if (qrExpanded) {
            WechatQrOverlay(qrModel = wechatQrModel, onDismiss = { qrExpanded = false })
        }
    }
}

/**
 * 顶部条：店名做成水平居中的渐变招牌（紫罗兰渐变底 + 半透明白描边 + 投影，长按 2 秒进入管理），
 * 店名与胶囊分别限宽（0.4 / 0.3 顶栏宽），最大宽度之和不超过顶栏宽，保证不重叠。
 * 左侧为微信二维码入口（小码 + 「加微信」，未上传时用内置默认二维码），点击弹出放大浮层。
 * 始终渲染，保证分类为空时管理入口仍可达。
 */
@Composable
private fun DisplayTopBar(
    storeName: String,
    categories: List<Category>,
    selectedCategoryId: Long,
    wechatQrModel: Any,
    onWechatQrClick: () -> Unit,
    onSelect: (Long) -> Unit,
    onRequestAdmin: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val barSpec = topBarSpecFor(maxWidth)
        val storeNameMaxWidth = minOf(barSpec.storeNameMaxWidth, maxWidth * StoreNameWidthFraction)
        val chipRowMaxWidth = maxWidth * ChipRowWidthFraction
        val displayName = storeName.ifBlank { stringResource(R.string.app_name) }
        val nameTooLong = displayName.length > StoreNameLongThreshold
        val nameStyle = MaterialTheme.typography.headlineSmall.copy(
            fontSize = if (nameTooLong) barSpec.storeNameSmallSize else barSpec.storeNameSize,
            lineHeight = if (nameTooLong) barSpec.storeNameSmallLineHeight else barSpec.storeNameLineHeight
        )
        val capsuleShape = RoundedCornerShape(50)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barSpec.height)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = storeNameMaxWidth)
                    .height(barSpec.storeNameCapsuleHeight)
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
                    .padding(horizontal = barSpec.storeNameHorizontalPadding),
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
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(TopBarCorner))
                    .clickable(onClick = onWechatQrClick)
                    .padding(WechatQrEntryPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = wechatQrModel,
                    contentDescription = stringResource(R.string.display_wechat_qr_hint),
                    modifier = Modifier
                        .size(barSpec.wechatQrBadgeSize)
                        .clip(RoundedCornerShape(WechatQrBadgeCorner))
                        .background(Color.White)
                        .padding(WechatQrBadgePadding),
                    contentScale = ContentScale.Fit
                )
                Text(
                    text = stringResource(R.string.display_wechat_qr_hint),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = barSpec.wechatQrHintSize),
                    fontFamily = ChineseFontFamily,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
            if (categories.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .widthIn(max = chipRowMaxWidth)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    categories.forEach { category ->
                        CategoryChip(
                            name = category.name,
                            fontSize = barSpec.categoryChipSize,
                            selected = category.id == selectedCategoryId,
                            onClick = { onSelect(category.id) }
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun CategoryChip(
    name: String,
    fontSize: TextUnit,
    selected: Boolean,
    onClick: () -> Unit
) {
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
            style = MaterialTheme.typography.titleMedium.copy(fontSize = fontSize),
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
 * 网格：列数、卡片高度、列距行距全部取自 [DisplaySpec]；
 * 一屏目标 12 道（见 `docs/design/0013-横竖屏与多尺寸自适应.md`），超出的部分滚动。
 */
@Composable
private fun DishGrid(
    dishes: List<Dish>,
    showStockHint: Boolean,
    cardAlpha: Float,
    spec: DisplaySpec
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(spec.columns),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(spec.gridColumnSpacing),
        verticalArrangement = Arrangement.spacedBy(spec.gridRowSpacing),
        contentPadding = PaddingValues(bottom = GridBottomPadding)
    ) {
        items(dishes, key = { it.id }) { dish ->
            DishCard(
                dish = dish,
                spec = spec,
                showStockHint = showStockHint,
                cardAlpha = cardAlpha,
                modifier = Modifier.height(spec.cardHeight)
            )
        }
    }
}
@Composable
private fun PriceTag(
    priceText: String,
    unit: String,
    spec: DisplaySpec,
    modifier: Modifier = Modifier
) {
    val columnWidth = spec.priceColumnWidth
    if (columnWidth == null) {
        // 两行档：价格胶囊随内容自适应，外层约束即上限。
        PriceCapsule(priceText = priceText, unit = unit, spec = spec, modifier = modifier)
    } else {
        // 单行档：按定宽列占位，保证跨卡片左边界与状态列对齐。
        Box(modifier = modifier.width(columnWidth), contentAlignment = Alignment.CenterStart) {
            PriceCapsule(priceText = priceText, unit = unit, spec = spec)
        }
    }
}

/**
 * 价格胶囊：淡色圆角底 + 喜庆红大字，单位以小字跟随，供顾客一眼看到价格。
 * 卡片底可低至 15% 不透明度、背景可能是红色或照片，价格区靠固定 90% 白的胶囊底保证可读
 * （红字落在白底上约 6.2:1）；胶囊底不随卡片不透明度变化。见 `docs/design/0012-展示页价格强调.md`。
 * 宽度由内容决定、不低于 [DisplaySpec.priceTagMinWidth]；超长价格由省略号兜底。
 */
@Composable
private fun PriceCapsule(
    priceText: String,
    unit: String,
    spec: DisplaySpec,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .widthIn(min = spec.priceTagMinWidth)
            .clip(RoundedCornerShape(PriceTagCorner))
            .background(PriceColors.Container)
            .padding(
                horizontal = PriceTagHorizontalPadding,
                vertical = PriceTagVerticalPadding
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontSize = spec.priceTextSize)) { append(priceText) }
                if (unit.isNotBlank()) {
                    withStyle(
                        SpanStyle(
                            fontSize = spec.priceUnitSize,
                            color = PriceColors.Content.copy(alpha = PriceUnitContentAlpha)
                        )
                    ) { append(" / " + unit) }
                }
            },
            style = MaterialTheme.typography.titleMedium,
            fontFamily = ChineseFontFamily,
            color = PriceColors.Content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
@Composable
fun DishCard(
    dish: Dish,
    spec: DisplaySpec = DisplaySpec.Default,
    showStockHint: Boolean = true,
    cardAlpha: Float = 1f,
    modifier: Modifier = Modifier
) {
    val contentAlpha = if (dish.soldOut) SoldOutContentAlpha else 1f
    val baseCardColors = CardDefaults.cardColors()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardCorner),
        colors = baseCardColors.copy(
            containerColor = baseCardColors.containerColor.copy(alpha = cardAlpha)
        )
    ) {
        when (spec.cardLayout) {
            DishCardLayout.SingleRow -> SingleRowContent(dish, spec, showStockHint, contentAlpha)
            DishCardLayout.TwoRow -> TwoRowContent(dish, spec, showStockHint, contentAlpha)
        }
    }
}

/** 单行卡片内容：缩略图 / 菜名（弹性列）/ 价格列 / 状态列，均左对齐。 */
@Composable
private fun SingleRowContent(
    dish: Dish,
    spec: DisplaySpec,
    showStockHint: Boolean,
    contentAlpha: Float
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spec.cardHorizontalPadding, vertical = spec.cardVerticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DishThumbnailContainer(dish, spec, contentAlpha)
        Spacer(modifier = Modifier.width(spec.thumbnailGap))
        DishName(dish.name, contentAlpha, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(spec.columnGap))
        PriceTag(
            priceText = PriceFormatter.formatYuan(dish.priceCents),
            unit = dish.unit,
            spec = spec,
            modifier = Modifier.alpha(contentAlpha)
        )
        Spacer(modifier = Modifier.width(spec.columnGap))
        DishStatus(dish, spec, showStockHint, modifier = spec.statusColumnWidth?.let { Modifier.width(it) } ?: Modifier)
    }
}

/** 两行卡片内容：上行缩略图 + 菜名，下行价格胶囊 + 状态徽标。 */
@Composable
private fun TwoRowContent(
    dish: Dish,
    spec: DisplaySpec,
    showStockHint: Boolean,
    contentAlpha: Float
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spec.cardHorizontalPadding, vertical = spec.cardVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(spec.columnGap)
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            DishThumbnailContainer(dish, spec, contentAlpha)
            Spacer(modifier = Modifier.width(spec.thumbnailGap))
            DishName(dish.name, contentAlpha, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PriceTag(
                priceText = PriceFormatter.formatYuan(dish.priceCents),
                unit = dish.unit,
                spec = spec,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .alpha(contentAlpha)
            )
            Spacer(modifier = Modifier.width(spec.columnGap))
            DishStatus(dish, spec, showStockHint)
        }
    }
}

/** 缩略图：整块随售完灰化。 */
@Composable
private fun DishThumbnailContainer(dish: Dish, spec: DisplaySpec, contentAlpha: Float) {
    Box(modifier = Modifier.alpha(contentAlpha)) { DishThumbnail(dish, spec.thumbnailSize) }
}

/** 菜名：两档字号一致（22sp），超出列宽走跑马灯。 */
@Composable
private fun DishName(name: String, contentAlpha: Float, modifier: Modifier = Modifier) {
    Text(
        text = name,
        modifier = modifier
            .basicMarquee(
                initialDelayMillis = MarqueeInitialDelayMillis,
                repeatDelayMillis = MarqueeRepeatDelayMillis
            )
            .alpha(contentAlpha),
        style = MaterialTheme.typography.titleLarge,
        fontFamily = ChineseFontFamily,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip
    )
}

/** 行尾状态：售完徽标始终显示；有货 / 余 N 受「显示是否有货」开关控制。 */
@Composable
private fun DishStatus(
    dish: Dish,
    spec: DisplaySpec,
    showStockHint: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        if (dish.soldOut) {
            SoldOutBadge(fontSize = spec.statusTextSize)
        } else if (showStockHint) {
            val status = stockStatus(dish)
            StatusBadge(
                text = status.text,
                fontSize = spec.statusTextSize,
                containerColor = status.containerColor,
                contentColor = status.contentColor
            )
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
private fun DishThumbnail(dish: Dish, size: Dp) {
    val context = LocalContext.current
    val file = dish.imagePath?.let { File(context.filesDir, it) }
    if (file != null && file.exists()) {
        AsyncImage(
            model = file,
            contentDescription = dish.name,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(ThumbnailCorner)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(ThumbnailCorner))
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
 * 仅容器色与文字色不同；字号随展示规格（单行档 14sp、两行档 12sp）。
 */
@Composable
private fun StatusBadge(
    text: String,
    fontSize: TextUnit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(StatusBadgeCorner)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = StatusBadgeHorizontalPadding,
                vertical = StatusBadgeVerticalPadding
            ),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSize),
            fontFamily = ChineseFontFamily,
            color = contentColor,
            maxLines = 1
        )
    }
}

/** 售完徽标：红底白字，独立于「显示是否有货」开关，始终显示。 */
@Composable
private fun SoldOutBadge(fontSize: TextUnit, modifier: Modifier = Modifier) {
    StatusBadge(
        text = stringResource(R.string.display_sold_out),
        fontSize = fontSize,
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

@Preview(showBackground = true, widthDp = 320, heightDp = 130)
@Composable
private fun TwoRowDishCardPreview() {
    MaterialTheme {
        DishCard(
            dish = Dish(
                id = 3L,
                categoryId = 1L,
                name = "芝士玉米鲜肉",
                priceCents = 1400,
                unit = "份",
                stockEnabled = false,
                updatedAt = 0L
            ),
            spec = DisplaySpec.TwoRowDefault,
            modifier = Modifier.height(DisplaySpec.TwoRowDefault.cardHeight)
        )
    }
}