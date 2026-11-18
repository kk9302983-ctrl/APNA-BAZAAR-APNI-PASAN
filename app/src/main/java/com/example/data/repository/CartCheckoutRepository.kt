package com.example.data.repository

import com.example.data.model.Address
import com.example.data.model.Cart
import com.example.data.model.CartItem
import com.example.data.model.Coupon
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentStatus
import com.example.data.model.Product
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.domain.DeliveryCalculator
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
import java.util.UUID

class CartCheckoutRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeCartItems(userId: String): Flow<List<CartItem>> = flow {
        val path = "cart_items"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CartItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreError(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCartMeta(userId: String): Flow<Cart?> = flow {
        val path = "carts/$userId"
        emitAll(
            db.collection("carts").document(userId)
                .snapshots()
                .map { snap -> snap.toObject(Cart::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = unwrapFirestoreError(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeAddresses(userId: String): Flow<List<Address>> = flow {
        val path = "addresses"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Address::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.defaultAddress }
                }
                .catch { error ->
                    val fsErr = unwrapFirestoreError(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addOrUpdateCartItem(product: Product, deltaQty: Long): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        val docId = "${uid}_${product.id}".take(128)
        val path = "cart_items/$docId"
        return try {
            require(product.active && product.available) { "Product is currently unavailable" }
            val docRef = db.collection("cart_items").document(docId)
            val existing = docRef.get().await()
            if (existing.exists()) {
                val currentQty = existing.getLong("quantity") ?: 0L
                val nextQty = (currentQty + deltaQty).coerceAtMost(99L)
                if (nextQty <= 0L) {
                    docRef.delete().await()
                } else {
                    require(nextQty <= product.availableToSell) {
                        "Only ${product.availableToSell} units available for ${product.productName}"
                    }
                    docRef.update(
                        mapOf(
                            "quantity" to nextQty,
                            "sellingPrice" to product.sellingPrice,
                            "originalPrice" to product.originalPrice,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } else if (deltaQty > 0L) {
                val clamped = deltaQty.coerceIn(1L, 99L)
                require(clamped <= product.availableToSell) {
                    "Only ${product.availableToSell} units available for ${product.productName}"
                }
                val payload = mapOf(
                    "userId" to uid,
                    "productId" to product.id,
                    "productName" to product.productName.take(100),
                    "brand" to product.brand.take(60),
                    "unit" to product.unit.take(30),
                    "imageUrl" to product.imageUrls.firstOrNull()?.take(500),
                    "sellingPrice" to product.sellingPrice,
                    "originalPrice" to product.originalPrice,
                    "quantity" to clamped,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun removeCartItem(cartItemId: String): Result<Unit> {
        val path = "cart_items/$cartItemId"
        return try {
            requireUserId()
            db.collection("cart_items").document(cartItemId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun applyCouponToCart(userId: String, couponCode: String?): Result<Unit> {
        val path = "carts/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val cartRef = db.collection("carts").document(userId)
            val snap = cartRef.get().await()
            val cleanCode = couponCode?.trim()?.uppercase()?.take(32)
            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "couponCode" to cleanCode,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                cartRef.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanCode.isNullOrEmpty()) {
                    updates["couponCode"] = ""
                } else {
                    updates["couponCode"] = cleanCode
                }
                cartRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun saveAddress(address: Address): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        val docRef = if (address.id.isNotBlank()) {
            db.collection("addresses").document(address.id)
        } else {
            db.collection("addresses").document()
        }
        return try {
            val payload = mapOf(
                "userId" to uid,
                "label" to address.label.trim().ifBlank { "Home" }.take(30),
                "recipientName" to address.recipientName.trim().take(80),
                "phone" to address.phone.trim().take(20),
                "houseLine" to address.houseLine.trim().take(150),
                "areaLine" to address.areaLine.trim().take(150),
                "landmark" to address.landmark?.trim()?.take(100),
                "pincode" to address.pincode.trim().take(12),
                "latitude" to address.latitude,
                "longitude" to address.longitude,
                "serviceable" to address.serviceable,
                "defaultAddress" to address.defaultAddress,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            if (address.id.isNotBlank()) {
                val updates = payload.toMutableMap()
                updates.remove("userId")
                updates.remove("createdAt")
                docRef.update(updates as Map<String, Any>).await()
            } else {
                docRef.set(payload).await()
            }
            if (address.defaultAddress) {
                db.collection("users").document(uid)
                    .update(mapOf("selectedAddressId" to docRef.id, "updatedAt" to FieldValue.serverTimestamp()))
                    .await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteAddress(addressId: String): Result<Unit> {
        val path = "addresses/$addressId"
        return try {
            requireUserId()
            db.collection("addresses").document(addressId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun placeOrder(
        customerName: String,
        customerPhone: String,
        address: Address,
        deliveryInstructions: String,
        cartItems: List<CartItem>,
        liveProducts: Map<String, Product>,
        coupon: Coupon?,
        deliveryFee: Double,
        minOrderAmount: Double,
        estimatedDeliveryMinutes: String,
        paymentMethod: PaymentMethod,
        gatewayRef: String?
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        val orderRef = db.collection("orders").document()
        return try {
            require(cartItems.isNotEmpty()) { "Your cart is empty." }
            require(address.serviceable) { "Selected delivery address is outside our service area." }

            // Re-validate prices and stock against latest server-synced products
            val verifiedItems = cartItems.map { item ->
                val product = liveProducts[item.productId]
                    ?: throw IllegalArgumentException("${item.productName} is no longer available.")
                require(product.active && product.available) {
                    "${product.productName} is currently unavailable."
                }
                require(product.availableToSell >= item.quantity) {
                    "Insufficient stock for ${product.productName} (${product.availableToSell} left)."
                }
                item.copy(
                    sellingPrice = product.sellingPrice,
                    originalPrice = product.originalPrice,
                    productName = product.productName,
                    unit = product.unit
                )
            }

            val bill = DeliveryCalculator.calculateBill(
                items = verifiedItems,
                coupon = coupon,
                deliveryFee = deliveryFee,
                minOrderAmount = minOrderAmount
            )
            require(bill.meetsMinOrder) {
                "Minimum order amount is ${DeliveryCalculator.formatRupees(minOrderAmount)}."
            }

            val orderNumber = "AB-${UUID.randomUUID().toString().take(6).uppercase()}"
            // Never mark online orders as Paid client-side without trusted backend verification
            val initialPaymentStatus = if (paymentMethod == PaymentMethod.RAZORPAY) {
                PaymentStatus.AWAITING_VERIFICATION.label
            } else {
                PaymentStatus.PENDING.label
            }

            val addressSnapshotMap = mapOf(
                "label" to address.label,
                "recipientName" to customerName.trim().take(80),
                "phone" to customerPhone.trim().take(20),
                "houseLine" to address.houseLine,
                "areaLine" to address.areaLine,
                "landmark" to (address.landmark ?: ""),
                "pincode" to address.pincode,
                "latitude" to address.latitude,
                "longitude" to address.longitude
            )

            val itemsSnapshotList = verifiedItems.map { item ->
                mapOf(
                    "productId" to item.productId,
                    "productName" to item.productName,
                    "unit" to item.unit,
                    "imageUrl" to (item.imageUrl ?: ""),
                    "sellingPrice" to item.sellingPrice,
                    "originalPrice" to item.originalPrice,
                    "quantity" to item.quantity,
                    "lineTotal" to DeliveryCalculator.roundRupees(item.sellingPrice * item.quantity)
                )
            }

            val orderPayload = mapOf(
                "orderNumber" to orderNumber,
                "customerId" to uid,
                "customerName" to customerName.trim().take(80),
                "customerPhone" to customerPhone.trim().take(20),
                "addressSnapshot" to addressSnapshotMap,
                "deliveryInstructions" to deliveryInstructions.trim().take(300).ifBlank { null },
                "orderItemsSnapshot" to itemsSnapshotList,
                "subtotal" to bill.subtotal,
                "discount" to bill.couponDiscount,
                "deliveryFee" to bill.deliveryFee,
                "grandTotal" to bill.grandTotal,
                "couponCode" to coupon?.code?.take(32),
                "paymentMethod" to paymentMethod.code,
                "paymentStatus" to initialPaymentStatus,
                "orderStatus" to OrderStatus.PENDING.label,
                "estimatedDeliveryMinutes" to estimatedDeliveryMinutes.take(40),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            val batch = db.batch()
            batch.set(orderRef, orderPayload)

            verifiedItems.forEach { item ->
                val oiRef = db.collection("order_items").document()
                batch.set(
                    oiRef,
                    mapOf(
                        "orderId" to orderRef.id,
                        "customerId" to uid,
                        "productId" to item.productId,
                        "productName" to item.productName,
                        "unit" to item.unit,
                        "sellingPrice" to item.sellingPrice,
                        "originalPrice" to item.originalPrice,
                        "quantity" to item.quantity,
                        "lineTotal" to DeliveryCalculator.roundRupees(item.sellingPrice * item.quantity),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            val paymentRef = db.collection("payments").document()
            batch.set(
                paymentRef,
                mapOf(
                    "orderId" to orderRef.id,
                    "customerId" to uid,
                    "paymentMethod" to paymentMethod.code,
                    "paymentStatus" to initialPaymentStatus,
                    "amount" to bill.grandTotal,
                    "gatewayRef" to gatewayRef?.take(120),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
            )

            val historyRef = db.collection("order_status_history").document()
            batch.set(
                historyRef,
                mapOf(
                    "orderId" to orderRef.id,
                    "customerId" to uid,
                    "status" to OrderStatus.PENDING.label,
                    "note" to "Order placed by customer (${paymentMethod.displayName})",
                    "changedBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            val notifRef = db.collection("notifications").document()
            batch.set(
                notifRef,
                mapOf(
                    "userId" to uid,
                    "orderId" to orderRef.id,
                    "title" to "Order $orderNumber Placed",
                    "message" to "We received your order. Estimated delivery: $estimatedDeliveryMinutes.",
                    "read" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            cartItems.forEach { cartItem ->
                if (cartItem.id.isNotBlank()) {
                    batch.delete(db.collection("cart_items").document(cartItem.id))
                }
            }

            batch.commit().await()
            Result.success(orderRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, orderRef.path)
            Result.failure(e)
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
}
