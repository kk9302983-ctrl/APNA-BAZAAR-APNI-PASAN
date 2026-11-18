package com.example.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Address
import com.example.data.model.AppNotification
import com.example.data.model.AppSettings
import com.example.data.model.Cart
import com.example.data.model.CartItem
import com.example.data.model.Category
import com.example.data.model.Coupon
import com.example.data.model.DeliveryZone
import com.example.data.model.Order
import com.example.data.model.OrderStatusHistory
import com.example.data.model.PaymentMethod
import com.example.data.model.Product
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.AuthRepository
import com.example.data.repository.CartCheckoutRepository
import com.example.data.repository.OrderRepository
import com.example.data.repository.StorefrontRepository
import com.example.domain.BillSummary
import com.example.domain.CodPaymentGateway
import com.example.domain.DeliveryCalculator
import com.example.domain.ModularRazorpayGateway
import com.example.domain.PaymentInitiationResult
import com.example.domain.ServiceabilityResult
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ProductSortOption(val label: String) {
    RELEVANCE("Relevance"),
    PRICE_LOW_HIGH("Price: Low to High"),
    PRICE_HIGH_LOW("Price: High to Low"),
    DISCOUNT_HIGH("Highest Discount")
}

data class CatalogFilterState(
    val query: String = "",
    val selectedCategoryId: String? = null,
    val inStockOnly: Boolean = false,
    val sortOption: ProductSortOption = ProductSortOption.RELEVANCE
)

