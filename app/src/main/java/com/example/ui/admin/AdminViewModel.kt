package com.example.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppSettings
import com.example.data.model.Category
import com.example.data.model.Coupon
import com.example.data.model.DeliveryZone
import com.example.data.model.InventoryLog
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.OrderStatusHistory
import com.example.data.model.Product
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.AdminRepository
import com.example.data.repository.StorefrontRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class AdminDashboardMetrics(
    val totalSales: Double = 0.0,
    val todaySales: Double = 0.0,
    val totalOrders: Int = 0,
    val todayOrders: Int = 0,
    val pendingOrders: Int = 0,
    val completedOrders: Int = 0,
    val cancelledOrders: Int = 0,
    val totalCustomers: Int = 0,
    val activeProducts: Int = 0,
    val lowStockProducts: Int = 0
)

class AdminViewModel(
    private val adminRepository: AdminRepository,
    private val storefrontRepository: StorefrontRepository
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val ordersState: StateFlow<UiState<List<Order>>> = adminRepository.observeAllOrders()
        .asUiState("Failed to load orders")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val products: StateFlow<List<Product>> = adminRepository.observeAllProducts()
        .safeList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val categories: StateFlow<List<Category>> = adminRepository.observeAllCategories()
        .safeList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val customers: StateFlow<List<UserProfile>> = adminRepository.observeAllCustomers()
        .safeList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val coupons: StateFlow<List<Coupon>> = adminRepository.observeAllCoupons()
        .safeList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val deliveryZones: StateFlow<List<DeliveryZone>> = adminRepository.observeAllDeliveryZones()
        .safeList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val inventoryLogs: StateFlow<List<InventoryLog>> = adminRepository.observeInventoryLogs()
        .safeList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val orderHistory: StateFlow<List<OrderStatusHistory>> = adminRepository.observeAllOrderHistory()
        .safeList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val appSettings: StateFlow<AppSettings> = storefrontRepository.observeAppSettings()
        .catch { err ->
            val fs = unwrapFirestoreException(err)
            if (fs == null && err is CancellationException) throw err
            emit(AppSettings())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppSettings())

    fun computeMetrics(
        orders: List<Order>,
        productList: List<Product>,
        customerList: List<UserProfile>
    ): AdminDashboardMetrics {
        val startOfDaySec = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis / 1000L

        val nonCancelled = orders.filter { it.orderStatus != OrderStatus.CANCELLED.label }
        val todayNonCancelled = nonCancelled.filter { (it.createdAt?.seconds ?: 0L) >= startOfDaySec }
        val todayAll = orders.filter { (it.createdAt?.seconds ?: 0L) >= startOfDaySec }

        return AdminDashboardMetrics(
            totalSales = nonCancelled.sumOf { it.grandTotal },
            todaySales = todayNonCancelled.sumOf { it.grandTotal },
            totalOrders = orders.size,
            todayOrders = todayAll.size,
            pendingOrders = orders.count { it.orderStatus == OrderStatus.PENDING.label },
            completedOrders = orders.count { it.orderStatus == OrderStatus.DELIVERED.label },
            cancelledOrders = orders.count { it.orderStatus == OrderStatus.CANCELLED.label },
            totalCustomers = customerList.size,
            activeProducts = productList.count { it.active },
            lowStockProducts = productList.count { it.active && (it.lowStock || it.outOfStock) }
        )
    }

    fun clearBanners() {
        _message.value = null
        _error.value = null
    }

    fun seedInitialCatalog() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                adminRepository.seedInitialStoreCatalog()
                    .onSuccess { _message.value = "10 categories, starter products, delivery zone, and APNA50 coupon initialized!" }
                    .onFailure { _error.value = it.localizedMessage ?: "Failed to seed store catalog" }
            } finally {
                _busy.value = false
            }
        }
    }

    fun confirmOrder(order: Order) {
        viewModelScope.launch {
            adminRepository.confirmOrderAndReserveStock(order)
                .onSuccess { _message.value = "Order ${order.orderNumber} confirmed & stock reserved." }
                .onFailure { _error.value = it.localizedMessage ?: "Failed to confirm order" }
        }
    }

    fun advanceOrderStatus(order: Order, nextStatus: OrderStatus, note: String = "", markPaid: Boolean? = null) {
        viewModelScope.launch {
            adminRepository.updateOrderStatus(order, nextStatus, note, markPaid)
                .onSuccess { _message.value = "Order ${order.orderNumber} updated to ${nextStatus.label}." }
                .onFailure { _error.value = it.localizedMessage ?: "Failed to update order" }
        }
    }

    fun adjustStock(product: Product, delta: Long, reason: String) {
        viewModelScope.launch {
            adminRepository.adjustProductStock(product, delta, reason)
                .onSuccess { _message.value = "Stock adjusted for ${product.productName}." }
                .onFailure { _error.value = it.localizedMessage ?: "Failed to adjust stock" }
        }
    }

    fun saveCategory(category: Category) {
        viewModelScope.launch {
            adminRepository.saveCategory(category)
                .onSuccess { _message.value = "Category '${category.name}' saved." }
                .onFailure { _error.value = it.localizedMessage ?: "Failed to save category" }
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            adminRepository.deleteCategory(categoryId)
                .onSuccess { _message.value = "Category deleted." }
                .onFailure { _error.value = it.localizedMessage ?: "Failed to delete category" }
        }
    }

    fun saveProduct(product: Product) {
        viewModelScope.launch {
            adminRepository.saveProduct(product)
                .onSuccess { _message.value = "Product '${product.productName}' saved." }
                .onFailure { _error.value = it.localizedMessage ?: "Failed to save product" }
        }
    }

    fun saveCoupon(coupon: Coupon) {
        viewModelScope.launch {
            adminRepository.saveCoupon(coupon)
                .onSuccess { _message.value = "Coupon '${coupon.code}' saved." }
                .onFailure { _error.value = it.localizedMessage ?: "Failed to save coupon" }
        }
    }

    fun saveDeliveryZone(zone: DeliveryZone) {
        viewModelScope.launch {
            adminRepository.saveDeliveryZone(zone)
                .onSuccess { _message.value = "Delivery zone '${zone.zoneName}' saved." }
                .onFailure { _error.value = it.localizedMessage ?: "Failed to save delivery zone" }
        }
    }

    fun saveSettings(settings: AppSettings) {
        viewModelScope.launch {
            adminRepository.saveAppSettings(settings)
                .onSuccess { _message.value = "Delivery & store settings saved." }
                .onFailure { _error.value = it.localizedMessage ?: "Failed to save settings" }
        }
    }

    private fun <T> Flow<List<T>>.safeList(): Flow<List<T>> =
        this.catch { error ->
            val fs = unwrapFirestoreException(error)
            if (fs == null && error is CancellationException) throw error
            emit(emptyList())
        }

    private fun <T> Flow<T>.asUiState(fallbackMsg: String): Flow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val fs = unwrapFirestoreException(error)
                if (fs == null && error is CancellationException) throw error
                emit(UiState.Error((fs ?: error).message ?: fallbackMsg))
            }

    private fun unwrapFirestoreException(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
}
