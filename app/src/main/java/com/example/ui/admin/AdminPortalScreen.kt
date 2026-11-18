package com.example.ui.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UiState
import com.example.ui.theme.EmeraldPrimary

private data class AdminTabItem(val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPortalScreen(
    viewModel: AdminViewModel,
    onExitToCustomerApp: () -> Unit
) {
    val ordersState by viewModel.ordersState.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val coupons by viewModel.coupons.collectAsStateWithLifecycle()
    val deliveryZones by viewModel.deliveryZones.collectAsStateWithLifecycle()
    val inventoryLogs by viewModel.inventoryLogs.collectAsStateWithLifecycle()
    val orderHistory by viewModel.orderHistory.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message, error) {
        val text = error ?: message
        if (!text.isNullOrBlank()) {
            snackbarHostState.showSnackbar(text)
            viewModel.clearBanners()
        }
    }

    val tabs = listOf(
        AdminTabItem("Dashboard", Icons.Filled.Dashboard),
        AdminTabItem("Orders", Icons.Filled.ReceiptLong),
        AdminTabItem("Catalog", Icons.Filled.Storefront),
        AdminTabItem("Inventory", Icons.Filled.Inventory),
        AdminTabItem("Operations", Icons.Filled.Settings)
    )

    val allOrders = (ordersState as? UiState.Success)?.data ?: emptyList()
    val metrics = remember(allOrders, products, customers) {
        viewModel.computeMetrics(allOrders, products, customers)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("APNA BAZAAR Admin", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text("Store Operations & Inventory", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onExitToCustomerApp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Customer Store", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = EmeraldPrimary)
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        tabs.forEachIndexed { idx, item ->
                            NavigationBarItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) }
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
                        tabs.forEachIndexed { idx, item ->
                            NavigationRailItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (selectedTab) {
                        0 -> AdminDashboardTab(
                            metrics = metrics,
                            products = products,
                            isBusy = isBusy,
                            onSeedCatalog = viewModel::seedInitialCatalog,
                            onOpenOrders = { selectedTab = 1 },
                            onOpenInventory = { selectedTab = 3 }
                        )
                        1 -> AdminOrdersTab(
                            ordersState = ordersState,
                            orderHistory = orderHistory,
                            onConfirmOrder = viewModel::confirmOrder,
                            onAdvanceStatus = viewModel::advanceOrderStatus
                        )
                        2 -> AdminCatalogTab(
                            categories = categories,
                            products = products,
                            onSaveCategory = viewModel::saveCategory,
                            onDeleteCategory = viewModel::deleteCategory,
                            onSaveProduct = viewModel::saveProduct
                        )
                        3 -> AdminInventoryTab(
                            products = products,
                            inventoryLogs = inventoryLogs,
                            onAdjustStock = viewModel::adjustStock
                        )
                        4 -> AdminOperationsTab(
                            customers = customers,
                            orders = allOrders,
                            coupons = coupons,
                            deliveryZones = deliveryZones,
                            appSettings = appSettings,
                            onSaveCoupon = viewModel::saveCoupon,
                            onSaveZone = viewModel::saveDeliveryZone,
                            onSaveSettings = viewModel::saveSettings
                        )
                    }
                }
            }
        }
    }
}
