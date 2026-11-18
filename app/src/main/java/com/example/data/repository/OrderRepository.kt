package com.example.data.repository

import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.OrderStatusHistory
import com.example.data.model.Product
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

class OrderRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeCustomerOrders(userId: String): Flow<List<Order>> = flow {
        val path = "orders"
        emitAll(
            db.collection(path)
                .whereEqualTo("customerId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Order::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
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

    fun observeCustomerOrderHistory(userId: String): Flow<List<OrderStatusHistory>> = flow {
        val path = "order_status_history"
        emitAll(
            db.collection(path)
                .whereEqualTo("customerId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(OrderStatusHistory::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun cancelOrderByCustomer(order: Order, reason: String): Result<Unit> {
        val path = "orders/${order.id}"
        return try {
            val uid = requireUserId()
            check(order.customerId == uid) { "Cannot cancel another user's order." }
            require(order.orderStatus == OrderStatus.PENDING.label || order.orderStatus == OrderStatus.CONFIRMED.label) {
                "Order can no longer be cancelled once packed or dispatched."
            }
            val batch = db.batch()
            val orderRef = db.collection("orders").document(order.id)
            batch.update(
                orderRef,
                mapOf(
                    "orderStatus" to OrderStatus.CANCELLED.label,
                    "cancelReason" to reason.trim().ifBlank { "Cancelled by customer" }.take(200),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val historyRef = db.collection("order_status_history").document()
            batch.set(
                historyRef,
                mapOf(
                    "orderId" to order.id,
                    "customerId" to uid,
                    "status" to OrderStatus.CANCELLED.label,
                    "note" to reason.trim().ifBlank { "Cancelled by customer" }.take(200),
                    "changedBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun reorderItems(
        order: Order,
        liveProducts: Map<String, Product>,
        cartRepo: CartCheckoutRepository
    ): Result<Int> {
        return try {
            requireUserId()
            var addedCount = 0
            order.orderItemsSnapshot.forEach { snapshotItem ->
                val product = liveProducts[snapshotItem.productId]
                if (product != null && product.active && product.available && product.availableToSell > 0) {
                    val qty = snapshotItem.quantity.coerceAtMost(product.availableToSell)
                    if (qty > 0 && cartRepo.addOrUpdateCartItem(product, qty).isSuccess) {
                        addedCount++
                    }
                }
            }
            Result.success(addedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "cart_items")
            Result.failure(e)
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
}
