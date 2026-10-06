package com.tanpai.menu.ui.display

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import com.tanpai.menu.ui.theme.ChineseFontFamily
import com.tanpai.menu.ui.theme.StatusColors
import java.io.File
import kotlinx.coroutines.withTimeoutOrNull

/** 顶部条高度：店名与分类胶囊共用，高度固定不随内容伸缩。 */
private val TopBarHeight = 44.dp
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

private val ContentPadding = 12.dp
/** 卡片固定高度：56dp 缩略图 + 上下各 6dp 内边距。 */
private val CardHeight = 68.dp
/** 卡片行距（行高变矮后放大行距，让 12 道仍铺满一屏）。 */
private val CardSpacing = 16.dp
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
    backgroundScrim: Float = 0.3f,
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

    Box(modifier = modifier.fillMaxSize()) {
        if (backgroundFile != null && backgroundFile.exists()) {
            AsyncImage(
                model = backgroundFile,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
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
                onSelect = { selectedCategoryId = it },
                onRequestAdmin = onRequestAdmin
            )
            Spacer(modifier = Modifier.height(CardSpacing))
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
    }
}

/**
 * 顶部条：店名做成水平居中的淡紫圆角胶囊（长按 2 秒进入管理），分类胶囊靠右（仅多分类时出现）。
 * 店名与胶囊分别限宽（0.4 / 0.3 顶栏宽），最大宽度之和不超过顶栏宽，保证不重叠。
 * 始终渲染，保证分类为空时管理入口仍可达。
 */
@Composable
private fun DisplayTopBar(
    storeName: String,
    categories: List<Category>,
    selectedCategoryId: Long,
    onSelect: (Long) -> Unit,
    onRequestAdmin: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(TopBarHeight)
    ) {
        val storeNameMaxWidth = minOf(StoreNameMaxWidth, maxWidth * StoreNameWidthFraction)
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = storeNameMaxWidth)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            val released = withTimeoutOrNull(AdminLongPressMillis) { awaitRelease() }
                            if (released == null) onRequestAdmin()
                        }
                    )
                },
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = storeName.ifBlank { stringResource(R.string.app_name) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 28.sp,
                    lineHeight = 34.sp
                ),
                fontFamily = ChineseFontFamily,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
 * 2 列网格：卡片固定 [CardHeight] 高、行距 [CardSpacing]，12 道一屏铺满，超过 12 道则滚动。
 */
@Composable
private fun DishGrid(dishes: List<Dish>, showStockHint: Boolean, cardAlpha: Float) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(CardSpacing),
        verticalArrangement = Arrangement.spacedBy(CardSpacing),
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
