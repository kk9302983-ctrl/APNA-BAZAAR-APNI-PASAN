package com.example.ui.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.Category
import com.example.data.model.InventoryLog
import com.example.data.model.Product
import com.example.domain.DeliveryCalculator
import com.example.ui.components.ProductArtwork
import com.example.ui.theme.DangerContainer
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary

@Composable
fun AdminCatalogTab(
    categories: List<Category>,
    products: List<Product>,
    onSaveCategory: (Category) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onSaveProduct: (Product) -> Unit
) {
    var subTab by remember { mutableIntStateOf(0) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subTab) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("Products (${products.size})") })
            Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Categories (${categories.size})") })
        }

        if (subTab == 0) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Button(
                        onClick = {
                            val firstCat = categories.firstOrNull()
                            editingProduct = Product(
                                categoryId = firstCat?.id ?: "cat_grocery",
                                categoryName = firstCat?.name ?: "Grocery & Ration",
                                imageUrls = listOf("drawable://img_cat_pantry"),
                                sellingPrice = 99.0,
                                originalPrice = 120.0,
                                unit = "1 kg",
                                stockQuantity = 20L
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add New Product", color = Color.White)
                    }
                }
                items(products, key = { it.id }) { prod ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProductArtwork(
                                imageUrl = prod.imageUrls.firstOrNull(),
                                contentDescription = prod.productName,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(prod.productName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${prod.unit} • ${DeliveryCalculator.formatRupees(prod.sellingPrice)} (MRP ${DeliveryCalculator.formatRupees(prod.originalPrice)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (prod.active) "Active • Stock: ${prod.availableToSell}" else "Hidden from storefront",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (prod.active) EmeraldDark else DangerCoral
                                )
                            }
                            IconButton(onClick = { editingProduct = prod }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit product")
                            }
                            Switch(
                                checked = prod.active,
                                onCheckedChange = { onSaveProduct(prod.copy(active = it)) }
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Button(
                        onClick = { editingCategory = Category(sortOrder = (categories.size + 1L)) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Category", color = Color.White)
                    }
                }
                items(categories, key = { it.id }) { cat ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cat.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Order #${cat.sortOrder} • ${if (cat.active) "Visible" else "Hidden"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { editingCategory = cat }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit category")
                            }
                            Switch(
                                checked = cat.active,
                                onCheckedChange = { onSaveCategory(cat.copy(active = it)) }
                            )
                            IconButton(onClick = { onDeleteCategory(cat.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete category", tint = DangerCoral)
                            }
                        }
                    }
                }
            }
        }
    }

    editingCategory?.let { cat ->
        CategoryEditorDialog(
            initial = cat,
            onDismiss = { editingCategory = null },
            onSave = {
                onSaveCategory(it)
                editingCategory = null
            }
        )
    }

    editingProduct?.let { prod ->
        ProductEditorDialog(
            initial = prod,
            categories = categories,
            onDismiss = { editingProduct = null },
            onSave = {
                onSaveProduct(it)
                editingProduct = null
            }
        )
    }
}

