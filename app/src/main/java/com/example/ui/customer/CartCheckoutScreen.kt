package com.example.ui.customer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Address
import com.example.data.model.CartItem
import com.example.data.model.Coupon
import com.example.data.model.Order
import com.example.data.model.PaymentMethod
import com.example.data.model.Product
import com.example.domain.BillSummary
import com.example.domain.DeliveryCalculator
import com.example.domain.ServiceabilityResult
import com.example.ui.components.EmptyIllustrationState
import com.example.ui.components.OrderStatusChip
import com.example.ui.components.ProductArtwork
import com.example.ui.theme.DangerContainer
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SaffronAccent
import com.example.ui.theme.SaffronContainer

@Composable
fun CartCheckoutScreen(
    cartItems: List<CartItem>,
    liveProducts: Map<String, Product>,
    coupons: List<Coupon>,
    appliedCoupon: Coupon?,
    bill: BillSummary,
    activeAddress: Address?,
    serviceability: ServiceabilityResult,
    defaultName: String,
    defaultPhone: String,
    isPlacingOrder: Boolean,
    confirmedOrder: Order?,
    onBrowseStore: () -> Unit,
    onUpdateCart: (Product, Long) -> Unit,
    onRemoveItem: (String) -> Unit,
    onApplyCoupon: (String) -> Unit,
    onSelectAddress: () -> Unit,
    onPlaceOrder: (String, String, String, PaymentMethod) -> Unit,
    onViewOrdersAfterConfirm: () -> Unit,
    onContinueShoppingAfterConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (confirmedOrder != null) {
        OrderConfirmationView(
            order = confirmedOrder,
            onTrackOrder = onViewOrdersAfterConfirm,
            onContinueShopping = onContinueShoppingAfterConfirm,
            modifier = modifier
        )
        return
    }

    if (cartItems.isEmpty()) {
        EmptyIllustrationState(
            title = "Your basket is empty",
            subtitle = "Add fresh vegetables, ration, dairy & daily essentials for 30–60 min delivery.",
            actionLabel = "Start Shopping",
            onAction = onBrowseStore,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    var couponInput by remember { mutableStateOf(appliedCoupon?.code ?: "") }
    var customerName by remember(defaultName, activeAddress) {
        mutableStateOf(activeAddress?.recipientName?.ifBlank { defaultName } ?: defaultName)
    }
    var customerPhone by remember(defaultPhone, activeAddress) {
        mutableStateOf(activeAddress?.phone?.ifBlank { defaultPhone } ?: defaultPhone)
    }
    var deliveryInstructions by remember { mutableStateOf("") }
    var selectedPayment by remember { mutableStateOf(PaymentMethod.COD) }

    val hasStockIssue = cartItems.any { item ->
        val prod = liveProducts[item.productId]
        prod == null || !prod.active || !prod.available || prod.availableToSell < item.quantity
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Shopping Cart (${cartItems.sumOf { it.quantity }} items)",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        items(cartItems, key = { it.id }) { item ->
            val liveProd = liveProducts[item.productId]
            val stockOk = liveProd != null && liveProd.active && liveProd.available && liveProd.availableToSell >= item.quantity

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, if (stockOk) MaterialTheme.colorScheme.outlineVariant else DangerCoral)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProductArtwork(
                        imageUrl = item.imageUrl,
                        contentDescription = item.productName,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.productName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "${item.unit} • ${DeliveryCalculator.formatRupees(item.sellingPrice)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!stockOk) {
                            Text(
                                text = "Insufficient stock (${liveProd?.availableToSell ?: 0} left)",
                                style = MaterialTheme.typography.labelSmall,
                                color = DangerCoral
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (liveProd != null) {
                            Surface(
                                color = EmeraldContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onUpdateCart(liveProd, -1L) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                                    }
                                    Text(
                                        text = item.quantity.toString(),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    IconButton(
                                        onClick = { onUpdateCart(liveProd, 1L) },
                                        enabled = item.quantity < liveProd.availableToSell,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        IconButton(onClick = { onRemoveItem(item.id) }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove item", tint = DangerCoral)
                        }
                    }
                }
            }
        }

        // Coupon Section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocalOffer, contentDescription = null, tint = SaffronAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Offers & Coupons", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = couponInput,
                            onValueChange = { couponInput = it.uppercase() },
                            placeholder = { Text("Enter coupon code") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("coupon_code_input")
                        )
                        Button(
                            onClick = { onApplyCoupon(couponInput) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Text(if (appliedCoupon != null) "Update" else "Apply")
                        }
                    }
                    if (appliedCoupon != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Applied: ${appliedCoupon.code} (-${DeliveryCalculator.formatRupees(bill.couponDiscount)})",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldDark
                            )
                            Text(
                                text = "Remove",
                                style = MaterialTheme.typography.labelMedium,
                                color = DangerCoral,
                                modifier = Modifier.clickable {
                                    couponInput = ""
                                    onApplyCoupon("")
                                }
                            )
                        }
                    } else if (coupons.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        coupons.take(2).forEach { c ->
                            Text(
                                text = "Tap to use ${c.code}: ${c.description ?: "Min order ${DeliveryCalculator.formatRupees(c.minOrderValue)}"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldDark,
                                modifier = Modifier
                                    .clickable {
                                        couponInput = c.code
                                        onApplyCoupon(c.code)
                                    }
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Delivery Address & Contact
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = EmeraldPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delivery Details", style = MaterialTheme.typography.titleSmall)
                        }
                        OutlinedButton(onClick = onSelectAddress) {
                            Text(if (activeAddress == null) "Select Address" else "Change")
                        }
                    }

                    if (activeAddress == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No delivery address selected. Tap Select Address to continue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DangerCoral
                        )
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${activeAddress.label}: ${activeAddress.formattedSummary()}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = serviceability.reason,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (serviceability.serviceable) EmeraldDark else DangerCoral
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("Customer Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text("Mobile Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deliveryInstructions,
                        onValueChange = { deliveryInstructions = it },
                        label = { Text("Delivery Instructions / Landmark Note") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Payment Method Selector
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Payments, contentDescription = null, tint = EmeraldPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Payment Method", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    PaymentMethod.entries.forEach { method ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedPayment = method }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPayment == method,
                                onClick = { selectedPayment = method }
                            )
                            Column {
                                Text(method.displayName, style = MaterialTheme.typography.bodyLarge)
                                if (method == PaymentMethod.RAZORPAY) {
                                    Text(
                                        text = stringResource(R.string.razorpay_note),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bill Summary & Checkout CTA
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Bill Summary", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    BillRow("Subtotal", DeliveryCalculator.formatRupees(bill.subtotal))
                    if (bill.mrpSavings > 0) {
                        BillRow("Product Savings (MRP)", "-${DeliveryCalculator.formatRupees(bill.mrpSavings)}", EmeraldDark)
                    }
                    if (bill.couponDiscount > 0) {
                        BillRow("Coupon Discount", "-${DeliveryCalculator.formatRupees(bill.couponDiscount)}", EmeraldDark)
                    }
                    BillRow("Delivery Fee", DeliveryCalculator.formatRupees(bill.deliveryFee))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Grand Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = DeliveryCalculator.formatRupees(bill.grandTotal),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldDark
                        )
                    }

                    if (!bill.meetsMinOrder) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(color = SaffronContainer, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = "Minimum order amount is ${DeliveryCalculator.formatRupees(bill.minOrderAmount)}. Add ${DeliveryCalculator.formatRupees(bill.minOrderAmount - bill.subtotal)} more.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (!serviceability.serviceable) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(color = DangerContainer, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = serviceability.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = DangerCoral,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val canSubmit = !isPlacingOrder &&
                        !hasStockIssue &&
                        bill.meetsMinOrder &&
                        serviceability.serviceable &&
                        activeAddress != null &&
                        customerName.isNotBlank() &&
                        customerPhone.trim().length >= 6

                    Button(
                        onClick = {
                            onPlaceOrder(customerName, customerPhone, deliveryInstructions, selectedPayment)
                        },
                        enabled = canSubmit,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("place_order_button")
                    ) {
                        if (isPlacingOrder) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Confirming Order...", color = Color.White)
                        } else {
                            Text(
                                text = "Place Order • ${DeliveryCalculator.formatRupees(bill.grandTotal)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BillRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
private fun OrderConfirmationView(
    order: Order,
    onTrackOrder: () -> Unit,
    onContinueShopping: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = "Order confirmed",
            tint = EmeraldPrimary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text("Order Placed Successfully!", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Order ID: ${order.orderNumber}",
            style = MaterialTheme.typography.titleMedium,
            color = EmeraldDark
        )
        Spacer(modifier = Modifier.height(14.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Order Status", style = MaterialTheme.typography.bodyMedium)
                    OrderStatusChip(order.orderStatus)
                }
                BillRow("Payment Method", PaymentMethod.fromCode(order.paymentMethod).displayName)
                BillRow("Payment Status", order.paymentStatus)
                BillRow("Estimated Delivery", order.estimatedDeliveryMinutes)
                BillRow("Total Payable", DeliveryCalculator.formatRupees(order.grandTotal), EmeraldDark)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onTrackOrder,
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("track_order_button")
        ) {
            Text("View My Orders", color = Color.White)
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onContinueShopping,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Continue Shopping")
        }
    }
}
