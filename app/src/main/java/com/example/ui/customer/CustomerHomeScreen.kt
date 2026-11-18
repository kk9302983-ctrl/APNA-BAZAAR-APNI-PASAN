package com.example.ui.customer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Address
import com.example.data.model.AppNotification
import com.example.data.model.AppSettings
import com.example.data.model.CartItem
import com.example.data.model.Category
import com.example.data.model.Product
import com.example.data.model.UiState
import com.example.domain.ServiceabilityResult
import com.example.ui.components.EmptyIllustrationState
import com.example.ui.components.ProductCard
import com.example.ui.components.categoryIconForKey
import com.example.ui.theme.DangerContainer
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SaffronAccent

@Composable
fun CustomerHomeScreen(
    activeAddress: Address?,
    serviceability: ServiceabilityResult,
    appSettings: AppSettings,
    categoriesState: UiState<List<Category>>,
    productsState: UiState<List<Product>>,
    cartItems: List<CartItem>,
    notifications: List<AppNotification>,
    onAddressClick: () -> Unit,
    onSearchClick: () -> Unit,
    onCategoryClick: (Category) -> Unit,
    onProductClick: (Product) -> Unit,
    onUpdateCart: (Product, Long) -> Unit,
    onOpenCatalog: () -> Unit,
    onNotificationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cartQtyMap = cartItems.associate { it.productId to it.quantity }
    val unreadCount = notifications.count { !it.read }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header with Address + ETA Badge + Notifications
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onAddressClick)
                                .padding(vertical = 4.dp)
                                .testTag("home_address_selector")
                        ) {
                            if (serviceability.serviceable) {
                                Surface(
                                    color = EmeraldContainer,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.Bolt,
                                            contentDescription = null,
                                            tint = EmeraldDark,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Delivery in ${serviceability.estimatedMinutes}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldDark
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.LocationOn,
                                    contentDescription = "Delivery address",
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = activeAddress?.let { "${it.label} • ${it.areaLine}" }
                                        ?: "Select delivery address",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Change address",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = onNotificationClick,
                            modifier = Modifier.testTag("notifications_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadCount > 0) {
                                        Badge { Text(unreadCount.toString()) }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Filled.Notifications,
                                    contentDescription = "Notifications",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search Bar Pill
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onSearchClick)
                            .testTag("home_search_bar"),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = "Search groceries",
                                tint = EmeraldPrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Search atta, milk, fresh veggies, snacks...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Service Unavailable Warning if outside 2-3 km or store closed
        if (!serviceability.serviceable) {
            item {
                Surface(
                    color = DangerContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable(onClick = onAddressClick)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = DangerCoral
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Delivery Unavailable",
                                style = MaterialTheme.typography.titleSmall,
                                color = DangerCoral
                            )
                            Text(
                                text = serviceability.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = DangerCoral
                            )
                        }
                    }
                }
            }
        }

        // Hero Promotional Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(164.dp)
                    .clickable(onClick = onOpenCatalog),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_groceries_1795023156941),
                        contentDescription = appSettings.storeName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xD9052E16),
                                        Color(0x99052E16),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            color = SaffronAccent,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "FRESH LOCAL STORE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "APNA BAZAAR | APNI PASAND",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = appSettings.tagline,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }

        // Categories Strip
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                SectionHeader(
                    title = "Shop by Category",
                    actionText = "View All",
                    onAction = onOpenCatalog
                )
                Spacer(modifier = Modifier.height(8.dp))
                when (categoriesState) {
                    is UiState.Loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = EmeraldPrimary) }

                    is UiState.Error -> Text(
                        text = categoriesState.message,
                        color = DangerCoral,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    is UiState.Success -> {
                        val cats = categoriesState.data
                        if (cats.isEmpty()) {
                            Text(
                                text = "Store categories are being stocked by the admin.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(cats, key = { it.id }) { category ->
                                    Column(
                                        modifier = Modifier
                                            .width(86.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { onCategoryClick(category) }
                                            .padding(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Surface(
                                            color = EmeraldContainer.copy(alpha = 0.65f),
                                            shape = CircleShape,
                                            modifier = Modifier.size(58.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = categoryIconForKey(category.iconKey),
                                                    contentDescription = category.name,
                                                    tint = EmeraldDark,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = category.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Product Curated Sections
        when (productsState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = EmeraldPrimary)
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Text(
                        text = productsState.message,
                        color = DangerCoral,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            is UiState.Success -> {
                val all = productsState.data
                if (all.isEmpty()) {
                    item {
                        EmptyIllustrationState(
                            title = "Fresh Catalog Coming Soon",
                            subtitle = "Our local store admin hasn't published inventory items yet. Check back shortly!",
                            actionLabel = "Browse Categories",
                            onAction = onOpenCatalog
                        )
                    }
                } else {
                    val bestSellers = all.filter { it.bestSeller }.ifEmpty { all.take(6) }
                    val dailyOffers = all.filter { it.dailyOffer || it.discountPercent >= 10 }.ifEmpty { all.take(6) }
                    val recommended = all.filter { it.recommended }.ifEmpty { all.take(6) }
                    val popularFeatured = all.filter { it.popular || it.featured }.ifEmpty { all.take(6) }

                    item {
                        ProductHorizontalSection(
                            title = "Best Sellers",
                            products = bestSellers,
                            cartQtyMap = cartQtyMap,
                            onSeeAll = onOpenCatalog,
                            onProductClick = onProductClick,
                            onUpdateCart = onUpdateCart
                        )
                    }
                    item {
                        ProductHorizontalSection(
                            title = "Daily Offers",
                            products = dailyOffers,
                            cartQtyMap = cartQtyMap,
                            onSeeAll = onOpenCatalog,
                            onProductClick = onProductClick,
                            onUpdateCart = onUpdateCart
                        )
                    }
                    item {
                        ProductHorizontalSection(
                            title = "Recommended For You",
                            products = recommended,
                            cartQtyMap = cartQtyMap,
                            onSeeAll = onOpenCatalog,
                            onProductClick = onProductClick,
                            onUpdateCart = onUpdateCart
                        )
                    }
                    item {
                        ProductHorizontalSection(
                            title = "Popular & Recently Added",
                            products = popularFeatured,
                            cartQtyMap = cartQtyMap,
                            onSeeAll = onOpenCatalog,
                            onProductClick = onProductClick,
                            onUpdateCart = onUpdateCart
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionText: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = actionText,
            style = MaterialTheme.typography.labelLarge,
            color = EmeraldPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onAction)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ProductHorizontalSection(
    title: String,
    products: List<Product>,
    cartQtyMap: Map<String, Long>,
    onSeeAll: () -> Unit,
    onProductClick: (Product) -> Unit,
    onUpdateCart: (Product, Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = title, actionText = "See all", onAction = onSeeAll)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(products, key = { "${title}_${it.id}" }) { product ->
                ProductCard(
                    product = product,
                    quantityInCart = cartQtyMap[product.id] ?: 0L,
                    onProductClick = { onProductClick(product) },
                    onAddClick = { onUpdateCart(product, 1L) },
                    onIncrement = { onUpdateCart(product, 1L) },
                    onDecrement = { onUpdateCart(product, -1L) },
                    modifier = Modifier.width(168.dp)
                )
            }
        }
    }
}