class CustomerViewModel(
    private val currentUserId: String,
    private val authRepository: AuthRepository,
    private val storefrontRepository: StorefrontRepository,
    private val cartCheckoutRepository: CartCheckoutRepository,
    private val orderRepository: OrderRepository
) : ViewModel() {

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _filter = MutableStateFlow(CatalogFilterState())
    val filter: StateFlow<CatalogFilterState> = _filter.asStateFlow()

    private val _selectedAddressId = MutableStateFlow<String?>(null)
    val selectedAddressId: StateFlow<String?> = _selectedAddressId.asStateFlow()

    private val _isPlacingOrder = MutableStateFlow(false)
    val isPlacingOrder: StateFlow<Boolean> = _isPlacingOrder.asStateFlow()

    private val _lastConfirmedOrderId = MutableStateFlow<String?>(null)
    val lastConfirmedOrderId: StateFlow<String?> = _lastConfirmedOrderId.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.ensureUserProfile()
            _isAdmin.value = authRepository.checkIsAdmin()
        }
    }

    val userProfile: StateFlow<UserProfile?> = authRepository.observeUserProfile(currentUserId)
        .catch { error ->
            val fs = unwrapFirestoreException(error)
            if (fs == null && error is CancellationException) throw error
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val categoriesState: StateFlow<UiState<List<Category>>> = storefrontRepository.observeActiveCategories()
        .asUiState("Failed to load categories")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val productsState: StateFlow<UiState<List<Product>>> = storefrontRepository.observeActiveProducts()
        .asUiState("Failed to load products")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val appSettings: StateFlow<AppSettings> = storefrontRepository.observeAppSettings()
        .catch { error ->
            val fs = unwrapFirestoreException(error)
            if (fs == null && error is CancellationException) throw error
            emit(AppSettings())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppSettings())

    val deliveryZones: StateFlow<List<DeliveryZone>> = storefrontRepository.observeActiveDeliveryZones()
        .catch { error ->
            val fs = unwrapFirestoreException(error)
            if (fs == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val coupons: StateFlow<List<Coupon>> = storefrontRepository.observeActiveCoupons()
        .catch { error ->
            val fs = unwrapFirestoreException(error)
            if (fs == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val cartItems: StateFlow<List<CartItem>> = cartCheckoutRepository.observeCartItems(currentUserId)
        .catch { error ->
            val fs = unwrapFirestoreException(error)
            if (fs == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val cartMeta: StateFlow<Cart?> = cartCheckoutRepository.observeCartMeta(currentUserId)
        .catch { error ->
            val fs = unwrapFirestoreException(error)
            if (fs == null && error is CancellationException) throw error
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val addresses: StateFlow<List<Address>> = cartCheckoutRepository.observeAddresses(currentUserId)
        .catch { error ->
            val fs = unwrapFirestoreException(error)
            if (fs == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val ordersState: StateFlow<UiState<List<Order>>> = orderRepository.observeCustomerOrders(currentUserId)
        .asUiState("Failed to load orders")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val orderHistory: StateFlow<List<OrderStatusHistory>> = orderRepository.observeCustomerOrderHistory(currentUserId)
        .catch { error ->
            val fs = unwrapFirestoreException(error)
            if (fs == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val notifications: StateFlow<List<AppNotification>> = storefrontRepository.observeUserNotifications(currentUserId)
        .catch { error ->
            val fs = unwrapFirestoreException(error)
            if (fs == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeAddress: StateFlow<Address?> = combine(addresses, userProfile, _selectedAddressId) { list, profile, localId ->
        val targetId = localId ?: profile?.selectedAddressId
        list.firstOrNull { it.id == targetId } ?: list.firstOrNull { it.defaultAddress } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val serviceability: StateFlow<ServiceabilityResult> = combine(activeAddress, appSettings, deliveryZones) { addr, settings, zones ->
        if (addr == null) {
            ServiceabilityResult(
                serviceable = settings.serviceAvailable,
                distanceKm = 0.0,
                matchedZone = zones.firstOrNull { it.active },
                deliveryFee = settings.defaultDeliveryFee,
                minOrderAmount = settings.minOrderAmount,
                estimatedMinutes = settings.estimatedDeliveryText,
                reason = if (settings.serviceAvailable) {
                    "Select a delivery address within ${DeliveryCalculator.formatKm(settings.serviceRadiusKm)}."
                } else {
                    "Store is currently closed (${settings.operatingHours})."
                }
            )
        } else {
            DeliveryCalculator.evaluateServiceability(addr.latitude, addr.longitude, settings, zones)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        ServiceabilityResult(true, 0.0, null, 20.0, 99.0, "30-60 mins", "")
    )

    val appliedCoupon: StateFlow<Coupon?> = combine(cartMeta, coupons) { meta, list ->
        val code = meta?.couponCode?.trim()?.uppercase()
        if (code.isNullOrBlank()) null else list.firstOrNull { it.code.equals(code, ignoreCase = true) && it.active }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val billSummary: StateFlow<BillSummary> = combine(cartItems, appliedCoupon, serviceability) { items, coupon, svc ->
        DeliveryCalculator.calculateBill(
            items = items,
            coupon = coupon,
            deliveryFee = svc.deliveryFee,
            minOrderAmount = svc.minOrderAmount
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        BillSummary(0.0, 0.0, 0.0, 0.0, 0.0, 99.0, false)
    )

    val filteredProducts: StateFlow<List<Product>> = combine(productsState, _filter) { state, f ->
        val all = (state as? UiState.Success)?.data ?: emptyList()
        val matched = all.filter { product ->
            val q = f.query.trim()
            val matchesQuery = q.isEmpty() ||
                product.productName.contains(q, ignoreCase = true) ||
                product.brand.contains(q, ignoreCase = true) ||
                product.categoryName.contains(q, ignoreCase = true)
            val matchesCategory = f.selectedCategoryId == null || product.categoryId == f.selectedCategoryId
            val matchesStock = !f.inStockOnly || (product.available && product.availableToSell > 0L)
            matchesQuery && matchesCategory && matchesStock
        }
        when (f.sortOption) {
            ProductSortOption.RELEVANCE -> matched
            ProductSortOption.PRICE_LOW_HIGH -> matched.sortedBy { it.sellingPrice }
            ProductSortOption.PRICE_HIGH_LOW -> matched.sortedByDescending { it.sellingPrice }
            ProductSortOption.DISCOUNT_HIGH -> matched.sortedByDescending { it.discountPercent }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun updateSearchQuery(query: String) {
        _filter.value = _filter.value.copy(query = query)
    }

    fun selectCategory(categoryId: String?) {
        _filter.value = _filter.value.copy(selectedCategoryId = categoryId)
    }

    fun toggleInStockOnly() {
        _filter.value = _filter.value.copy(inStockOnly = !_filter.value.inStockOnly)
    }

    fun setSortOption(option: ProductSortOption) {
        _filter.value = _filter.value.copy(sortOption = option)
    }

    fun clearMessages() {
        _actionMessage.value = null
        _errorMessage.value = null
    }

    fun clearConfirmedOrder() {
        _lastConfirmedOrderId.value = null
    }

    fun addOrUpdateCart(product: Product, deltaQty: Long) {
        viewModelScope.launch {
            val res = cartCheckoutRepository.addOrUpdateCartItem(product, deltaQty)
            res.onFailure { _errorMessage.value = it.localizedMessage ?: "Unable to update cart" }
        }
    }

    fun removeCartItem(cartItemId: String) {
        viewModelScope.launch {
            cartCheckoutRepository.removeCartItem(cartItemId)
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Unable to remove item" }
        }
    }

    fun applyCoupon(code: String) {
        viewModelScope.launch {
            val trimmed = code.trim().uppercase()
            if (trimmed.isEmpty()) {
                cartCheckoutRepository.applyCouponToCart(currentUserId, "")
                _actionMessage.value = "Coupon removed"
                return@launch
            }
            val found = coupons.value.firstOrNull { it.code.equals(trimmed, ignoreCase = true) && it.active }
            if (found == null) {
                _errorMessage.value = "Invalid or inactive coupon code."
                return@launch
            }
            if (found.expiryEpochMs in 1 until System.currentTimeMillis()) {
                _errorMessage.value = "This coupon has expired."
                return@launch
            }
            val subtotal = cartItems.value.sumOf { it.sellingPrice * it.quantity }
            if (subtotal < found.minOrderValue) {
                _errorMessage.value = "Add ${DeliveryCalculator.formatRupees(found.minOrderValue - subtotal)} more to use ${found.code}."
                return@launch
            }
            cartCheckoutRepository.applyCouponToCart(currentUserId, found.code)
                .onSuccess { _actionMessage.value = "Coupon ${found.code} applied!" }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to apply coupon" }
        }
    }

    fun selectAddress(address: Address) {
        _selectedAddressId.value = address.id
        viewModelScope.launch {
            val name = userProfile.value?.name?.takeIf { it.isNotBlank() } ?: address.recipientName.ifBlank { "Customer" }
            val phone = userProfile.value?.phone ?: address.phone
            authRepository.updateProfile(currentUserId, name, phone, selectedAddressId = address.id)
        }
    }

    fun saveAddress(address: Address, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            val eval = DeliveryCalculator.evaluateServiceability(
                address.latitude,
                address.longitude,
                appSettings.value,
                deliveryZones.value
            )
            val evaluated = address.copy(serviceable = eval.serviceable)
            cartCheckoutRepository.saveAddress(evaluated)
                .onSuccess { savedId ->
                    _selectedAddressId.value = savedId
                    _actionMessage.value = if (eval.serviceable) {
                        "Address saved — ${eval.reason}"
                    } else {
                        "Address saved (outside delivery area: ${eval.reason})"
                    }
                    onSaved()
                }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to save address" }
        }
    }

    fun deleteAddress(addressId: String) {
        viewModelScope.launch {
            cartCheckoutRepository.deleteAddress(addressId)
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to delete address" }
        }
    }

    fun placeOrder(
        customerName: String,
        customerPhone: String,
        deliveryInstructions: String,
        paymentMethod: PaymentMethod
    ) {
        if (_isPlacingOrder.value) return // Duplicate submission guard
        val addr = activeAddress.value
        if (addr == null) {
            _errorMessage.value = "Please select or add a delivery address first."
            return
        }
        val svc = serviceability.value
        if (!svc.serviceable) {
            _errorMessage.value = svc.reason.ifBlank { "Delivery is unavailable at your selected location." }
            return
        }
        val currentProducts = (productsState.value as? UiState.Success)?.data?.associateBy { it.id } ?: emptyMap()
        if (cartItems.value.isEmpty()) {
            _errorMessage.value = "Your cart is empty."
            return
        }

        _isPlacingOrder.value = true
        viewModelScope.launch {
            try {
                val gateway = when (paymentMethod) {
                    PaymentMethod.COD -> CodPaymentGateway()
                    PaymentMethod.RAZORPAY -> ModularRazorpayGateway(isGatewayConfigured = false)
                }
                val tempNumber = "TMP-${System.currentTimeMillis()}"
                val bill = billSummary.value
                val paymentInit = gateway.initiate(tempNumber, bill.grandTotal)
                if (paymentInit is PaymentInitiationResult.Failed) {
                    _errorMessage.value = paymentInit.reason
                    return@launch
                }
                val ready = paymentInit as PaymentInitiationResult.Ready

                val res = cartCheckoutRepository.placeOrder(
                    customerName = customerName,
                    customerPhone = customerPhone,
                    address = addr.copy(serviceable = svc.serviceable),
                    deliveryInstructions = deliveryInstructions,
                    cartItems = cartItems.value,
                    liveProducts = currentProducts,
                    coupon = appliedCoupon.value,
                    deliveryFee = svc.deliveryFee,
                    minOrderAmount = svc.minOrderAmount,
                    estimatedDeliveryMinutes = svc.estimatedMinutes,
                    paymentMethod = ready.paymentMethod,
                    gatewayRef = ready.gatewayRef
                )
                res.onSuccess { orderId ->
                    cartCheckoutRepository.applyCouponToCart(currentUserId, "")
                    _lastConfirmedOrderId.value = orderId
                    _actionMessage.value = ready.notice
                }.onFailure { err ->
                    _errorMessage.value = err.localizedMessage ?: "Failed to place order"
                }
            } finally {
                _isPlacingOrder.value = false
            }
        }
    }

    fun cancelOrder(order: Order, reason: String) {
        viewModelScope.launch {
            orderRepository.cancelOrderByCustomer(order, reason)
                .onSuccess { _actionMessage.value = "Order ${order.orderNumber} cancelled." }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Unable to cancel order" }
        }
    }

    fun reorder(order: Order, onAddedToCart: () -> Unit) {
        val currentProducts = (productsState.value as? UiState.Success)?.data?.associateBy { it.id } ?: emptyMap()
        viewModelScope.launch {
            orderRepository.reorderItems(order, currentProducts, cartCheckoutRepository)
                .onSuccess { count ->
                    if (count > 0) {
                        _actionMessage.value = "$count item(s) added to your cart."
                        onAddedToCart()
                    } else {
                        _errorMessage.value = "None of the items from this order are currently in stock."
                    }
                }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Reorder failed" }
        }
    }

    fun updateProfile(name: String, phone: String, language: String = "en") {
        viewModelScope.launch {
            authRepository.updateProfile(
                userId = currentUserId,
                name = name,
                phone = phone,
                selectedAddressId = _selectedAddressId.value,
                language = language
            ).onSuccess {
                _actionMessage.value = "Profile updated"
            }.onFailure {
                _errorMessage.value = it.localizedMessage ?: "Failed to update profile"
            }
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch { storefrontRepository.markNotificationRead(id) }
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
