package com.tanpai.menu.ui.display

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tanpai.menu.R
import com.tanpai.menu.data.db.Category
import com.tanpai.menu.data.db.Dish
import com.tanpai.menu.domain.PriceFormatter
import com.tanpai.menu.kiosk.KeepScreenOn
import java.io.File
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun DisplayScreen(
    viewModel: DisplayViewModel,
    storeName: String,
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
                .width(240.dp)
                .fillMaxHeight()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when {
                categories.isEmpty() -> CenterHint(stringResource(R.string.display_no_categories))
                dishes.isEmpty() -> CenterHint(stringResource(R.string.display_no_dishes))
                else -> DishGrid(dishes)
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
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Text(
                    text = storeName.ifBlank { stringResource(R.string.app_name) },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.display_long_press_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories, key = { it.id }) { category ->
                    val selected = category.id == selectedCategoryId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .clickable { onSelect(category.id) },
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = category.name,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
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

@Composable
private fun DishGrid(dishes: List<Dish>) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 260.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(dishes, key = { it.id }) { dish ->
            DishCard(dish = dish)
        }
    }
}

@Composable
fun DishCard(dish: Dish, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (dish.soldOut) 0.45f else 1f)
            ) {
                DishImage(dish)
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = dish.name,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${PriceFormatter.formatYuan(dish.priceCents)} / ${dish.unit}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (dish.stockEnabled) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.display_stock_remaining, dish.stockCount),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (dish.soldOut) {
                SoldOutStamp(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun DishImage(dish: Dish) {
    val context = LocalContext.current
    val file = dish.imagePath?.let { File(context.filesDir, it) }
    if (file != null && file.exists()) {
        AsyncImage(
            model = file,
            contentDescription = dish.name,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dish.name.take(1),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun SoldOutStamp(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.error,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = stringResource(R.string.display_sold_out),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onError
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

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun DishCardPreview() {
    MaterialTheme {
        DishCard(
            dish = Dish(
                id = 1L,
                categoryId = 1L,
                name = "烤串",
                priceCents = 300,
                unit = "串",
                stockEnabled = true,
                stockCount = 18,
                updatedAt = 0L
            )
        )
    }
}
