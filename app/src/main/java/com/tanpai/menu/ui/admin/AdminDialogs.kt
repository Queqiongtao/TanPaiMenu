package com.tanpai.menu.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tanpai.menu.R
import com.tanpai.menu.data.db.Category
import com.tanpai.menu.data.db.Dish
import kotlin.math.roundToInt

@Composable
fun CategoryEditDialog(
    initial: Category?,
    onConfirm: (name: String, sortOrder: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var sortOrder by remember { mutableStateOf((initial?.sortOrder ?: 0).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.admin_add_category else R.string.admin_edit)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.admin_category_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sortOrder,
                    onValueChange = { sortOrder = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.admin_sort_order)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onConfirm(name.trim(), sortOrder.toIntOrNull() ?: 0)
                    onDismiss()
                }
            ) { Text(stringResource(R.string.admin_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.admin_cancel)) }
        }
    )
}

@Composable
fun DishEditDialog(
    initial: Dish?,
    categoryId: Long,
    onConfirm: (Dish) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var price by remember { mutableStateOf(initial?.let { it.priceCents / 100.0 }?.toString() ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "") }
    var stockEnabled by remember { mutableStateOf(initial?.stockEnabled ?: false) }
    var stockCount by remember { mutableStateOf((initial?.stockCount ?: 0).toString()) }
    var sortOrder by remember { mutableStateOf((initial?.sortOrder ?: 0).toString()) }
    var description by remember { mutableStateOf(initial?.description ?: "") }

    val priceCents = price.toDoubleOrNull()?.let { (it * 100).roundToInt() } ?: 0
    val defaultUnit = stringResource(R.string.dish_default_unit)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.admin_add_dish else R.string.admin_edit)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.admin_dish_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.admin_price_yuan)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text(stringResource(R.string.admin_unit)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.admin_stock_enabled))
                    Switch(checked = stockEnabled, onCheckedChange = { stockEnabled = it })
                }
                OutlinedTextField(
                    value = stockCount,
                    onValueChange = { stockCount = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.admin_stock_count)) },
                    enabled = stockEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = sortOrder,
                    onValueChange = { sortOrder = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.admin_sort_order)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.admin_description)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onConfirm(
                        Dish(
                            id = initial?.id ?: 0L,
                            categoryId = initial?.categoryId ?: categoryId,
                            name = name.trim(),
                            priceCents = priceCents,
                            unit = unit.trim().ifBlank { defaultUnit },
                            imagePath = initial?.imagePath,
                            stockEnabled = stockEnabled,
                            stockCount = stockCount.toIntOrNull() ?: 0,
                            soldOut = initial?.soldOut ?: false,
                            description = description.trim(),
                            sortOrder = sortOrder.toIntOrNull() ?: 0,
                            enabled = initial?.enabled ?: true,
                            updatedAt = initial?.updatedAt ?: 0L
                        )
                    )
                    onDismiss()
                }
            ) { Text(stringResource(R.string.admin_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.admin_cancel)) }
        }
    )
}

@Composable
fun ConfirmDialog(
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.admin_delete)) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                }
            ) { Text(stringResource(R.string.admin_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.admin_cancel)) }
        }
    )
}
