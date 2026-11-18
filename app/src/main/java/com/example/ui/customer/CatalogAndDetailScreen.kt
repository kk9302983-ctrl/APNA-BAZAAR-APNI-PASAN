package com.example.ui.customer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.model.CartItem
import com.example.data.model.Category
import com.example.data.model.Product
import com.example.domain.DeliveryCalculator
import com.example.ui.components.EmptyIllustrationState
import com.example.ui.components.ProductArtwork
import com.example.ui.components.ProductCard
import com.example.ui.theme.DangerContainer
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SaffronAccent

@Composable
fun CatalogScreen(
    categories: List<Category>,
    products: List<Product>,
    filter: CatalogFilterState,
    cartItems: List<CartItem>,
    onQueryChange: (String) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onToggleInStock: () -> Unit,
    onSelectSort: (ProductSortOption) -> Unit,
    onProductClick: (Product) -> Unit,
    onUpdateCart: (Product, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val cartQtyMap = cartItems.associate { it.productId to it.quantity }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search groceries, brands, categories...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (filter.query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("catalog_search_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = filter.selectedCategoryId == null,
                            onClick = { onSelectCategory(null) },
                            label = { Text("All") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldContainer,
                                selectedLabelColor = EmeraldDark
                            )
                        )
                    }
                    items(categories, key = { it.id }) { cat ->
                        FilterChip(
                            selected = filter.selectedCategoryId == cat.id,
                            onClick = {
                                onSelectCategory(if (filter.selectedCategoryId == cat.id) null else cat.id)
                            },
                            label = { Text(cat.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldContainer,
                                selectedLabelColor = EmeraldDark
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = filter.inStockOnly,
                        onClick = onToggleInStock,
                        label = { Text("In Stock Only") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldContainer,
                            selectedLabelColor = EmeraldDark
                        )
                    )

                    Box {
                        FilterChip(
                            selected = filter.sortOption != ProductSortOption.RELEVANCE,
                            onClick = { sortMenuExpanded = true },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Sort,
                                    contentDescription = "Sort products",
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            label = { Text(filter.sortOption.label) }
                        )
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            ProductSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        onSelectSort(option)
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (products.isEmpty()) {
            EmptyIllustrationState(
                title = "No matching products",
                subtitle = "Try clearing your search or switching category filters.",
                actionLabel = "Reset Filters",
                onAction = {
                    onQueryChange("")
                    onSelectCategory(null)
                    if (filter.inStockOnly) onToggleInStock()
                }
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 164.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(products, key = { it.id }) { product ->
                    ProductCard(
                        product = product,
                        quantityInCart = cartQtyMap[product.id] ?: 0L,
                        onProductClick = { onProductClick(product) },
                        onAddClick = { onUpdateCart(product, 1L) },
                        onIncrement = { onUpdateCart(product, 1L) },
                        onDecrement = { onUpdateCart(product, -1L) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    product: Product,
    relatedProducts: List<Product>,
    cartItems: List<CartItem>,
    onBack: () -> Unit,
    onAddQuantityToCart: (Product, Long) -> Unit,
    onSelectRelated: (Product) -> Unit
) {
    var selectedQty by remember(product.id) { mutableLongStateOf(1L) }
    val existingInCart = cartItems.firstOrNull { it.productId == product.id }?.quantity ?: 0L
    val cartQtyMap = cartItems.associate { it.productId to it.quantity }
    val maxCanAdd = (product.availableToSell - existingInCart).coerceAtLeast(0L)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(product.brand, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!product.outOfStock && maxCanAdd > 0L) {
                        Surface(
                            color = EmeraldContainer,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, EmeraldPrimary)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { if (selectedQty > 1L) selectedQty-- },
                                    enabled = selectedQty > 1L
                                ) {
                                    Icon(Icons.Filled.Remove, contentDescription = "Decrease")
                                }
                                Text(
                                    text = selectedQty.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )
                                IconButton(
                                    onClick = { if (selectedQty < maxCanAdd) selectedQty++ },
                                    enabled = selectedQty < maxCanAdd
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Increase")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = {
                                onAddQuantityToCart(product, selectedQty)
                                onBack()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("detail_add_to_cart")
                        ) {
                            Text(
                                text = "Add • ${DeliveryCalculator.formatRupees(product.sellingPrice * selectedQty)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White
                            )
                        }
                    } else {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(if (product.outOfStock) "Currently Out of Stock" else "Max Stock Already in Cart")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                ProductArtwork(
                    imageUrl = product.imageUrls.firstOrNull(),
                    contentDescription = product.productName,
                    modifier = Modifier.fillMaxSize()
                )
                if (product.discountPercent > 0) {
                    Surface(
                        color = SaffronAccent,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "${product.discountPercent}% OFF",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "${product.brand} • ${product.categoryName}",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = product.productName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = DeliveryCalculator.formatRupees(product.sellingPrice),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (product.originalPrice > product.sellingPrice) {
                        Text(
                            text = DeliveryCalculator.formatRupees(product.originalPrice),
                            style = MaterialTheme.typography.titleMedium.copy(
                                textDecoration = TextDecoration.LineThrough
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = product.unit,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = if (product.outOfStock) DangerContainer else EmeraldContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Inventory2,
                            contentDescription = null,
                            tint = if (product.outOfStock) DangerCoral else EmeraldDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (product.outOfStock) {
                                "Out of stock"
                            } else if (product.lowStock) {
                                "Only ${product.availableToSell} left in store!"
                            } else {
                                "In stock (${product.availableToSell} available)"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (product.outOfStock) DangerCoral else EmeraldDark
                        )
                    }
                }

                if (!product.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "About this product",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = product.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (relatedProducts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Related Products",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(relatedProducts, key = { it.id }) { rel ->
                            ProductCard(
                                product = rel,
                                quantityInCart = cartQtyMap[rel.id] ?: 0L,
                                onProductClick = { onSelectRelated(rel) },
                                onAddClick = { onAddQuantityToCart(rel, 1L) },
                                onIncrement = { onAddQuantityToCart(rel, 1L) },
                                onDecrement = { onAddQuantityToCart(rel, -1L) },
                                modifier = Modifier.width(164.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
