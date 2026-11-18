package com.example.ui.admin

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.AppSettings
import com.example.data.model.Coupon
import com.example.data.model.DeliveryZone
import com.example.data.model.Order
import com.example.data.model.UserProfile
import com.example.domain.DeliveryCalculator
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary

@Composable
fun AdminOperationsTab(
    customers: List<UserProfile>,
    orders: List<Order>,
    coupons: List<Coupon>,
    deliveryZones: List<DeliveryZone>,
    appSettings: AppSettings,
    onSaveCoupon: (Coupon) -> Unit,
    onSaveZone: (DeliveryZone) -> Unit,
    onSaveSettings: (AppSettings) -> Unit
) {
    var subTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subTab) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("Customers") })
            Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Coupons") })
            Tab(selected = subTab == 2, onClick = { subTab = 2 }, text = { Text("Delivery") })
        }

        when (subTab) {
            0 -> AdminCustomersSection(customers, orders)
            1 -> AdminCouponsSection(coupons, onSaveCoupon)
            2 -> AdminDeliverySettingsSection(appSettings, deliveryZones, onSaveSettings, onSaveZone)
        }
    }
}

@Composable
private fun AdminCustomersSection(customers: List<UserProfile>, orders: List<Order>) {
    var search by remember { mutableStateOf("") }
    val filtered = customers.filter {
        val q = search.trim()
        q.isEmpty() || it.name.contains(q, ignoreCase = true) ||
            it.phone.contains(q, ignoreCase = true) ||
            it.email.contains(q, ignoreCase = true)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search customers by name, email, phone...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        items(filtered, key = { it.id }) { user ->
            val userOrders = orders.filter { it.customerId == user.userId }
            val totalSpent = userOrders.sumOf { it.grandTotal }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(user.name, style = MaterialTheme.typography.titleSmall)
                    Text("${user.email} • ${user.phone.ifBlank { "No phone added" }}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${userOrders.size} orders • Total Spent: ${DeliveryCalculator.formatRupees(totalSpent)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldDark
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminCouponsSection(
    coupons: List<Coupon>,
    onSaveCoupon: (Coupon) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Button(
                onClick = { showCreate = true },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create Coupon", color = Color.White)
            }
        }
        items(coupons, key = { it.id }) { coupon ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(coupon.code, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${coupon.discountType}: ${coupon.discountValue} • Min Order: ${DeliveryCalculator.formatRupees(coupon.minOrderValue)} • Max Disc: ${DeliveryCalculator.formatRupees(coupon.maxDiscount)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = coupon.active,
                        onCheckedChange = { onSaveCoupon(coupon.copy(active = it)) }
                    )
                }
            }
        }
    }

    if (showCreate) {
        var code by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("FLAT") }
        var value by remember { mutableStateOf("50") }
        var minOrder by remember { mutableStateOf("299") }
        var maxDisc by remember { mutableStateOf("100") }
        var daysValid by remember { mutableStateOf("30") }

        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Create Discount Coupon") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = code, onValueChange = { code = it.uppercase() }, label = { Text("Coupon Code") }, singleLine = true)
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = type == "FLAT", onClick = { type = "FLAT" }, label = { Text("FLAT ₹") })
                        FilterChip(selected = type == "PERCENT", onClick = { type = "PERCENT" }, label = { Text("PERCENT %") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Value") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = minOrder, onValueChange = { minOrder = it }, label = { Text("Min Order") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = maxDisc, onValueChange = { maxDisc = it }, label = { Text("Max Discount") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = daysValid, onValueChange = { daysValid = it }, label = { Text("Valid Days") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val days = (daysValid.toLongOrNull() ?: 30L).coerceAtLeast(1L)
                        onSaveCoupon(
                            Coupon(
                                code = code.trim().uppercase(),
                                description = desc.trim().ifBlank { null },
                                discountType = type,
                                discountValue = value.toDoubleOrNull() ?: 10.0,
                                minOrderValue = minOrder.toDoubleOrNull() ?: 0.0,
                                maxDiscount = maxDisc.toDoubleOrNull() ?: 100.0,
                                expiryEpochMs = System.currentTimeMillis() + days * 86_400_000L,
                                active = true
                            )
                        )
                        showCreate = false
                    },
                    enabled = code.trim().length >= 2
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AdminDeliverySettingsSection(
    appSettings: AppSettings,
    zones: List<DeliveryZone>,
    onSaveSettings: (AppSettings) -> Unit,
    onSaveZone: (DeliveryZone) -> Unit
) {
    var serviceAvailable by remember(appSettings) { mutableStateOf(appSettings.serviceAvailable) }
    var operatingHours by remember(appSettings) { mutableStateOf(appSettings.operatingHours) }
    var eta by remember(appSettings) { mutableStateOf(appSettings.estimatedDeliveryText) }
    var deliveryFee by remember(appSettings) { mutableStateOf(appSettings.defaultDeliveryFee.toString()) }
    var minOrder by remember(appSettings) { mutableStateOf(appSettings.minOrderAmount.toString()) }
    var radiusKm by remember(appSettings) { mutableStateOf(appSettings.serviceRadiusKm.toString()) }
    var storeLat by remember(appSettings) { mutableStateOf(appSettings.storeLat.toString()) }
    var storeLng by remember(appSettings) { mutableStateOf(appSettings.storeLng.toString()) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Delivery Service Active", style = MaterialTheme.typography.titleSmall)
                            Text("Disable when store cannot fulfil deliveries", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = serviceAvailable, onCheckedChange = { serviceAvailable = it })
                    }
                    OutlinedTextField(value = operatingHours, onValueChange = { operatingHours = it }, label = { Text("Operating Hours") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = eta, onValueChange = { eta = it }, label = { Text("Estimated Delivery Window") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = deliveryFee, onValueChange = { deliveryFee = it }, label = { Text("Delivery Fee ₹") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = minOrder, onValueChange = { minOrder = it }, label = { Text("Min Order ₹") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = radiusKm, onValueChange = { radiusKm = it }, label = { Text("Service Radius (km)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = storeLat, onValueChange = { storeLat = it }, label = { Text("Store Lat") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = storeLng, onValueChange = { storeLng = it }, label = { Text("Store Lng") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = {
                            onSaveSettings(
                                appSettings.copy(
                                    serviceAvailable = serviceAvailable,
                                    operatingHours = operatingHours.trim(),
                                    estimatedDeliveryText = eta.trim(),
                                    defaultDeliveryFee = deliveryFee.toDoubleOrNull() ?: 20.0,
                                    minOrderAmount = minOrder.toDoubleOrNull() ?: 99.0,
                                    serviceRadiusKm = radiusKm.toDoubleOrNull() ?: 2.5,
                                    storeLat = storeLat.toDoubleOrNull() ?: 28.6139,
                                    storeLng = storeLng.toDoubleOrNull() ?: 77.2090
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Store Settings", color = Color.White)
                    }
                }
            }
        }

        if (zones.isNotEmpty()) {
            item { Text("Active Delivery Zones", style = MaterialTheme.typography.titleMedium) }
            items(zones, key = { it.id }) { zone ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(zone.zoneName, style = MaterialTheme.typography.titleSmall)
                            Text("Radius: ${zone.radiusKm} km • Fee: ${DeliveryCalculator.formatRupees(zone.deliveryFee)} • Min: ${DeliveryCalculator.formatRupees(zone.minOrderAmount)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = zone.active, onCheckedChange = { onSaveZone(zone.copy(active = it)) })
                    }
                }
            }
        }
    }
}
