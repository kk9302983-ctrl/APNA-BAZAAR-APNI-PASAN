package com.example.ui.customer

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppSettings
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.OrderStatusHistory
import com.example.data.model.PaymentMethod
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.domain.DeliveryCalculator
import com.example.ui.components.EmptyIllustrationState
import com.example.ui.components.OrderStatusChip
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary

@Composable
fun OrdersScreen(
    ordersState: UiState<List<Order>>,
    orderHistory: List<OrderStatusHistory>,
    onBrowseStore: () -> Unit,
    onCancelOrder: (Order, String) -> Unit,
    onReorder: (Order) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var activeDetailOrder by remember { mutableStateOf<Order?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Current Orders") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Previous Orders") })
        }

        when (ordersState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EmeraldPrimary)
            }
            is UiState.Error -> Text(ordersState.message, color = DangerCoral, modifier = Modifier.padding(16.dp))
            is UiState.Success -> {
                val filtered = ordersState.data.filter { order ->
                    val terminal = order.orderStatus == OrderStatus.DELIVERED.label ||
                        order.orderStatus == OrderStatus.CANCELLED.label
                    if (selectedTab == 0) !terminal else terminal
                }
                if (filtered.isEmpty()) {
                    EmptyIllustrationState(
                        title = if (selectedTab == 0) "No active orders" else "No past orders yet",
                        subtitle = "Orders you place will appear here with real-time tracking.",
                        actionLabel = "Shop Now",
                        onAction = onBrowseStore
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filtered, key = { it.id }) { order ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeDetailOrder = order },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(order.orderNumber, style = MaterialTheme.typography.titleMedium)
                                        OrderStatusChip(order.orderStatus)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "${order.orderItemsSnapshot.sumOf { it.quantity }} item(s) • ${DeliveryCalculator.formatRupees(order.grandTotal)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${PaymentMethod.fromCode(order.paymentMethod).displayName} (${order.paymentStatus})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { activeDetailOrder = order }) {
                                            Text("View Details")
                                        }
                                        Button(
                                            onClick = { onReorder(order) },
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                                        ) {
                                            Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Reorder", color = Color.White)
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

    activeDetailOrder?.let { selectedOrder ->
        val latestOrder = (ordersState as? UiState.Success)?.data?.firstOrNull { it.id == selectedOrder.id } ?: selectedOrder
        val historyForOrder = orderHistory.filter { it.orderId == latestOrder.id }
        val canCancel = latestOrder.orderStatus == OrderStatus.PENDING.label ||
            latestOrder.orderStatus == OrderStatus.CONFIRMED.label
        var cancelReason by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { activeDetailOrder = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(latestOrder.orderNumber)
                    OrderStatusChip(latestOrder.orderStatus)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Delivering to: ${latestOrder.addressSnapshot.houseLine}, ${latestOrder.addressSnapshot.areaLine}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    HorizontalDivider()
                    latestOrder.orderItemsSnapshot.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${item.quantity}x ${item.productName}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(DeliveryCalculator.formatRupees(item.lineTotal), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider()
                    Text(
                        text = "Total: ${DeliveryCalculator.formatRupees(latestOrder.grandTotal)} • ${latestOrder.paymentMethod} (${latestOrder.paymentStatus})",
                        style = MaterialTheme.typography.titleSmall,
                        color = EmeraldDark
                    )
                    if (historyForOrder.isNotEmpty()) {
                        Text("Status Timeline", style = MaterialTheme.typography.labelLarge)
                        historyForOrder.forEach { h ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${h.status}: ${h.note ?: ""}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (canCancel) {
                        OutlinedTextField(
                            value = cancelReason,
                            onValueChange = { cancelReason = it },
                            label = { Text("Cancellation reason (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (canCancel) {
                    TextButton(
                        onClick = {
                            onCancelOrder(latestOrder, cancelReason)
                            activeDetailOrder = null
                        }
                    ) {
                        Text("Cancel Order", color = DangerCoral)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { activeDetailOrder = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun ProfileScreen(
    profile: UserProfile?,
    userEmail: String?,
    isAdmin: Boolean,
    appSettings: AppSettings,
    onUpdateProfile: (String, String) -> Unit,
    onOpenAddresses: () -> Unit,
    onOpenOrders: () -> Unit,
    onOpenAdminPortal: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember(profile) { mutableStateOf(profile?.name ?: "") }
    var phone by remember(profile) { mutableStateOf(profile?.phone ?: "") }
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogBody by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = EmeraldContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = EmeraldDark)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(profile?.name ?: "Apna Bazaar Customer", style = MaterialTheme.typography.titleMedium)
                            Text(userEmail ?: profile?.email ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Customer Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onUpdateProfile(name, phone) },
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Profile", color = Color.White)
                    }
                }
            }
        }

        if (isAdmin) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenAdminPortal)
                        .testTag("open_admin_portal_button"),
                    colors = CardDefaults.cardColors(containerColor = EmeraldContainer),
                    border = BorderStroke(1.5.dp, EmeraldPrimary)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.AdminPanelSettings, contentDescription = "Admin Portal", tint = EmeraldDark)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Admin Management Portal", style = MaterialTheme.typography.titleMedium, color = EmeraldDark)
                            Text("Manage inventory, orders, coupons & delivery zones", style = MaterialTheme.typography.bodySmall, color = EmeraldDark)
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column {
                    ProfileMenuRow(Icons.Filled.LocationOn, "Saved Addresses", onOpenAddresses)
                    HorizontalDivider()
                    ProfileMenuRow(Icons.Filled.ReceiptLong, "My Orders", onOpenOrders)
                    HorizontalDivider()
                    ProfileMenuRow(Icons.Filled.HelpOutline, "Help & Support") {
                        infoDialogTitle = "Help & Support"
                        infoDialogBody = "Store Hours: ${appSettings.operatingHours}\nPhone: ${appSettings.supportPhone ?: "+91 9876543210"}\nEmail: ${appSettings.supportEmail ?: "support@apnabazaar.local"}"
                    }
                    HorizontalDivider()
                    ProfileMenuRow(Icons.Filled.PrivacyTip, "Privacy Policy") {
                        infoDialogTitle = "Privacy Policy"
                        infoDialogBody = "APNA BAZAAR | APNI PASAND stores your profile, delivery addresses, and order history securely in Cloud Firestore, accessible only to you and authorized store administrators for local order fulfillment."
                    }
                    HorizontalDivider()
                    ProfileMenuRow(Icons.Filled.Description, "Terms & Conditions") {
                        infoDialogTitle = "Terms & Conditions"
                        infoDialogBody = "Deliveries operate within our 2–3 km local service area during store hours (${appSettings.operatingHours}). Cancellations are permitted prior to packing."
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onSignOut,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DangerCoral),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("logout_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = DangerCoral)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout", color = DangerCoral)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (infoDialogTitle != null) {
        AlertDialog(
            onDismissRequest = { infoDialogTitle = null },
            title = { Text(infoDialogTitle ?: "") },
            text = { Text(infoDialogBody) },
            confirmButton = {
                TextButton(onClick = { infoDialogTitle = null }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun ProfileMenuRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = EmeraldPrimary)
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}
