package com.tanpai.menu.ui.display

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
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
import java.io.File
import kotlinx.coroutines.withTimeoutOrNull

/** 侧栏宽度：只放店名与分类，把宽度让给菜品区。 */
private val SidebarWidth = 150.dp

private val ContentPadding = 12.dp
private val CardSpacing = 10.dp
private val CardMaxHeight = 96.dp
private val CardMinHeight = 72.dp
private val ThumbnailSize = 56.dp

/** 卡片内横向内边距。 */
private val CardHorizontalPadding = 10.dp
/** 缩略图与文字之间的间距。 */
private val ThumbnailGap = 8.dp
/** 菜名 / 价格 / 状态三列之间的间距。 */
private val ColumnGap = 5.dp
/** 价格列定宽，保证跨卡片左边界一致。 */
private val PriceColumnWidth = 100.dp
/** 状态列定宽（有货 / 余 N / 已售完徽标共用）。 */
private val StatusColumnWidth = 60.dp

@Composable
fun DisplayScreen(
    viewModel: DisplayViewModel,
    storeName: String,
    showStockHint: Boolean,
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

    Row(modifier = modifier.fillMaxSize()) {
        CategorySidebar(
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            onSelect = { selectedCategoryId = it },
            storeName = storeName,
            onRequestAdmin = onRequestAdmin,
            modifier = Modifier
                .width(SidebarWidth)
                .fillMaxHeight()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(ContentPadding)
        ) {
            when {
                categories.isEmpty() -> CenterHint(stringResource(R.string.display_no_categories))
                dishes.isEmpty() -> CenterHint(stringResource(R.string.display_no_dishes))
                else -> DishGrid(dishes = dishes, showStockHint = showStockHint)
            }
        }
    }
}

@Composable
private fun CategorySidebar(
    categories: List<Category>,
    selectedCategoryId: Long,
    onSelect: (Long) -> Unit,
    storeName: String,
    onRequestAdmin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                val released = withTimeoutOrNull(5_000L) { awaitRelease() }
                                if (released == null) onRequestAdmin()
                            }
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 16.dp)
            ) {
                Text(
                    text = storeName.ifBlank { stringResource(R.string.app_name) },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 26.sp,
                        lineHeight = 32.sp
                    ),
                    fontFamily = ChineseFontFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.display_long_press_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(categories, key = { it.id }) { category ->
                    val selected = category.id == selectedCategoryId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .clickable { onSelect(category.id) },
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = category.name,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = ChineseFontFamily,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 2 列网格；行高按可用高度摊平，使 12 道菜正好铺满一屏，超过 12 道则滚动。
 * 行高限制在 [CardMinHeight] 与 [CardMaxHeight] 之间，避免菜品少时被拉得过大。
 */
@Composable
private fun DishGrid(dishes: List<Dish>, showStockHint: Boolean) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val rows = ((dishes.size + 1) / 2).coerceAtLeast(1)
        val rowHeight = ((maxHeight - CardSpacing * (rows - 1)) / rows)
            .coerceIn(CardMinHeight, CardMaxHeight)

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(CardSpacing),
            verticalArrangement = Arrangement.spacedBy(CardSpacing),
            contentPadding = PaddingValues(bottom = 4.dp)
        ) {
            items(dishes, key = { it.id }) { dish ->
                DishCard(
                    dish = dish,
                    showStockHint = showStockHint,
                    modifier = Modifier.height(rowHeight)
                )
            }
        }
    }
}

@Composable
fun DishCard(dish: Dish, showStockHint: Boolean = true, modifier: Modifier = Modifier) {
    val contentAlpha = if (dish.soldOut) 0.45f else 1f
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = CardHorizontalPadding, vertical = 8.dp),
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
                    Text(
                        text = status.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = ChineseFontFamily,
                        color = status.color,
                        maxLines = 1
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
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    else -> StockStatus(
        text = stringResource(R.string.display_in_stock),
        color = MaterialTheme.colorScheme.tertiary
    )
}

private data class StockStatus(val text: String, val color: Color)

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

/** 售完徽标：置于卡片行尾，不覆盖菜名与价格。 */
@Composable
private fun SoldOutBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.error,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = stringResource(R.string.display_sold_out),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = ChineseFontFamily,
            color = MaterialTheme.colorScheme.onError,
            maxLines = 1
        )
    }
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
