package com.example

import com.example.data.model.AppSettings
import com.example.data.model.CartItem
import com.example.data.model.Coupon
import com.example.data.model.DeliveryZone
import com.example.domain.DeliveryCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryCalculatorTest {

    @Test
    fun `serviceability accepts addresses within 2_5 km and rejects outside`() {
        val settings = AppSettings(
            serviceAvailable = true,
            storeLat = 28.6139,
            storeLng = 77.2090,
            serviceRadiusKm = 2.5,
            defaultDeliveryFee = 20.0,
            minOrderAmount = 99.0
        )
        val zones = listOf(
            DeliveryZone(
                id = "z1",
                zoneName = "Central",
                centerLat = 28.6139,
                centerLng = 77.2090,
                radiusKm = 2.5,
                deliveryFee = 20.0,
                minOrderAmount = 99.0,
                estimatedMinutes = "30-60 mins",
                active = true
            )
        )

        // ~1.1 km north
        val inside = DeliveryCalculator.evaluateServiceability(28.6239, 77.2090, settings, zones)
        assertTrue(inside.serviceable)
        assertEquals(20.0, inside.deliveryFee, 0.01)

        // ~11 km north
        val outside = DeliveryCalculator.evaluateServiceability(28.7139, 77.2090, settings, zones)
        assertFalse(outside.serviceable)
    }

    @Test
    fun `grand total calculates subtotal minus coupon discount plus delivery fee accurately`() {
        val items = listOf(
            CartItem(productId = "p1", productName = "Atta", sellingPrice = 249.0, originalPrice = 295.0, quantity = 1),
            CartItem(productId = "p2", productName = "Milk", sellingPrice = 34.0, originalPrice = 36.0, quantity = 2)
        )
        val now = 1_700_000_000_000L
        val validCoupon = Coupon(
            code = "APNA50",
            discountType = "FLAT",
            discountValue = 50.0,
            minOrderValue = 299.0,
            maxDiscount = 50.0,
            expiryEpochMs = now + 100_000L,
            active = true
        )

        // Subtotal = 249 + 68 = 317; Discount = 50; Delivery = 20 -> Grand Total = 287
        val bill = DeliveryCalculator.calculateBill(items, validCoupon, 20.0, 99.0, now)
        assertEquals(317.0, bill.subtotal, 0.01)
        assertEquals(50.0, bill.couponDiscount, 0.01)
        assertEquals(20.0, bill.deliveryFee, 0.01)
        assertEquals(287.0, bill.grandTotal, 0.01)
        assertTrue(bill.meetsMinOrder)
    }

    @Test
    fun `expired or below min order coupon yields zero discount`() {
        val items = listOf(
            CartItem(productId = "p2", productName = "Milk", sellingPrice = 34.0, originalPrice = 36.0, quantity = 2)
        )
        val now = 1_700_000_000_000L
        val expired = Coupon(
            code = "OLD50",
            discountType = "FLAT",
            discountValue = 50.0,
            minOrderValue = 50.0,
            maxDiscount = 50.0,
            expiryEpochMs = now - 1000L,
            active = true
        )
        val bill = DeliveryCalculator.calculateBill(items, expired, 20.0, 99.0, now)
        assertEquals(0.0, bill.couponDiscount, 0.01)
        assertFalse(bill.meetsMinOrder) // 68 < 99
    }
}
