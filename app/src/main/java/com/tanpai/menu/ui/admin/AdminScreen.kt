package com.tanpai.menu.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tanpai.menu.R
import com.tanpai.menu.data.db.Category
import com.tanpai.menu.data.db.Dish
import com.tanpai.menu.domain.PriceFormatter
import com.tanpai.menu.domain.SellError
import com.tanpai.menu.domain.SellResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(viewModel: AdminViewModel, onExit: () -> Unit) {
    val menu by viewModel.menu.collectAsState()
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
            text = stringResource(R.string.admin_delete_category_confirm, category.name),
            onConfirm = { viewModel.deleteCategory(category.id) },
            onDismiss = { deletingCategory = null }
        )
    }
    if (showNewDish && selectedCategory != null) {
        DishEditDialog(
            initial = null,
            categoryId = selectedCategory.id,
            onConfirm = { viewModel.createDish(it) },
            onDismiss = { showNewDish = false }
        )
    }
    editingDish?.let { dish ->
        DishEditDialog(
            initial = dish,
            categoryId = dish.categoryId,
            onConfirm = { viewModel.updateDish(it) },
            onDismiss = { editingDish = null }
        )
    }
    deletingDish?.let { dish ->
        ConfirmDialog(
            text = stringResource(R.string.admin_delete_dish_confirm, dish.name),
            onConfirm = { viewModel.deleteDish(dish.id) },
            onDismiss = { deletingDish = null }
        )
    }
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
            Button(onClick = onSell, enabled = !dish.soldOut) {
                Text(stringResource(R.string.admin_sell))
            }
            TextButton(onClick = onUnsell) {
                Text(stringResource(R.string.admin_unsell))
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
