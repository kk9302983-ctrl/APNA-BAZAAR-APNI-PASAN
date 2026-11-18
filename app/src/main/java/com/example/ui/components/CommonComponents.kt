package com.example.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BabyChangingStation
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.OrderStatus
import com.example.data.model.Product
import com.example.domain.DeliveryCalculator
import com.example.ui.theme.DangerContainer
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.OnEmeraldContainer
import com.example.ui.theme.OnSaffronContainer
import com.example.ui.theme.SaffronAccent
import com.example.ui.theme.SaffronContainer

@Composable
fun ProductArtwork(
    imageUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val bundledRes = when (imageUrl) {
        "drawable://img_hero_groceries" -> R.drawable.img_hero_groceries_1795023156941
        "drawable://img_cat_pantry" -> R.drawable.img_cat_pantry_1795023173991
        "drawable://img_cat_dairy_snacks" -> R.drawable.img_cat_dairy_snacks_1795023174673
        "drawable://img_delivery_doorstep" -> R.drawable.img_delivery_doorstep_1795023157296
        else -> null
    }
    when {
        bundledRes != null -> {
            Image(
                painter = painterResource(id = bundledRes),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        }
        !imageUrl.isNullOrBlank() -> {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                error = painterResource(id = R.drawable.img_cat_pantry_1795023173991),
                placeholder = painterResource(id = R.drawable.img_cat_pantry_1795023173991)
            )
        }
        else -> {
            Image(
                painter = painterResource(id = R.drawable.img_cat_pantry_1795023173991),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        }
    }
}

fun categoryIconForKey(iconKey: String): ImageVector = when (iconKey.lowercase()) {
    "grocery" -> Icons.Filled.LocalGroceryStore
    "produce" -> Icons.Filled.Eco
    "dairy" -> Icons.Filled.BakeryDining
    "snacks" -> Icons.Filled.Fastfood
    "personal" -> Icons.Filled.Spa
    "cleaning" -> Icons.Filled.CleaningServices
    "kitchen" -> Icons.Filled.Kitchen
    "baby" -> Icons.Filled.BabyChangingStation
    "health" -> Icons.Filled.MedicalServices
    "frozen" -> Icons.Filled.ShoppingBag
    else -> Icons.Filled.LocalGroceryStore
}

@Composable
fun ProductCard(
    product: Product,
    quantityInCart: Long,
    onProductClick: () -> Unit,
    onAddClick: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOut = product.outOfStock
    Card(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onProductClick)
            .testTag("product_card_${product.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
                    .clip(MaterialTheme.shapes.small)
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
                        shape = RoundedCornerShape(bottomEnd = 10.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = "${product.discountPercent}% OFF",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (isOut) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = DangerContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Out of Stock",
                                style = MaterialTheme.typography.labelSmall,
                                color = DangerCoral,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = product.brand,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = product.productName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(40.dp)
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = product.unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = DeliveryCalculator.formatRupees(product.sellingPrice),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (product.originalPrice > product.sellingPrice) {
                        Text(
                            text = DeliveryCalculator.formatRupees(product.originalPrice),
                            style = MaterialTheme.typography.bodySmall.copy(
                                textDecoration = TextDecoration.LineThrough
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isOut) {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Sold Out", style = MaterialTheme.typography.labelSmall)
                    }
                } else if (quantityInCart <= 0L) {
                    OutlinedButton(
                        onClick = onAddClick,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.5.dp, EmeraldPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = EmeraldContainer.copy(alpha = 0.45f),
                            contentColor = EmeraldDark
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("add_to_cart_${product.id}")
                    ) {
                        Text("ADD", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Surface(
                        color = EmeraldPrimary,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = onDecrement,
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("decrement_${product.id}")
                            ) {
                                Icon(
                                    Icons.Filled.Remove,
                                    contentDescription = "Decrease quantity",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = quantityInCart.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                            IconButton(
                                onClick = onIncrement,
                                enabled = quantityInCart < product.availableToSell,
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("increment_${product.id}")
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = "Increase quantity",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OrderStatusChip(status: String, modifier: Modifier = Modifier) {
    val (bg, fg) = when (OrderStatus.fromLabel(status)) {
        OrderStatus.DELIVERED -> EmeraldContainer to OnEmeraldContainer
        OrderStatus.CANCELLED -> DangerContainer to DangerCoral
        OrderStatus.PENDING -> SaffronContainer to OnSaffronContainer
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun EmptyIllustrationState(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_delivery_doorstep_1795023157296),
            contentDescription = title,
            modifier = Modifier
                .size(180.dp)
                .clip(MaterialTheme.shapes.large),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(actionLabel)
            }
        }
    }
}