@Composable
private fun CategoryEditorDialog(
    initial: Category,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var subtitle by remember { mutableStateOf(initial.subtitle ?: "") }
    var iconKey by remember { mutableStateOf(initial.iconKey) }
    var sortOrder by remember { mutableStateOf(initial.sortOrder.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "New Category" else "Edit Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Category Name") }, singleLine = true)
                OutlinedTextField(value = subtitle, onValueChange = { subtitle = it }, label = { Text("Subtitle") }, singleLine = true)
                OutlinedTextField(value = iconKey, onValueChange = { iconKey = it }, label = { Text("Icon Key (grocery, produce, dairy, snacks...)") }, singleLine = true)
                OutlinedTextField(value = sortOrder, onValueChange = { sortOrder = it }, label = { Text("Sort Order") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            subtitle = subtitle.trim().ifBlank { null },
                            iconKey = iconKey.trim().ifBlank { "grocery" },
                            sortOrder = sortOrder.toLongOrNull() ?: 1L
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductEditorDialog(
    initial: Product,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit
) {
    var name by remember { mutableStateOf(initial.productName) }
    var brand by remember { mutableStateOf(initial.brand) }
    var desc by remember { mutableStateOf(initial.description ?: "") }
    var unit by remember { mutableStateOf(initial.unit) }
    var sell by remember { mutableStateOf(initial.sellingPrice.toString()) }
    var mrp by remember { mutableStateOf(initial.originalPrice.toString()) }
    var stock by remember { mutableStateOf(initial.stockQuantity.toString()) }
    var threshold by remember { mutableStateOf(initial.lowStockThreshold.toString()) }
    var imageUrl by remember { mutableStateOf(initial.imageUrls.firstOrNull() ?: "drawable://img_cat_pantry") }
    var selectedCatId by remember { mutableStateOf(initial.categoryId) }
    var selectedCatName by remember { mutableStateOf(initial.categoryName) }
    var catMenuExpanded by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) imageUrl = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "Add Product" else "Edit Product") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Product Name") }, singleLine = true)
                OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("Brand") }, singleLine = true)

                if (categories.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = catMenuExpanded,
                        onExpandedChange = { catMenuExpanded = !catMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCatName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catMenuExpanded) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = catMenuExpanded,
                            onDismissRequest = { catMenuExpanded = false }
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        selectedCatId = cat.id
                                        selectedCatName = cat.name
                                        catMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Weight / Unit (e.g. 1 kg)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sell, onValueChange = { sell = it }, label = { Text("Selling ₹") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = mrp, onValueChange = { mrp = it }, label = { Text("MRP ₹") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = stock, onValueChange = { stock = it }, label = { Text("Stock Qty") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = threshold, onValueChange = { threshold = it }, label = { Text("Low Alert") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, maxLines = 3)
                OutlinedTextField(value = imageUrl, onValueChange = { imageUrl = it }, label = { Text("Image URI / Drawable Key") }, singleLine = true)
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick Product Image (Photo Picker)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sellVal = sell.toDoubleOrNull() ?: 1.0
                    val mrpVal = maxOf(mrp.toDoubleOrNull() ?: sellVal, sellVal)
                    val stockVal = stock.toLongOrNull() ?: 0L
                    onSave(
                        initial.copy(
                            productName = name.trim(),
                            brand = brand.trim().ifBlank { "Apna Bazaar" },
                            description = desc.trim().ifBlank { null },
                            categoryId = selectedCatId,
                            categoryName = selectedCatName,
                            unit = unit.trim().ifBlank { "1 unit" },
                            sellingPrice = sellVal,
                            originalPrice = mrpVal,
                            stockQuantity = stockVal,
                            lowStockThreshold = threshold.toLongOrNull() ?: 5L,
                            imageUrls = listOf(imageUrl.trim()).filter { it.isNotBlank() },
                            available = (stockVal - initial.reservedQuantity) > 0L
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AdminInventoryTab(
    products: List<Product>,
    inventoryLogs: List<InventoryLog>,
    onAdjustStock: (Product, Long, String) -> Unit
) {
    var subTab by remember { mutableIntStateOf(0) }
    var filterMode by remember { mutableStateOf("All") }
    var adjustingProduct by remember { mutableStateOf<Product?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subTab) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("Stock Levels") })
            Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Movement Logs (${inventoryLogs.size})") })
        }

        if (subTab == 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Low Stock", "Out of Stock").forEach { mode ->
                    FilterChip(
                        selected = filterMode == mode,
                        onClick = { filterMode = mode },
                        label = { Text(mode) }
                    )
                }
            }
            val list = products.filter { p ->
                when (filterMode) {
                    "Low Stock" -> p.lowStock
                    "Out of Stock" -> p.outOfStock
                    else -> true
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(list, key = { it.id }) { prod ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            1.dp,
                            if (prod.outOfStock || prod.lowStock) DangerCoral else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Inventory,
                                contentDescription = null,
                                tint = if (prod.outOfStock) DangerCoral else EmeraldPrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(prod.productName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Available: ${prod.availableToSell} • Reserved: ${prod.reservedQuantity} • Sold: ${prod.soldQuantity}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (prod.outOfStock || prod.lowStock) {
                                    Surface(
                                        color = DangerContainer,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Text(
                                            text = if (prod.outOfStock) "OUT OF STOCK" else "LOW STOCK ALERT",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = DangerCoral,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            OutlinedButton(onClick = { adjustingProduct = prod }) {
                                Text("Adjust")
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(inventoryLogs, key = { it.id }) { log ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${log.productName} (${log.changeType})", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = if (log.quantityDelta >= 0) "+${log.quantityDelta}" else "${log.quantityDelta}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (log.quantityDelta >= 0) EmeraldDark else DangerCoral
                                )
                            }
                            Text(
                                text = "Stock: ${log.previousStock} → ${log.newStock} • ${log.reason}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "By: ${log.adminEmail}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    adjustingProduct?.let { prod ->
        var deltaText by remember { mutableStateOf("10") }
        var reason by remember { mutableStateOf("Store restock") }
        AlertDialog(
            onDismissRequest = { adjustingProduct = null },
            title = { Text("Adjust Stock: ${prod.productName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current Total Stock: ${prod.stockQuantity} (Reserved: ${prod.reservedQuantity})")
                    OutlinedTextField(
                        value = deltaText,
                        onValueChange = { deltaText = it },
                        label = { Text("Delta (+/- quantity, e.g. 20 or -5)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Audit Reason") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val delta = deltaText.toLongOrNull() ?: 0L
                        onAdjustStock(prod, delta, reason)
                        adjustingProduct = null
                    },
                    enabled = (deltaText.toLongOrNull() ?: 0L) != 0L && reason.isNotBlank()
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { adjustingProduct = null }) { Text("Cancel") } }
        )
    }
}
