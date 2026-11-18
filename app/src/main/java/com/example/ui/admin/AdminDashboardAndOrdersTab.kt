package com.example.ui.admin

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.OrderStatusHistory
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentStatus
import com.example.data.model.Product
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.domain.DeliveryCalculator
import com.example.ui.components.OrderStatusChip
import com.example.ui.theme.DangerContainer
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SaffronContainer

@Composable
fun AdminDashboardTab(
    metrics: AdminDashboardMetrics,
    products: List<Product>,
    isBusy: Boolean,
    onSeedCatalog: () -> Unit,
    onOpenOrders: () -> Unit,
    onOpenInventory: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (products.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = EmeraldContainer),
                    border = BorderStroke(1.5.dp, EmeraldPrimary)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = EmeraldDark)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Initialize Store Catalog", style = MaterialTheme.typography.titleMedium, color = EmeraldDark)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Seed the 10 Apna Bazaar categories, starter grocery products, 2.5 km delivery zone, and APNA50 coupon in one tap.",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldDark
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onSeedCatalog,
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            modifier = Modifier.testTag("seed_catalog_button")
                        ) {
                            Text(if (isBusy) "Initializing..." else "Initialize Store Catalog", color = Color.White)
                        }
                    }
                }
            }
        }

        item {
            Text("Store Performance", style = MaterialTheme.typography.headlineSmall)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricTile("Today's Sales", DeliveryCalculator.formatRupees(metrics.todaySales), EmeraldContainer, EmeraldDark, Modifier.weight(1f))
                MetricTile("Total Sales", DeliveryCalculator.formatRupees(metrics.totalSales), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricTile("Today's Orders", metrics.todayOrders.toString(), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                MetricTile("Total Orders", metrics.totalOrders.toString(), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricTile(
                    "Pending Orders",
                    metrics.pendingOrders.toString(),
                    SaffronContainer,
                    Color(0xFF92400E),
                    Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenOrders)
                )
                MetricTile("Completed", metrics.completedOrders.toString(), EmeraldContainer, EmeraldDark, Modifier.weight(1f))
                MetricTile("Cancelled", metrics.cancelledOrders.toString(), DangerContainer, DangerCoral, Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricTile("Customers", metrics.totalCustomers.toString(), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                MetricTile("Active Products", metrics.activeProducts.toString(), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                MetricTile(
                    "Low Stock",
                    metrics.lowStockProducts.toString(),
                    if (metrics.lowStockProducts > 0) DangerContainer else MaterialTheme.colorScheme.surface,
                    if (metrics.lowStockProducts > 0) DangerCoral else MaterialTheme.colorScheme.onSurface,
                    Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenInventory)
                )
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    bgColor: Color,
    fgColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = fgColor.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = fgColor)
        }
    }
}

@Composable
fun AdminOrdersTab(
    ordersState: UiState<List<Order>>,
    orderHistory: List<OrderStatusHistory>,
    onConfirmOrder: (Order) -> Unit,
    onAdvanceStatus: (Order, OrderStatus, String, Boolean?) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("All") }
    var detailOrder by remember { mutableStateOf<Order?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search by Order ID or Customer...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            val filters = listOf("All") + OrderStatus.entries.map { it.label }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters) { f ->
                    FilterChip(
                        selected = statusFilter == f,
                        onClick = { statusFilter = f },
                        label = { Text(f) }
                    )
                }
            }
        }

        when (ordersState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EmeraldPrimary)
            }
            is UiState.Error -> Text(ordersState.message, color = DangerCoral, modifier = Modifier.padding(16.dp))
            is UiState.Success -> {
                val filtered = ordersState.data.filter { o ->
                    val matchStatus = statusFilter == "All" || o.orderStatus == statusFilter
                    val q = search.trim()
                    val matchQ = q.isEmpty() ||
                        o.orderNumber.contains(q, ignoreCase = true) ||
                        o.customerName.contains(q, ignoreCase = true) ||
                        o.customerPhone.contains(q, ignoreCase = true)
                    matchStatus && matchQ
                }
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No orders match this filter.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filtered, key = { it.id }) { order ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { detailOrder = order },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${order.orderNumber} • ${order.customerName}", style = MaterialTheme.typography.titleSmall)
                                        OrderStatusChip(order.orderStatus)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${order.customerPhone} • ${DeliveryCalculator.formatRupees(order.grandTotal)} (${order.paymentMethod}: ${order.paymentStatus})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (order.orderStatus == OrderStatus.PENDING.label) {
                                            Button(
                                                onClick = { onConfirmOrder(order) },
                                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                                            ) {
                                                Text("Accept & Reserve", color = Color.White)
                                            }
                                            OutlinedButton(
                                                onClick = { onAdvanceStatus(order, OrderStatus.CANCELLED, "Rejected by store", null) }
                                            ) {
                                                Text("Reject", color = DangerCoral)
                                            }
                                        } else {
                                            val next = nextStatusFor(order.orderStatus)
                                            if (next != null) {
                                                Button(
                                                    onClick = { onAdvanceStatus(order, next, "Moved to ${next.label}", null) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                                                ) {
                                                    Text("Mark ${next.label}", color = Color.White)
                                                }
                                            }
                                        }
                                        OutlinedButton(onClick = { detailOrder = order }) {
                                            Text("Inspect")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    detailOrder?.let { selected ->
        val latest = (ordersState as? UiState.Success)?.data?.firstOrNull { it.id == selected.id } ?: selected
        val history = orderHistory.filter { it.orderId == latest.id }
        var statusNote by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { detailOrder = null },
            title = { Text("${latest.orderNumber} (${latest.orderStatus})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${latest.customerName} • ${latest.customerPhone}", style = MaterialTheme.typography.titleSmall)
                    Text("${latest.addressSnapshot.houseLine}, ${latest.addressSnapshot.areaLine} (${latest.addressSnapshot.pincode})", style = MaterialTheme.typography.bodySmall)
                    if (!latest.deliveryInstructions.isNullOrBlank()) {
                        Text("Note: ${latest.deliveryInstructions}", style = MaterialTheme.typography.bodySmall, color = EmeraldDark)
                    }
                    HorizontalDivider()
                    latest.orderItemsSnapshot.forEach { item ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${item.quantity}x ${item.productName}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(DeliveryCalculator.formatRupees(item.lineTotal), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider()
                    Text("Total: ${DeliveryCalculator.formatRupees(latest.grandTotal)} • ${PaymentMethod.fromCode(latest.paymentMethod).displayName} (${latest.paymentStatus})", style = MaterialTheme.typography.titleSmall)
                    if (latest.paymentStatus != PaymentStatus.PAID.label && latest.orderStatus != OrderStatus.CANCELLED.label) {
                        OutlinedButton(
                            onClick = {
                                onAdvanceStatus(latest, OrderStatus.fromLabel(latest.orderStatus), "Payment verified by admin", true)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Verify & Mark Payment Paid")
                        }
                    }
                    OutlinedTextField(
                        value = statusNote,
                        onValueChange = { statusNote = it },
                        label = { Text("Activity / Cancel Note") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (history.isNotEmpty()) {
                        Text("Activity History", style = MaterialTheme.typography.labelLarge)
                        history.forEach { h ->
                            Text("• ${h.status}: ${h.note ?: ""}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                if (latest.orderStatus != OrderStatus.DELIVERED.label && latest.orderStatus != OrderStatus.CANCELLED.label) {
                    TextButton(
                        onClick = {
                            onAdvanceStatus(latest, OrderStatus.CANCELLED, statusNote.ifBlank { "Cancelled by admin" }, null)
                            detailOrder = null
                        }
                    ) {
                        Text("Cancel & Restore Stock", color = DangerCoral)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { detailOrder = null }) { Text("Close") }
            }
        )
    }
}

private fun nextStatusFor(current: String): OrderStatus? = when (OrderStatus.fromLabel(current)) {
    OrderStatus.CONFIRMED -> OrderStatus.PREPARING
    OrderStatus.PREPARING -> OrderStatus.PACKED
    OrderStatus.PACKED -> OrderStatus.OUT_FOR_DELIVERY
    OrderStatus.OUT_FOR_DELIVERY -> OrderStatus.DELIVERED
    else -> null
}
