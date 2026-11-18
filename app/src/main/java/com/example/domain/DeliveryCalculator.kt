package com.example.domain

import com.example.data.model.AppSettings
import com.example.data.model.CartItem
import com.example.data.model.Coupon
import com.example.data.model.DeliveryZone
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class ServiceabilityResult(
    val serviceable: Boolean,
    val distanceKm: Double,
    val matchedZone: DeliveryZone?,
    val deliveryFee: Double,
    val minOrderAmount: Double,
    val estimatedMinutes: String,
    val reason: String
)

data class BillSummary(
    val subtotal: Double,
    val mrpSavings: Double,
    val couponDiscount: Double,
    val deliveryFee: Double,
    val grandTotal: Double,
    val minOrderAmount: Double,
    val meetsMinOrder: Boolean
)

object DeliveryCalculator {

    private const val EARTH_RADIUS_KM = 6371.0

    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        val c = 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        return EARTH_RADIUS_KM * c
    }

    fun evaluateServiceability(
        lat: Double,
        lng: Double,
        settings: AppSettings,
        zones: List<DeliveryZone>
    ): ServiceabilityResult {
        if (!settings.serviceAvailable) {
            return ServiceabilityResult(
                serviceable = false,
                distanceKm = 0.0,
                matchedZone = null,
                deliveryFee = settings.defaultDeliveryFee,
                minOrderAmount = settings.minOrderAmount,
                estimatedMinutes = settings.estimatedDeliveryText,
                reason = "Store is currently closed (${settings.operatingHours})."
            )
        }

        val activeZones = zones.filter { it.active }
        if (activeZones.isNotEmpty()) {
            val evaluated = activeZones.map { zone ->
                zone to distanceKm(lat, lng, zone.centerLat, zone.centerLng)
            }
            val inRange = evaluated.filter { (zone, dist) -> dist <= zone.radiusKm }
                .minByOrNull { it.second }
            if (inRange != null) {
                val (zone, dist) = inRange
                return ServiceabilityResult(
                    serviceable = true,
                    distanceKm = dist,
                    matchedZone = zone,
                    deliveryFee = zone.deliveryFee,
                    minOrderAmount = zone.minOrderAmount,
                    estimatedMinutes = zone.estimatedMinutes,
                    reason = "Delivering in ${zone.estimatedMinutes} (${formatKm(dist)} away)"
                )
            }
            val nearest = evaluated.minByOrNull { it.second }
            val nearestDist = nearest?.second ?: 0.0
            val radius = nearest?.first?.radiusKm ?: settings.serviceRadiusKm
            return ServiceabilityResult(
                serviceable = false,
                distanceKm = nearestDist,
                matchedZone = null,
                deliveryFee = settings.defaultDeliveryFee,
                minOrderAmount = settings.minOrderAmount,
                estimatedMinutes = settings.estimatedDeliveryText,
                reason = "Location is ${formatKm(nearestDist)} away (outside our ${formatKm(radius)} delivery area)."
            )
        }

        val dist = distanceKm(lat, lng, settings.storeLat, settings.storeLng)
        val ok = dist <= settings.serviceRadiusKm
        return ServiceabilityResult(
            serviceable = ok,
            distanceKm = dist,
            matchedZone = null,
            deliveryFee = settings.defaultDeliveryFee,
            minOrderAmount = settings.minOrderAmount,
            estimatedMinutes = settings.estimatedDeliveryText,
            reason = if (ok) {
                "Delivering in ${settings.estimatedDeliveryText} (${formatKm(dist)} away)"
            } else {
                "Location is ${formatKm(dist)} away (outside our ${formatKm(settings.serviceRadiusKm)} delivery area)."
            }
        )
    }

    fun calculateCouponDiscount(
        subtotal: Double,
        coupon: Coupon?,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Double {
        if (coupon == null || !coupon.active) return 0.0
        if (coupon.expiryEpochMs in 1 until nowEpochMs) return 0.0
        if (subtotal < coupon.minOrderValue) return 0.0

        val rawDiscount = when (coupon.discountType.uppercase()) {
            "PERCENT" -> subtotal * (coupon.discountValue / 100.0)
            else -> coupon.discountValue
        }
        val capped = if (coupon.maxDiscount > 0.0) minOf(rawDiscount, coupon.maxDiscount) else rawDiscount
        return roundRupees(capped.coerceIn(0.0, subtotal))
    }

    fun calculateBill(
        items: List<CartItem>,
        coupon: Coupon?,
        deliveryFee: Double,
        minOrderAmount: Double,
        nowEpochMs: Long = System.currentTimeMillis()
    ): BillSummary {
        val subtotal = roundRupees(items.sumOf { it.sellingPrice * it.quantity })
        val mrpTotal = roundRupees(items.sumOf { it.originalPrice * it.quantity })
        val mrpSavings = roundRupees((mrpTotal - subtotal).coerceAtLeast(0.0))
        val discount = calculateCouponDiscount(subtotal, coupon, nowEpochMs)
        val appliedDeliveryFee = if (subtotal <= 0.0) 0.0 else roundRupees(deliveryFee)
        val grandTotal = roundRupees((subtotal - discount + appliedDeliveryFee).coerceAtLeast(0.0))
        return BillSummary(
            subtotal = subtotal,
            mrpSavings = mrpSavings,
            couponDiscount = discount,
            deliveryFee = appliedDeliveryFee,
            grandTotal = grandTotal,
            minOrderAmount = minOrderAmount,
            meetsMinOrder = subtotal >= minOrderAmount && items.isNotEmpty()
        )
    }

    fun roundRupees(value: Double): Double = (value * 100.0).roundToInt() / 100.0

    fun formatKm(km: Double): String = "${((km * 10.0).roundToInt() / 10.0)} km"

    fun formatRupees(amount: Double): String {
        val rounded = roundRupees(amount)
        return if (rounded % 1.0 == 0.0) "₹${rounded.toInt()}" else "₹${"%.2f".format(rounded)}"
    }
}
