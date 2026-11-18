package com.example.ui.customer

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.Address
import com.example.data.model.AppSettings
import com.example.data.model.DeliveryZone
import com.example.domain.DeliveryCalculator
import com.example.ui.theme.DangerContainer
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSelectionScreen(
    addresses: List<Address>,
    activeAddress: Address?,
    appSettings: AppSettings,
    deliveryZones: List<DeliveryZone>,
    defaultName: String,
    defaultPhone: String,
    onSelectAddress: (Address) -> Unit,
    onSaveAddress: (Address, () -> Unit) -> Unit,
    onDeleteAddress: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var label by remember { mutableStateOf("Home") }
    var recipientName by remember(defaultName) { mutableStateOf(defaultName) }
    var phone by remember(defaultPhone) { mutableStateOf(defaultPhone) }
    var houseLine by remember { mutableStateOf("") }
    var areaLine by remember { mutableStateOf("") }
    var landmark by remember { mutableStateOf("") }
    var pincode by remember { mutableStateOf("") }
    var lat by remember { mutableDoubleStateOf(appSettings.storeLat) }
    var lng by remember { mutableDoubleStateOf(appSettings.storeLng) }
    var gpsStatus by remember { mutableStateOf<String?>(null) }

    val previewServiceability = remember(lat, lng, appSettings, deliveryZones) {
        DeliveryCalculator.evaluateServiceability(lat, lng, appSettings, deliveryZones)
    }

    @SuppressLint("MissingPermission")
    fun fetchCurrentGpsLocation() {
        gpsStatus = "Fetching current GPS coordinates..."
        scope.launch {
            try {
                val client = LocationServices.getFusedLocationProviderClient(context)
                val loc = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                    ?: client.lastLocation.await()
                if (loc != null) {
                    lat = loc.latitude
                    lng = loc.longitude
                    gpsStatus = "GPS locked (${"%.4f".format(lat)}, ${"%.4f".format(lng)})"
                    withContext(Dispatchers.IO) {
                        runCatching {
                            @Suppress("DEPRECATION")
                            val results = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)
                            results?.firstOrNull()?.let { geo ->
                                if (areaLine.isBlank()) {
                                    areaLine = listOfNotNull(geo.subLocality, geo.locality)
                                        .joinToString(", ")
                                        .ifBlank { geo.getAddressLine(0) ?: "" }
                                }
                                if (pincode.isBlank() && !geo.postalCode.isNullOrBlank()) {
                                    pincode = geo.postalCode
                                }
                            }
                        }
                    }
                } else {
                    gpsStatus = "GPS location unavailable. Enter address manually or use Store Neighbourhood."
                }
            } catch (e: Exception) {
                gpsStatus = "Could not fetch GPS: ${e.localizedMessage}"
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchCurrentGpsLocation()
        } else {
            gpsStatus = "Location permission denied. You can still enter your address manually."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Delivery Location") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        val hasFine = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasFine) {
                            fetchCurrentGpsLocation()
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("use_gps_button")
                ) {
                    Icon(Icons.Filled.GpsFixed, contentDescription = null, tint = EmeraldPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Use Current GPS Location")
                }

                if (gpsStatus != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = gpsStatus ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Live Service-Area Preview
            item {
                Surface(
                    color = if (previewServiceability.serviceable) EmeraldContainer else DangerContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (previewServiceability.serviceable) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (previewServiceability.serviceable) EmeraldDark else DangerCoral
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (previewServiceability.serviceable) "Inside 2–3 km Service Area" else "Outside Service Area",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (previewServiceability.serviceable) EmeraldDark else DangerCoral
                            )
                            Text(
                                text = previewServiceability.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (previewServiceability.serviceable) EmeraldDark else DangerCoral
                            )
                        }
                    }
                }
            }

            if (addresses.isNotEmpty()) {
                item {
                    Text("Saved Addresses", style = MaterialTheme.typography.titleMedium)
                }
                items(addresses, key = { it.id }) { addr ->
                    val isSelected = addr.id == activeAddress?.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectAddress(addr)
                                onBack()
                            },
                        border = BorderStroke(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) EmeraldPrimary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) EmeraldContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = if (addr.serviceable) EmeraldPrimary else DangerCoral
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${addr.label} • ${addr.recipientName}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = addr.formattedSummary(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (addr.serviceable) "Serviceable (30–60 mins)" else "Outside delivery zone",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (addr.serviceable) EmeraldDark else DangerCoral
                                )
                            }
                            IconButton(onClick = { onDeleteAddress(addr.id) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete address",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Manual Address Entry Form
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Add New Address", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Home", "Work", "Other").forEach { tag ->
                        FilterChip(
                            selected = label == tag,
                            onClick = { label = tag },
                            label = { Text(tag) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = recipientName,
                    onValueChange = { recipientName = it },
                    label = { Text("Recipient Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Mobile Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = houseLine,
                    onValueChange = { houseLine = it },
                    label = { Text("Flat / House No. / Building") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("address_house_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = areaLine,
                    onValueChange = { areaLine = it },
                    label = { Text("Colony / Street / Sector") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("address_area_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = landmark,
                        onValueChange = { landmark = it },
                        label = { Text("Landmark (Optional)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = pincode,
                        onValueChange = { pincode = it },
                        label = { Text("PIN Code") },
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                val canSave = recipientName.isNotBlank() &&
                    phone.trim().length >= 6 &&
                    houseLine.trim().length >= 2 &&
                    areaLine.trim().length >= 2 &&
                    pincode.trim().length >= 4

                Button(
                    onClick = {
                        onSaveAddress(
                            Address(
                                label = label,
                                recipientName = recipientName.trim(),
                                phone = phone.trim(),
                                houseLine = houseLine.trim(),
                                areaLine = areaLine.trim(),
                                landmark = landmark.trim().ifBlank { null },
                                pincode = pincode.trim(),
                                latitude = lat,
                                longitude = lng,
                                serviceable = previewServiceability.serviceable,
                                defaultAddress = true
                            )
                        ) {
                            onBack()
                        }
                    },
                    enabled = canSave,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_address_button")
                ) {
                    Text("Save & Select Address", color = Color.White)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
