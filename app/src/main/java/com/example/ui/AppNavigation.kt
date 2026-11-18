package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.data.model.UiState
import com.example.data.repository.AdminRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.CartCheckoutRepository
import com.example.data.repository.OrderRepository
import com.example.data.repository.StorefrontRepository
import com.example.ui.admin.AdminPortalScreen
import com.example.ui.admin.AdminViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.customer.CartCheckoutScreen
import com.example.ui.customer.CatalogScreen
import com.example.ui.customer.CustomerHomeScreen
import com.example.ui.customer.CustomerViewModel
import com.example.ui.customer.LocationSelectionScreen
import com.example.ui.customer.OrdersScreen
import com.example.ui.customer.ProductDetailScreen
import com.example.ui.customer.ProfileScreen
import com.example.ui.theme.EmeraldDark
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

object AppRoutes {
    const val ROUTE_HOME = "home"
    const val ROUTE_CATALOG = "catalog"
    const val ROUTE_CART = "cart"
    const val ROUTE_ORDERS = "orders"
    const val ROUTE_PROFILE = "profile"
    const val ROUTE_LOCATION = "location"
    const val ROUTE_PRODUCT_DETAIL = "product_detail"
    const val ROUTE_ADMIN = "admin"
}

private data class CustomerNavTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val db = remember(context) {
        FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id))
    }
    val authRepository = remember(db) { AuthRepository(db) }
    val currentUser by authRepository.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(
            authRepository = authRepository,
            onAuthenticated = {}
        )
    } else {
        key(user.uid) {
            AuthenticatedAppShell(
                currentUserId = user.uid,
                userEmail = user.email,
                authRepository = authRepository
            )
        }
    }
}

