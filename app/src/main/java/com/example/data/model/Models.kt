package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class OrderStatus(val label: String) {
    PENDING("Pending"),
    CONFIRMED("Confirmed"),
    PREPARING("Preparing"),
    PACKED("Packed"),
    OUT_FOR_DELIVERY("Out for Delivery"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled");

    companion object {
        fun fromLabel(raw: String?): OrderStatus =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) } ?: PENDING
    }
}

enum class PaymentMethod(val code: String, val displayName: String) {
    COD("COD", "Cash on Delivery"),
    RAZORPAY("RAZORPAY", "Online Payment (Razorpay)");

    companion object {
        fun fromCode(raw: String?): PaymentMethod =
            entries.firstOrNull { it.code.equals(raw, ignoreCase = true) } ?: COD
    }
}

enum class PaymentStatus(val label: String) {
    PENDING("Pending"),
    AWAITING_VERIFICATION("Awaiting Verification"),
    PAID("Paid"),
    FAILED("Failed"),
    REFUNDED("Refunded");

    companion object {
        fun fromLabel(raw: String?): PaymentStatus =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) } ?: PENDING
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val selectedAddressId: String? = null,
    val language: String = "en",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdminUser(
    @DocumentId val id: String = "",
    val adminId: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "manager",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Category(
    @DocumentId val id: String = "",
    val name: String = "",
    val subtitle: String? = null,
    val iconKey: String = "grocery",
    val sortOrder: Long = 0L,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val productName: String = "",
    val brand: String = "",
    val description: String? = null,
    val categoryId: String = "",
    val categoryName: String = "",
    val imageUrls: List<String> = emptyList(),
    val sellingPrice: Double = 0.0,
    val originalPrice: Double = 0.0,
    val unit: String = "",
    val stockQuantity: Long = 0L,
    val reservedQuantity: Long = 0L,
    val soldQuantity: Long = 0L,
    val lowStockThreshold: Long = 5L,
    val available: Boolean = true,
    val active: Boolean = true,
    val bestSeller: Boolean = false,
    val recommended: Boolean = false,
    val dailyOffer: Boolean = false,
    val popular: Boolean = false,
    val featured: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val availableToSell: Long
        get() = (stockQuantity - reservedQuantity).coerceAtLeast(0L)

    val discountPercent: Int
        get() = if (originalPrice > sellingPrice && originalPrice > 0.0) {
            (((originalPrice - sellingPrice) / originalPrice) * 100.0).toInt().coerceIn(0, 99)
        } else 0

    val lowStock: Boolean
        get() = availableToSell in 1..lowStockThreshold

    val outOfStock: Boolean
        get() = availableToSell <= 0L || !available
}

@Keep
@IgnoreExtraProperties
data class Cart(
    @DocumentId val id: String = "",
    val userId: String = "",
    val couponCode: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CartItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val productId: String = "",
    val productName: String = "",
    val brand: String? = null,
    val unit: String = "",
    val imageUrl: String? = null,
    val sellingPrice: Double = 0.0,
    val originalPrice: Double = 0.0,
    val quantity: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Address(
    @DocumentId val id: String = "",
    val userId: String = "",
    val label: String = "Home",
    val recipientName: String = "",
    val phone: String = "",
    val houseLine: String = "",
    val areaLine: String = "",
    val landmark: String? = null,
    val pincode: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val serviceable: Boolean = false,
    val defaultAddress: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun formattedSummary(): String =
        listOfNotNull(
            houseLine.takeIf { it.isNotBlank() },
            areaLine.takeIf { it.isNotBlank() },
            landmark?.takeIf { it.isNotBlank() }?.let { "Near $it" },
            pincode.takeIf { it.isNotBlank() }
        ).joinToString(", ")
}

@Keep
@IgnoreExtraProperties
data class AddressSnapshot(
    val label: String = "Home",
    val recipientName: String = "",
    val phone: String = "",
    val houseLine: String = "",
    val areaLine: String = "",
    val landmark: String = "",
    val pincode: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

@Keep
@IgnoreExtraProperties
data class OrderItemSnapshot(
    val productId: String = "",
    val productName: String = "",
    val unit: String = "",
    val imageUrl: String = "",
    val sellingPrice: Double = 0.0,
    val originalPrice: Double = 0.0,
    val quantity: Long = 1L,
    val lineTotal: Double = 0.0
)

@Keep
@IgnoreExtraProperties
data class Order(
    @DocumentId val id: String = "",
    val orderNumber: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val addressSnapshot: AddressSnapshot = AddressSnapshot(),
    val deliveryInstructions: String? = null,
    val orderItemsSnapshot: List<OrderItemSnapshot> = emptyList(),
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val grandTotal: Double = 0.0,
    val couponCode: String? = null,
    val paymentMethod: String = PaymentMethod.COD.code,
    val paymentStatus: String = PaymentStatus.PENDING.label,
    val orderStatus: String = OrderStatus.PENDING.label,
    val estimatedDeliveryMinutes: String = "30-60 mins",
    val cancelReason: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OrderItem(
    @DocumentId val id: String = "",
    val orderId: String = "",
    val customerId: String = "",
    val productId: String = "",
    val productName: String = "",
    val unit: String = "",
    val sellingPrice: Double = 0.0,
    val originalPrice: Double = 0.0,
    val quantity: Long = 1L,
    val lineTotal: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Coupon(
    @DocumentId val id: String = "",
    val code: String = "",
    val description: String? = null,
    val discountType: String = "FLAT",
    val discountValue: Double = 0.0,
    val minOrderValue: Double = 0.0,
    val maxDiscount: Double = 0.0,
    val expiryEpochMs: Long = 0L,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DeliveryZone(
    @DocumentId val id: String = "",
    val zoneName: String = "",
    val centerLat: Double = 28.6139,
    val centerLng: Double = 77.2090,
    val radiusKm: Double = 3.0,
    val deliveryFee: Double = 20.0,
    val minOrderAmount: Double = 99.0,
    val estimatedMinutes: String = "30-60 mins",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppSettings(
    @DocumentId val id: String = "store_config",
    val storeName: String = "APNA BAZAAR | APNI PASAND",
    val tagline: String = "Your Daily Needs, At Your Doorstep.",
    val serviceAvailable: Boolean = true,
    val operatingHours: String = "7:00 AM - 10:30 PM",
    val defaultDeliveryFee: Double = 20.0,
    val minOrderAmount: Double = 99.0,
    val serviceRadiusKm: Double = 3.0,
    val storeLat: Double = 28.6139,
    val storeLng: Double = 77.2090,
    val estimatedDeliveryText: String = "30-60 mins",
    val supportPhone: String? = "+91 9876543210",
    val supportEmail: String? = "abbas.0078666@gmail.com",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PaymentRecord(
    @DocumentId val id: String = "",
    val orderId: String = "",
    val customerId: String = "",
    val paymentMethod: String = PaymentMethod.COD.code,
    val paymentStatus: String = PaymentStatus.PENDING.label,
    val amount: Double = 0.0,
    val gatewayRef: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class InventoryLog(
    @DocumentId val id: String = "",
    val productId: String = "",
    val productName: String = "",
    val changeType: String = "ADJUSTMENT",
    val quantityDelta: Long = 0L,
    val previousStock: Long = 0L,
    val newStock: Long = 0L,
    val reason: String = "",
    val adminUid: String = "",
    val adminEmail: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val orderId: String? = null,
    val title: String = "",
    val message: String = "",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OrderStatusHistory(
    @DocumentId val id: String = "",
    val orderId: String = "",
    val customerId: String = "",
    val status: String = OrderStatus.PENDING.label,
    val note: String? = null,
    val changedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
