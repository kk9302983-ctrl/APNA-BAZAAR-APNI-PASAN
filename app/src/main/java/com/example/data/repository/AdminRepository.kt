package com.example.data.repository

import com.example.data.model.AppSettings
import com.example.data.model.Category
import com.example.data.model.Coupon
import com.example.data.model.DeliveryZone
import com.example.data.model.InventoryLog
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.OrderStatusHistory
import com.example.data.model.PaymentStatus
import com.example.data.model.Product
import com.example.data.model.UserProfile
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AdminRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireAdminIdentity(): Pair<String, String> {
        val user = auth.currentUser ?: throw IllegalStateException("Admin sign-in required")
        return user.uid to (user.email ?: "admin@apnabazaar")
    }

    fun observeAllOrders(): Flow<List<Order>> = observeCollection("orders") {
        it.toObjects(Order::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { o -> o.createdAt?.seconds ?: 0L }
    }

    fun observeAllProducts(): Flow<List<Product>> = observeCollection("products") {
        it.toObjects(Product::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { p -> p.productName }
    }

    fun observeAllCategories(): Flow<List<Category>> = observeCollection("categories") {
        it.toObjects(Category::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { c -> c.sortOrder }
    }

    fun observeAllCustomers(): Flow<List<UserProfile>> = observeCollection("users") {
        it.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { u -> u.createdAt?.seconds ?: 0L }
    }

    fun observeAllCoupons(): Flow<List<Coupon>> = observeCollection("coupons") {
        it.toObjects(Coupon::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { c -> c.createdAt?.seconds ?: 0L }
    }

    fun observeAllDeliveryZones(): Flow<List<DeliveryZone>> = observeCollection("delivery_zones") {
        it.toObjects(DeliveryZone::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    }

    fun observeInventoryLogs(): Flow<List<InventoryLog>> = observeCollection("inventory_logs") {
        it.toObjects(InventoryLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { log -> log.createdAt?.seconds ?: 0L }
    }

    fun observeAllOrderHistory(): Flow<List<OrderStatusHistory>> = observeCollection("order_status_history") {
        it.toObjects(OrderStatusHistory::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { h -> h.createdAt?.seconds ?: 0L }
    }

    private fun <T> observeCollection(
        collection: String,
        mapper: (com.google.firebase.firestore.QuerySnapshot) -> List<T>
    ): Flow<List<T>> = flow {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Admin sign-in required")
        emitAll(
            db.collection(collection)
                .snapshots()
                .map(mapper)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, collection)
                    }
                    throw error
                }
        )
    }

    /**
     * Atomic transaction to confirm a Pending order, reserve stock across all line items
     * without overselling, and record inventory_logs + order_status_history + notification.
     */
    suspend fun confirmOrderAndReserveStock(order: Order): Result<Unit> {
        val path = "orders/${order.id}"
        return try {
            val (adminUid, adminEmail) = requireAdminIdentity()
            require(order.orderStatus == OrderStatus.PENDING.label) {
                "Only Pending orders can be confirmed."
            }
            db.runTransaction { tx ->
                val orderRef = db.collection("orders").document(order.id)
                val orderSnap = tx.get(orderRef)
                val latestStatus = orderSnap.getString("orderStatus") ?: OrderStatus.PENDING.label
                if (latestStatus != OrderStatus.PENDING.label) {
                    throw IllegalStateException("Order status already changed to $latestStatus")
                }

                val productSnapshots = order.orderItemsSnapshot.map { item ->
                    val pRef = db.collection("products").document(item.productId)
                    Triple(item, pRef, tx.get(pRef))
                }

                for ((item, pRef, pSnap) in productSnapshots) {
                    val stock = pSnap.getLong("stockQuantity") ?: 0L
                    val reserved = pSnap.getLong("reservedQuantity") ?: 0L
                    val available = stock - reserved
                    if (available < item.quantity) {
                        throw IllegalStateException("Insufficient stock for ${item.productName}: $available available.")
                    }
                    val newReserved = reserved + item.quantity
                    tx.update(
                        pRef,
                        mapOf(
                            "reservedQuantity" to newReserved,
                            "available" to ((stock - newReserved) > 0L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    val logRef = db.collection("inventory_logs").document()
                    tx.set(
                        logRef,
                        mapOf(
                            "productId" to item.productId,
                            "productName" to item.productName,
                            "changeType" to "RESERVE",
                            "quantityDelta" to item.quantity,
                            "previousStock" to stock,
                            "newStock" to stock,
                            "reason" to "Reserved for order ${order.orderNumber}",
                            "adminUid" to adminUid,
                            "adminEmail" to adminEmail,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                tx.update(
                    orderRef,
                    mapOf(
                        "orderStatus" to OrderStatus.CONFIRMED.label,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                val historyRef = db.collection("order_status_history").document()
                tx.set(
                    historyRef,
                    mapOf(
                        "orderId" to order.id,
                        "customerId" to order.customerId,
                        "status" to OrderStatus.CONFIRMED.label,
                        "note" to "Order confirmed and stock reserved",
                        "changedBy" to adminUid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )

                val notifRef = db.collection("notifications").document()
                tx.set(
                    notifRef,
                    mapOf(
                        "userId" to order.customerId,
                        "orderId" to order.id,
                        "title" to "Order ${order.orderNumber} Confirmed",
                        "message" to "Your order has been confirmed and is being prepared.",
                        "read" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Advances order status (`Preparing`, `Packed`, `Out for Delivery`, `Delivered`, `Cancelled`).
     * Handles final stock deduction + soldQuantity increment on `Delivered`,
     * and stock reservation release on `Cancelled`.
     */
    suspend fun updateOrderStatus(
        order: Order,
        nextStatus: OrderStatus,
        note: String = "",
        markPaymentPaid: Boolean? = null
    ): Result<Unit> {
        val path = "orders/${order.id}"
        return try {
            val (adminUid, adminEmail) = requireAdminIdentity()
            db.runTransaction { tx ->
                val orderRef = db.collection("orders").document(order.id)
                val orderSnap = tx.get(orderRef)
                val currentStatus = OrderStatus.fromLabel(orderSnap.getString("orderStatus"))

                val wasReserved = currentStatus in listOf(
                    OrderStatus.CONFIRMED,
                    OrderStatus.PREPARING,
                    OrderStatus.PACKED,
                    OrderStatus.OUT_FOR_DELIVERY
                )

                val productSnapshots = order.orderItemsSnapshot.map { item ->
                    val pRef = db.collection("products").document(item.productId)
                    Triple(item, pRef, tx.get(pRef))
                }

                if (nextStatus == OrderStatus.DELIVERED && currentStatus != OrderStatus.DELIVERED) {
                    for ((item, pRef, pSnap) in productSnapshots) {
                        val stock = pSnap.getLong("stockQuantity") ?: 0L
                        val reserved = pSnap.getLong("reservedQuantity") ?: 0L
                        val sold = pSnap.getLong("soldQuantity") ?: 0L
                        val newStock = (stock - item.quantity).coerceAtLeast(0L)
                        val newReserved = if (wasReserved) (reserved - item.quantity).coerceAtLeast(0L) else reserved
                        tx.update(
                            pRef,
                            mapOf(
                                "stockQuantity" to newStock,
                                "reservedQuantity" to newReserved,
                                "soldQuantity" to (sold + item.quantity),
                                "available" to ((newStock - newReserved) > 0L),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        val logRef = db.collection("inventory_logs").document()
                        tx.set(
                            logRef,
                            mapOf(
                                "productId" to item.productId,
                                "productName" to item.productName,
                                "changeType" to "SALE",
                                "quantityDelta" to -item.quantity,
                                "previousStock" to stock,
                                "newStock" to newStock,
                                "reason" to "Delivered order ${order.orderNumber}",
                                "adminUid" to adminUid,
                                "adminEmail" to adminEmail,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                } else if (nextStatus == OrderStatus.CANCELLED && wasReserved) {
                    for ((item, pRef, pSnap) in productSnapshots) {
                        val stock = pSnap.getLong("stockQuantity") ?: 0L
                        val reserved = pSnap.getLong("reservedQuantity") ?: 0L
                        val newReserved = (reserved - item.quantity).coerceAtLeast(0L)
                        tx.update(
                            pRef,
                            mapOf(
                                "reservedQuantity" to newReserved,
                                "available" to ((stock - newReserved) > 0L),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        val logRef = db.collection("inventory_logs").document()
                        tx.set(
                            logRef,
                            mapOf(
                                "productId" to item.productId,
                                "productName" to item.productName,
                                "changeType" to "RELEASE",
                                "quantityDelta" to item.quantity,
                                "previousStock" to stock,
                                "newStock" to stock,
                                "reason" to "Released from cancelled order ${order.orderNumber}",
                                "adminUid" to adminUid,
                                "adminEmail" to adminEmail,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }

                val orderUpdates = mutableMapOf<String, Any>(
                    "orderStatus" to nextStatus.label,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (nextStatus == OrderStatus.CANCELLED && note.isNotBlank()) {
                    orderUpdates["cancelReason"] = note.take(200)
                }
                if (markPaymentPaid == true || (nextStatus == OrderStatus.DELIVERED && order.paymentMethod == "COD")) {
                    orderUpdates["paymentStatus"] = PaymentStatus.PAID.label
                }
                tx.update(orderRef, orderUpdates)

                val historyRef = db.collection("order_status_history").document()
                tx.set(
                    historyRef,
                    mapOf(
                        "orderId" to order.id,
                        "customerId" to order.customerId,
                        "status" to nextStatus.label,
                        "note" to note.ifBlank { "Status updated to ${nextStatus.label}" }.take(200),
                        "changedBy" to adminUid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )

                val notifRef = db.collection("notifications").document()
                tx.set(
                    notifRef,
                    mapOf(
                        "userId" to order.customerId,
                        "orderId" to order.id,
                        "title" to "Order ${order.orderNumber}: ${nextStatus.label}",
                        "message" to note.ifBlank { "Your order status is now ${nextStatus.label}." }.take(300),
                        "read" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adjustProductStock(
        product: Product,
        delta: Long,
        reason: String
    ): Result<Unit> {
        val path = "products/${product.id}"
        return try {
            val (adminUid, adminEmail) = requireAdminIdentity()
            require(reason.isNotBlank()) { "Adjustment reason is required." }
            db.runTransaction { tx ->
                val pRef = db.collection("products").document(product.id)
                val snap = tx.get(pRef)
                val prevStock = snap.getLong("stockQuantity") ?: 0L
                val reserved = snap.getLong("reservedQuantity") ?: 0L
                val newStock = (prevStock + delta).coerceAtLeast(reserved)
                val available = (newStock - reserved) > 0L

                tx.update(
                    pRef,
                    mapOf(
                        "stockQuantity" to newStock,
                        "available" to available,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                val logRef = db.collection("inventory_logs").document()
                tx.set(
                    logRef,
                    mapOf(
                        "productId" to product.id,
                        "productName" to product.productName,
                        "changeType" to if (delta >= 0) "RESTOCK" else "ADJUSTMENT",
                        "quantityDelta" to (newStock - prevStock),
                        "previousStock" to prevStock,
                        "newStock" to newStock,
                        "reason" to reason.trim().take(200),
                        "adminUid" to adminUid,
                        "adminEmail" to adminEmail,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveCategory(category: Category): Result<Unit> {
        val docRef = if (category.id.isNotBlank()) db.collection("categories").document(category.id)
        else db.collection("categories").document()
        return try {
            requireAdminIdentity()
            if (category.id.isNotBlank()) {
                docRef.update(
                    mapOf(
                        "name" to category.name.trim().take(60),
                        "subtitle" to (category.subtitle ?: "").take(100),
                        "iconKey" to category.iconKey.take(40),
                        "sortOrder" to category.sortOrder,
                        "active" to category.active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "name" to category.name.trim().take(60),
                        "subtitle" to (category.subtitle ?: "").take(100),
                        "iconKey" to category.iconKey.take(40),
                        "sortOrder" to category.sortOrder,
                        "active" to category.active,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(categoryId: String): Result<Unit> {
        return try {
            requireAdminIdentity()
            db.collection("categories").document(categoryId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "categories/$categoryId")
            Result.failure(e)
        }
    }

    suspend fun saveProduct(product: Product): Result<Unit> {
        val docRef = if (product.id.isNotBlank()) db.collection("products").document(product.id)
        else db.collection("products").document()
        return try {
            requireAdminIdentity()
            val safeOrig = maxOf(product.originalPrice, product.sellingPrice).coerceAtLeast(1.0)
            val safeSell = product.sellingPrice.coerceAtLeast(1.0)
            val common = mapOf(
                "productName" to product.productName.trim().take(100),
                "brand" to product.brand.trim().ifBlank { "Apna Bazaar" }.take(60),
                "description" to (product.description ?: "").trim().take(600),
                "categoryId" to product.categoryId,
                "categoryName" to product.categoryName.take(60),
                "imageUrls" to product.imageUrls.take(5),
                "sellingPrice" to safeSell,
                "originalPrice" to safeOrig,
                "unit" to product.unit.trim().ifBlank { "1 unit" }.take(30),
                "stockQuantity" to product.stockQuantity.coerceAtLeast(0L),
                "reservedQuantity" to product.reservedQuantity.coerceAtLeast(0L),
                "soldQuantity" to product.soldQuantity.coerceAtLeast(0L),
                "lowStockThreshold" to product.lowStockThreshold.coerceAtLeast(0L),
                "available" to (product.available && product.availableToSell > 0L),
                "active" to product.active,
                "bestSeller" to product.bestSeller,
                "recommended" to product.recommended,
                "dailyOffer" to product.dailyOffer,
                "popular" to product.popular,
                "featured" to product.featured,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (product.id.isNotBlank()) {
                docRef.update(common).await()
            } else {
                val createMap = common.toMutableMap()
                createMap["createdAt"] = FieldValue.serverTimestamp()
                docRef.set(createMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveCoupon(coupon: Coupon): Result<Unit> {
        val docRef = if (coupon.id.isNotBlank()) db.collection("coupons").document(coupon.id)
        else db.collection("coupons").document()
        return try {
            requireAdminIdentity()
            val payload = mutableMapOf<String, Any>(
                "code" to coupon.code.trim().uppercase().take(32),
                "description" to (coupon.description ?: "").trim().take(160),
                "discountType" to if (coupon.discountType == "PERCENT") "PERCENT" else "FLAT",
                "discountValue" to coupon.discountValue.coerceAtLeast(1.0),
                "minOrderValue" to coupon.minOrderValue.coerceAtLeast(0.0),
                "maxDiscount" to coupon.maxDiscount.coerceAtLeast(0.0),
                "expiryEpochMs" to coupon.expiryEpochMs.coerceAtLeast(System.currentTimeMillis() + 86_400_000L),
                "active" to coupon.active,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (coupon.id.isNotBlank()) {
                docRef.update(payload).await()
            } else {
                payload["createdAt"] = FieldValue.serverTimestamp()
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveDeliveryZone(zone: DeliveryZone): Result<Unit> {
        val docRef = if (zone.id.isNotBlank()) db.collection("delivery_zones").document(zone.id)
        else db.collection("delivery_zones").document()
        return try {
            requireAdminIdentity()
            val payload = mutableMapOf<String, Any>(
                "zoneName" to zone.zoneName.trim().take(80),
                "centerLat" to zone.centerLat,
                "centerLng" to zone.centerLng,
                "radiusKm" to zone.radiusKm.coerceIn(0.5, 25.0),
                "deliveryFee" to zone.deliveryFee.coerceAtLeast(0.0),
                "minOrderAmount" to zone.minOrderAmount.coerceAtLeast(0.0),
                "estimatedMinutes" to zone.estimatedMinutes.ifBlank { "30-60 mins" }.take(40),
                "active" to zone.active,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (zone.id.isNotBlank()) {
                docRef.update(payload).await()
            } else {
                payload["createdAt"] = FieldValue.serverTimestamp()
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveAppSettings(settings: AppSettings): Result<Unit> {
        val docRef = db.collection("app_settings").document("store_config")
        return try {
            requireAdminIdentity()
            val payload = mapOf(
                "storeName" to settings.storeName.take(80),
                "tagline" to settings.tagline.take(120),
                "serviceAvailable" to settings.serviceAvailable,
                "operatingHours" to settings.operatingHours.take(80),
                "defaultDeliveryFee" to settings.defaultDeliveryFee.coerceAtLeast(0.0),
                "minOrderAmount" to settings.minOrderAmount.coerceAtLeast(0.0),
                "serviceRadiusKm" to settings.serviceRadiusKm.coerceIn(0.5, 25.0),
                "storeLat" to settings.storeLat,
                "storeLng" to settings.storeLng,
                "estimatedDeliveryText" to settings.estimatedDeliveryText.take(40),
                "supportPhone" to (settings.supportPhone ?: "").take(20),
                "supportEmail" to (settings.supportEmail ?: "").take(120),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * One-tap store bootstrapper for Admin: populates the 10 standard categories,
     * initial store-owned SKUs, default 2.5 km delivery zone, store_config, and welcome coupon.
     */
    suspend fun seedInitialStoreCatalog(): Result<Unit> {
        return try {
            val (adminUid, adminEmail) = requireAdminIdentity()
            val batch = db.batch()

            val categories = listOf(
                Triple("cat_grocery", "Grocery & Ration", "grocery"),
                Triple("cat_fruits_veg", "Fruits & Vegetables", "produce"),
                Triple("cat_dairy", "Dairy & Breakfast", "dairy"),
                Triple("cat_snacks", "Snacks & Beverages", "snacks"),
                Triple("cat_personal", "Personal Care", "personal"),
                Triple("cat_cleaning", "Household Cleaning", "cleaning"),
                Triple("cat_kitchen", "Kitchen Essentials", "kitchen"),
                Triple("cat_baby", "Baby Care", "baby"),
                Triple("cat_health", "Health & Hygiene", "health"),
                Triple("cat_frozen", "Frozen & Packaged Foods", "frozen")
            )

            categories.forEachIndexed { idx, (id, name, iconKey) ->
                batch.set(
                    db.collection("categories").document(id),
                    mapOf(
                        "name" to name,
                        "subtitle" to "Fresh daily essentials",
                        "iconKey" to iconKey,
                        "sortOrder" to (idx + 1L),
                        "active" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            data class SeedSku(
                val id: String,
                val name: String,
                val brand: String,
                val desc: String,
                val catId: String,
                val catName: String,
                val img: String,
                val sell: Double,
                val mrp: Double,
                val unit: String,
                val stock: Long,
                val best: Boolean = false,
                val rec: Boolean = false,
                val offer: Boolean = false,
                val pop: Boolean = false,
                val feat: Boolean = false
            )

            val starterProducts = listOf(
                SeedSku("prod_atta", "Chakki Fresh Whole Wheat Atta", "Apna Harvest", "100% MP Sharbati stone-ground whole wheat flour for soft rotis.", "cat_grocery", "Grocery & Ration", "drawable://img_cat_pantry", 249.0, 295.0, "5 kg", 45, best = true, rec = true, feat = true),
                SeedSku("prod_basmati", "Royal Aged Basmati Rice", "Apna Harvest", "Long-grain aromatic aged basmati rice for biryani and pulao.", "cat_grocery", "Grocery & Ration", "drawable://img_cat_pantry", 145.0, 180.0, "1 kg", 32, best = true, pop = true),
                SeedSku("prod_toordal", "Unpolished Toor Dal", "Apna Harvest", "Farm-sourced protein-rich unpolished arhar dal.", "cat_grocery", "Grocery & Ration", "drawable://img_cat_pantry", 168.0, 195.0, "1 kg", 28, rec = true, offer = true),
                SeedSku("prod_tomato", "Desi Farm Tomatoes", "Fresh Mandi", "Juicy, ripe red tomatoes picked fresh every morning.", "cat_fruits_veg", "Fruits & Vegetables", "drawable://img_hero_groceries", 38.0, 50.0, "500 g", 60, best = true, offer = true, pop = true),
                SeedSku("prod_spinach", "Fresh Green Palak Bunch", "Fresh Mandi", "Crisp pesticide-free spinach leaves, triple washed.", "cat_fruits_veg", "Fruits & Vegetables", "drawable://img_hero_groceries", 24.0, 32.0, "250 g", 35, rec = true, feat = true),
                SeedSku("prod_mango", "Safeda Golden Mangoes", "Fresh Mandi", "Naturally ripened sweet seasonal mangoes.", "cat_fruits_veg", "Fruits & Vegetables", "drawable://img_hero_groceries", 135.0, 175.0, "1 kg", 20, offer = true, feat = true),
                SeedSku("prod_milk", "Fresh Full Cream Milk", "Apna Dairy", "Rich creamy homogenized cow & buffalo milk.", "cat_dairy", "Dairy & Breakfast", "drawable://img_cat_dairy_snacks", 34.0, 36.0, "500 ml", 70, best = true, pop = true, feat = true),
                SeedSku("prod_paneer", "Malai Fresh Paneer", "Apna Dairy", "Soft, protein-rich cottage cheese block.", "cat_dairy", "Dairy & Breakfast", "drawable://img_cat_dairy_snacks", 92.0, 110.0, "200 g", 24, rec = true, pop = true),
                SeedSku("prod_chai", "Assam Kadak Masala Chai", "Apna Tea", "Strong aromatic CTC tea blended with cardamom & ginger.", "cat_snacks", "Snacks & Beverages", "drawable://img_cat_dairy_snacks", 140.0, 165.0, "250 g", 30, best = true, offer = true),
                SeedSku("prod_oil", "Kachi Ghani Mustard Oil", "Apna Harvest", "Cold-pressed pure mustard oil for authentic Indian cooking.", "cat_kitchen", "Kitchen Essentials", "drawable://img_cat_pantry", 165.0, 195.0, "1 L", 26, rec = true, offer = true, feat = true)
            )

            starterProducts.forEach { sku ->
                batch.set(
                    db.collection("products").document(sku.id),
                    mapOf(
                        "productName" to sku.name,
                        "brand" to sku.brand,
                        "description" to sku.desc,
                        "categoryId" to sku.catId,
                        "categoryName" to sku.catName,
                        "imageUrls" to listOf(sku.img),
                        "sellingPrice" to sku.sell,
                        "originalPrice" to sku.mrp,
                        "unit" to sku.unit,
                        "stockQuantity" to sku.stock,
                        "reservedQuantity" to 0L,
                        "soldQuantity" to 0L,
                        "lowStockThreshold" to 6L,
                        "available" to true,
                        "active" to true,
                        "bestSeller" to sku.best,
                        "recommended" to sku.rec,
                        "dailyOffer" to sku.offer,
                        "popular" to sku.pop,
                        "featured" to sku.feat,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.set(
                db.collection("delivery_zones").document("zone_central"),
                mapOf(
                    "zoneName" to "Local Neighbourhood (2.5 km)",
                    "centerLat" to 28.6139,
                    "centerLng" to 77.2090,
                    "radiusKm" to 2.5,
                    "deliveryFee" to 20.0,
                    "minOrderAmount" to 99.0,
                    "estimatedMinutes" to "30-60 mins",
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            batch.set(
                db.collection("app_settings").document("store_config"),
                mapOf(
                    "storeName" to "APNA BAZAAR | APNI PASAND",
                    "tagline" to "Your Daily Needs, At Your Doorstep.",
                    "serviceAvailable" to true,
                    "operatingHours" to "7:00 AM - 10:30 PM",
                    "defaultDeliveryFee" to 20.0,
                    "minOrderAmount" to 99.0,
                    "serviceRadiusKm" to 2.5,
                    "storeLat" to 28.6139,
                    "storeLng" to 77.2090,
                    "estimatedDeliveryText" to "30-60 mins",
                    "supportPhone" to "+91 9876543210",
                    "supportEmail" to adminEmail,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            batch.set(
                db.collection("coupons").document("coupon_apna50"),
                mapOf(
                    "code" to "APNA50",
                    "description" to "Flat ₹50 off on orders above ₹299",
                    "discountType" to "FLAT",
                    "discountValue" to 50.0,
                    "minOrderValue" to 299.0,
                    "maxDiscount" to 50.0,
                    "expiryEpochMs" to (System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000),
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            batch.set(
                db.collection("inventory_logs").document(),
                mapOf(
                    "productId" to "prod_atta",
                    "productName" to "Initial Store Catalog",
                    "changeType" to "RESTOCK",
                    "quantityDelta" to 45L,
                    "previousStock" to 0L,
                    "newStock" to 45L,
                    "reason" to "Initialized 10 categories & starter inventory",
                    "adminUid" to adminUid,
                    "adminEmail" to adminEmail,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "categories")
            Result.failure(e)
        }
    }
}