@Composable
private fun AuthenticatedAppShell(
    currentUserId: String,
    userEmail: String?,
    authRepository: AuthRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var showNotificationsDialog by remember { mutableStateOf(false) }

    val customerViewModel: CustomerViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                CustomerViewModel(
                    currentUserId = currentUserId,
                    authRepository = AuthRepository(db),
                    storefrontRepository = StorefrontRepository(db),
                    cartCheckoutRepository = CartCheckoutRepository(db),
                    orderRepository = OrderRepository(db)
                )
            }
        }
    )

    val isAdmin by customerViewModel.isAdmin.collectAsStateWithLifecycle()
    val userProfile by customerViewModel.userProfile.collectAsStateWithLifecycle()
    val categoriesState by customerViewModel.categoriesState.collectAsStateWithLifecycle()
    val productsState by customerViewModel.productsState.collectAsStateWithLifecycle()
    val filteredProducts by customerViewModel.filteredProducts.collectAsStateWithLifecycle()
    val filter by customerViewModel.filter.collectAsStateWithLifecycle()
    val appSettings by customerViewModel.appSettings.collectAsStateWithLifecycle()
    val deliveryZones by customerViewModel.deliveryZones.collectAsStateWithLifecycle()
    val coupons by customerViewModel.coupons.collectAsStateWithLifecycle()
    val appliedCoupon by customerViewModel.appliedCoupon.collectAsStateWithLifecycle()
    val cartItems by customerViewModel.cartItems.collectAsStateWithLifecycle()
    val addresses by customerViewModel.addresses.collectAsStateWithLifecycle()
    val activeAddress by customerViewModel.activeAddress.collectAsStateWithLifecycle()
    val serviceability by customerViewModel.serviceability.collectAsStateWithLifecycle()
    val billSummary by customerViewModel.billSummary.collectAsStateWithLifecycle()
    val ordersState by customerViewModel.ordersState.collectAsStateWithLifecycle()
    val orderHistory by customerViewModel.orderHistory.collectAsStateWithLifecycle()
    val notifications by customerViewModel.notifications.collectAsStateWithLifecycle()
    val isPlacingOrder by customerViewModel.isPlacingOrder.collectAsStateWithLifecycle()
    val lastConfirmedOrderId by customerViewModel.lastConfirmedOrderId.collectAsStateWithLifecycle()
    val actionMessage by customerViewModel.actionMessage.collectAsStateWithLifecycle()
    val errorMessage by customerViewModel.errorMessage.collectAsStateWithLifecycle()

    LaunchedEffect(actionMessage, errorMessage) {
        val text = errorMessage ?: actionMessage
        if (!text.isNullOrBlank()) {
            snackbarHostState.showSnackbar(text)
            customerViewModel.clearMessages()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val topLevelTabs = remember {
        listOf(
            CustomerNavTab(AppRoutes.ROUTE_HOME, "Home", Icons.Filled.Home),
            CustomerNavTab(AppRoutes.ROUTE_CATALOG, "Categories", Icons.Filled.Category),
            CustomerNavTab(AppRoutes.ROUTE_CART, "Cart", Icons.Filled.ShoppingCart),
            CustomerNavTab(AppRoutes.ROUTE_ORDERS, "Orders", Icons.Filled.ReceiptLong),
            CustomerNavTab(AppRoutes.ROUTE_PROFILE, "Profile", Icons.Filled.Person)
        )
    }

    val showBottomNav = currentRoute in topLevelTabs.map { it.route }
    val cartCount = cartItems.sumOf { it.quantity }.toInt()

    val allProducts = (productsState as? UiState.Success)?.data ?: emptyList()
    val productsMap = remember(allProducts) { allProducts.associateBy { it.id } }
    val allCategories = (categoriesState as? UiState.Success)?.data ?: emptyList()
    val allOrders = (ordersState as? UiState.Success)?.data ?: emptyList()
    val confirmedOrder = remember(lastConfirmedOrderId, allOrders) {
        lastConfirmedOrderId?.let { oid -> allOrders.firstOrNull { it.id == oid } }
    }

    fun navigateTopTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp && showBottomNav

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (showBottomNav && !useRail) {
                    NavigationBar {
                        topLevelTabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateTopTab(tab.route) },
                                icon = {
                                    if (tab.route == AppRoutes.ROUTE_CART && cartCount > 0) {
                                        BadgedBox(badge = { Badge { Text(cartCount.toString()) } }) {
                                            Icon(tab.icon, contentDescription = tab.label)
                                        }
                                    } else {
                                        Icon(tab.icon, contentDescription = tab.label)
                                    }
                                },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (useRail) {
                    NavigationRail {
                        topLevelTabs.forEach { tab ->
                            NavigationRailItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateTopTab(tab.route) },
                                icon = {
                                    if (tab.route == AppRoutes.ROUTE_CART && cartCount > 0) {
                                        BadgedBox(badge = { Badge { Text(cartCount.toString()) } }) {
                                            Icon(tab.icon, contentDescription = tab.label)
                                        }
                                    } else {
                                        Icon(tab.icon, contentDescription = tab.label)
                                    }
                                },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = AppRoutes.ROUTE_HOME
                    ) {
                        composable(AppRoutes.ROUTE_HOME) {
                            CustomerHomeScreen(
                                activeAddress = activeAddress,
                                serviceability = serviceability,
                                appSettings = appSettings,
                                categoriesState = categoriesState,
                                productsState = productsState,
                                cartItems = cartItems,
                                notifications = notifications,
                                onAddressClick = { navController.navigate(AppRoutes.ROUTE_LOCATION) },
                                onSearchClick = { navigateTopTab(AppRoutes.ROUTE_CATALOG) },
                                onCategoryClick = { cat ->
                                    customerViewModel.selectCategory(cat.id)
                                    navigateTopTab(AppRoutes.ROUTE_CATALOG)
                                },
                                onProductClick = { prod ->
                                    navController.navigate("${AppRoutes.ROUTE_PRODUCT_DETAIL}/${prod.id}")
                                },
                                onUpdateCart = customerViewModel::addOrUpdateCart,
                                onOpenCatalog = { navigateTopTab(AppRoutes.ROUTE_CATALOG) },
                                onNotificationClick = { showNotificationsDialog = true }
                            )
                        }

                        composable(AppRoutes.ROUTE_CATALOG) {
                            CatalogScreen(
                                categories = allCategories,
                                products = filteredProducts,
                                filter = filter,
                                cartItems = cartItems,
                                onQueryChange = customerViewModel::updateSearchQuery,
                                onSelectCategory = customerViewModel::selectCategory,
                                onToggleInStock = customerViewModel::toggleInStockOnly,
                                onSelectSort = customerViewModel::setSortOption,
                                onProductClick = { prod ->
                                    navController.navigate("${AppRoutes.ROUTE_PRODUCT_DETAIL}/${prod.id}")
                                },
                                onUpdateCart = customerViewModel::addOrUpdateCart
                            )
                        }

                        composable(
                            route = "${AppRoutes.ROUTE_PRODUCT_DETAIL}/{productId}",
                            arguments = listOf(navArgument("productId") { type = NavType.StringType })
                        ) { entry ->
                            val productId = entry.arguments?.getString("productId") ?: ""
                            val prod = productsMap[productId]
                            if (prod != null) {
                                val related = allProducts.filter {
                                    it.id != prod.id && it.categoryId == prod.categoryId
                                }.take(6)
                                ProductDetailScreen(
                                    product = prod,
                                    relatedProducts = related,
                                    cartItems = cartItems,
                                    onBack = { navController.popBackStack() },
                                    onAddQuantityToCart = customerViewModel::addOrUpdateCart,
                                    onSelectRelated = { rel ->
                                        navController.navigate("${AppRoutes.ROUTE_PRODUCT_DETAIL}/${rel.id}")
                                    }
                                )
                            } else {
                                LaunchedEffect(Unit) { navController.popBackStack() }
                            }
                        }

                        composable(AppRoutes.ROUTE_LOCATION) {
                            LocationSelectionScreen(
                                addresses = addresses,
                                activeAddress = activeAddress,
                                appSettings = appSettings,
                                deliveryZones = deliveryZones,
                                defaultName = userProfile?.name ?: "",
                                defaultPhone = userProfile?.phone ?: "",
                                onSelectAddress = customerViewModel::selectAddress,
                                onSaveAddress = customerViewModel::saveAddress,
                                onDeleteAddress = customerViewModel::deleteAddress,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(AppRoutes.ROUTE_CART) {
                            CartCheckoutScreen(
                                cartItems = cartItems,
                                liveProducts = productsMap,
                                coupons = coupons,
                                appliedCoupon = appliedCoupon,
                                bill = billSummary,
                                activeAddress = activeAddress,
                                serviceability = serviceability,
                                defaultName = userProfile?.name ?: "",
                                defaultPhone = userProfile?.phone ?: "",
                                isPlacingOrder = isPlacingOrder,
                                confirmedOrder = confirmedOrder,
                                onBrowseStore = { navigateTopTab(AppRoutes.ROUTE_CATALOG) },
                                onUpdateCart = customerViewModel::addOrUpdateCart,
                                onRemoveItem = customerViewModel::removeCartItem,
                                onApplyCoupon = customerViewModel::applyCoupon,
                                onSelectAddress = { navController.navigate(AppRoutes.ROUTE_LOCATION) },
                                onPlaceOrder = customerViewModel::placeOrder,
                                onViewOrdersAfterConfirm = {
                                    customerViewModel.clearConfirmedOrder()
                                    navigateTopTab(AppRoutes.ROUTE_ORDERS)
                                },
                                onContinueShoppingAfterConfirm = {
                                    customerViewModel.clearConfirmedOrder()
                                    navigateTopTab(AppRoutes.ROUTE_HOME)
                                }
                            )
                        }

                        composable(AppRoutes.ROUTE_ORDERS) {
                            OrdersScreen(
                                ordersState = ordersState,
                                orderHistory = orderHistory,
                                onBrowseStore = { navigateTopTab(AppRoutes.ROUTE_CATALOG) },
                                onCancelOrder = customerViewModel::cancelOrder,
                                onReorder = { order ->
                                    customerViewModel.reorder(order) {
                                        navigateTopTab(AppRoutes.ROUTE_CART)
                                    }
                                }
                            )
                        }

                        composable(AppRoutes.ROUTE_PROFILE) {
                            ProfileScreen(
                                profile = userProfile,
                                userEmail = userEmail,
                                isAdmin = isAdmin,
                                appSettings = appSettings,
                                onUpdateProfile = customerViewModel::updateProfile,
                                onOpenAddresses = { navController.navigate(AppRoutes.ROUTE_LOCATION) },
                                onOpenOrders = { navigateTopTab(AppRoutes.ROUTE_ORDERS) },
                                onOpenAdminPortal = {
                                    if (isAdmin) {
                                        navController.navigate(AppRoutes.ROUTE_ADMIN)
                                    }
                                },
                                onSignOut = {
                                    scope.launch { authRepository.signOut(context) }
                                }
                            )
                        }

                        composable(AppRoutes.ROUTE_ADMIN) {
                            if (isAdmin) {
                                val adminViewModel: AdminViewModel = viewModel(
                                    key = "admin_$currentUserId",
                                    factory = viewModelFactory {
                                        initializer {
                                            val app = checkNotNull(this[APPLICATION_KEY])
                                            val databaseId = app.getString(R.string.firestore_database_id)
                                            val db = FirebaseFirestore.getInstance(databaseId)
                                            AdminViewModel(
                                                adminRepository = AdminRepository(db),
                                                storefrontRepository = StorefrontRepository(db)
                                            )
                                        }
                                    }
                                )
                                AdminPortalScreen(
                                    viewModel = adminViewModel,
                                    onExitToCustomerApp = { navController.popBackStack() }
                                )
                            } else {
                                LaunchedEffect(Unit) { navController.popBackStack() }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNotificationsDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationsDialog = false },
            title = { Text("Notifications") },
            text = {
                if (notifications.isEmpty()) {
                    Text("No order notifications yet.")
                } else {
                    Column {
                        notifications.take(8).forEach { notif ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(
                                    text = notif.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (notif.read) MaterialTheme.colorScheme.onSurface else EmeraldDark
                                )
                                Text(notif.message, style = MaterialTheme.typography.bodySmall)
                            }
                            HorizontalDivider()
                            if (!notif.read) {
                                LaunchedEffect(notif.id) {
                                    customerViewModel.markNotificationRead(notif.id)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotificationsDialog = false }) { Text("Close") }
            }
        )
    }
}
