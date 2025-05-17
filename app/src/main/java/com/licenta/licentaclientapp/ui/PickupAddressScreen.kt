package com.licenta.licentaclientapp.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupAddressScreen(
    navController: NavController,
    // Use the existing CardViewModel from the app
    cardViewModel: CardViewModel? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val TAG = "PickupAddressScreen"

    // Default location (will be updated with real location)
    val defaultLocation = LatLng(37.7749, -122.4194)

    var sackSize by remember { mutableStateOf("60") }
    var isAddressConfirmed by remember { mutableStateOf(true) }
    var currentAddress by remember { mutableStateOf("Loading location...") }
    var selectedCardIndex by remember { mutableStateOf(0) }

    // Location-related state
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isLocationEnabled by remember {
        mutableStateOf(isLocationEnabled(context))
    }

    var showPermissionDialog by remember { mutableStateOf(!hasLocationPermission) }
    var showLocationServicesDialog by remember { mutableStateOf(hasLocationPermission && !isLocationEnabled) }
    var deviceLocation by remember { mutableStateOf<LatLng?>(null) }
    var isLoadingLocation by remember { mutableStateOf(false) }

    // Permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        hasLocationPermission = fineLocationGranted || coarseLocationGranted

        if (hasLocationPermission) {
            // Check if location services are enabled
            isLocationEnabled = isLocationEnabled(context)

            if (isLocationEnabled) {
                // Both permission and location services are enabled, fetch location
                fetchLocation(
                    context,
                    scope,
                    { isLoading -> isLoadingLocation = isLoading },
                    { location ->
                        deviceLocation = location
                        // Get address from coordinates
                        location?.let { getAddressFromLocation(context, it) { address ->
                            if (address != null) {
                                currentAddress = address
                            }
                        }}
                    }
                )
            } else {
                // Show dialog to enable location services
                showLocationServicesDialog = true
            }
        }
    }

    // Map properties
    val mapProperties by remember(hasLocationPermission) {
        mutableStateOf(
            MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                maxZoomPreference = 20.0f,
                minZoomPreference = 3.0f
            )
        )
    }

    // UI settings
    val uiSettings by remember {
        mutableStateOf(
            MapUiSettings(
                myLocationButtonEnabled = hasLocationPermission,
                zoomControlsEnabled = true,
                mapToolbarEnabled = false
            )
        )
    }

    // Camera position state
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 15f)
    }

    // Initial setup
    LaunchedEffect(Unit) {
        // Update permission state
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        hasLocationPermission = hasFineLocationPermission || hasCoarseLocationPermission

        // Check if location is enabled
        isLocationEnabled = isLocationEnabled(context)

        Log.d(TAG, "Initial check - Permission: $hasLocationPermission, Location enabled: $isLocationEnabled")

        if (hasLocationPermission && isLocationEnabled && deviceLocation == null && !isLoadingLocation) {
            fetchLocation(
                context,
                scope,
                { isLoading -> isLoadingLocation = isLoading },
                { location ->
                    deviceLocation = location
                    // Get address from coordinates
                    location?.let { getAddressFromLocation(context, it) { address ->
                        if (address != null) {
                            currentAddress = address
                        }
                    }}
                }
            )
        } else if (hasLocationPermission && !isLocationEnabled) {
            showLocationServicesDialog = true
        } else if (!hasLocationPermission) {
            showPermissionDialog = true
        }
    }

    // Move camera when device location is obtained
    LaunchedEffect(deviceLocation) {
        deviceLocation?.let { location ->
            Log.d(TAG, "Moving camera to location: $location")
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngZoom(location, 15f)
            )
        }
    }

    // Mock credit card data - in a real app this would come from cardViewModel
    val availableCreditCards = remember {
        listOf(
            PaymentCard("Visa", "****1234", "12/25"),
            PaymentCard("Mastercard", "****5678", "10/26")
        )
    }

    // Location permission dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Location Permission") },
            text = { Text("This app needs access to your location to show your position on the map and provide better pickup services.") },
            confirmButton = {
                Button(
                    onClick = {
                        // Request both permissions
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                        showPermissionDialog = false
                    }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPermissionDialog = false }
                ) {
                    Text("Not Now")
                }
            }
        )
    }

    // Location services dialog
    if (showLocationServicesDialog) {
        AlertDialog(
            onDismissRequest = { showLocationServicesDialog = false },
            title = { Text("Location Services Disabled") },
            text = { Text("Please enable location services to use this feature.") },
            confirmButton = {
                Button(
                    onClick = {
                        // Open location settings
                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        context.startActivity(intent)
                        showLocationServicesDialog = false
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLocationServicesDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirm Pickup Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Google Maps Implementation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = mapProperties,
                    uiSettings = uiSettings,
                    onMapLoaded = {
                        // Refresh location when map is loaded if we don't have it yet
                        if (hasLocationPermission && isLocationEnabled && deviceLocation == null && !isLoadingLocation) {
                            fetchLocation(
                                context,
                                scope,
                                { isLoading -> isLoadingLocation = isLoading },
                                { location ->
                                    deviceLocation = location
                                    // Get address from coordinates
                                    location?.let { getAddressFromLocation(context, it) { address ->
                                        if (address != null) {
                                            currentAddress = address
                                        }
                                    }}
                                }
                            )
                        }
                    }
                ) {
                    // Only draw marker if we have a location
                    deviceLocation?.let { location ->
                        Marker(
                            state = MarkerState(position = location),
                            title = "Pickup Location"
                        )
                    }
                }

                // Show loading indicator if currently fetching location
                if (isLoadingLocation) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                // Show permission/location services button if needed
                if (!hasLocationPermission || (hasLocationPermission && !isLocationEnabled)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopCenter)
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (!hasLocationPermission)
                                    "Location permission is needed to show your current position"
                                else
                                    "Location services need to be enabled",
                                color = Color.Black,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            if (!hasLocationPermission) {
                                // Check if we need to show the settings button
                                val shouldOpenSettings = try {
                                    val activity = context as? android.app.Activity
                                    if (activity != null) {
                                        !ActivityCompat.shouldShowRequestPermissionRationale(
                                            activity,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    } else {
                                        false
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error checking permission rationale", e)
                                    false
                                }

                                if (shouldOpenSettings) {
                                    // If permission was denied permanently, open settings
                                    Button(
                                        onClick = {
                                            // Open app settings
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.fromParts("package", context.packageName, null)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.fillMaxWidth(0.7f)
                                    ) {
                                        Text("Open Settings")
                                    }
                                } else {
                                    // Regular permission request
                                    Button(
                                        onClick = {
                                            locationPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(0.7f)
                                    ) {
                                        Text("Enable Location")
                                    }
                                }
                            } else if (!isLocationEnabled) {
                                // Button to open location settings
                                Button(
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.fillMaxWidth(0.7f)
                                ) {
                                    Text("Enable Location Services")
                                }
                            }
                        }
                    }
                }

                // "Start Pickup" button at the bottom of the map
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.BottomCenter)
                ) {
                }
            }

            // Address Confirmation
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Pickup Address",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = "Location",
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            currentAddress,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )

                        IconButton(onClick = { isAddressConfirmed = false }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Address"
                            )
                        }
                    }

                    // Custom address input (shows when address is not confirmed)
                    if (!isAddressConfirmed) {
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = currentAddress,
                            onValueChange = { currentAddress = it },
                            label = { Text("Enter correct address") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { isAddressConfirmed = true }) {
                                Text("Confirm")
                            }
                        }
                    }
                }
            }

            // Sack Size Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Sack Size",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = sackSize,
                        onValueChange = {
                            // Only allow numeric input
                            if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                sackSize = it
                            }
                        },
                        label = { Text("Size in liters") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        suffix = { Text("L") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Payment Method Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Payment Method",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    availableCreditCards.forEachIndexed { index, card ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .border(
                                    width = 1.dp,
                                    color = if (selectedCardIndex == index)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        Color.LightGray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .selectable(
                                    selected = selectedCardIndex == index,
                                    onClick = { selectedCardIndex = index }
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CreditCard,
                                contentDescription = "Credit Card",
                                tint = MaterialTheme.colorScheme.primary
                            )

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    card.type,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "${card.lastFourDigits} | Expires ${card.expiryDate}",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }

                            RadioButton(
                                selected = selectedCardIndex == index,
                                onClick = { selectedCardIndex = index }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    TextButton(
                        onClick = { navController.navigate("debit_card_screen") },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("+ Add New Card")
                    }
                }
            }

            // Action Button
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    // Process the pickup request and navigate to the next screen
                    // For example:
                    // viewModel.startPickup(currentAddress, sackSize.toInt(), availableCreditCards[selectedCardIndex])
                    // navController.navigate("pickup_confirmation_screen")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    "CONFIRM PICKUP",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Add some space at the bottom for better scrolling experience
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// Helper function to get address from LatLng
fun getAddressFromLocation(context: Context, location: LatLng, callback: (String?) -> Unit) {
    try {
        val geocoder = Geocoder(context, Locale.getDefault())

        // For Android SDK 33 and above
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                if (addresses.isNotEmpty()) {
                    val address = addresses[0]
                    val addressLine = address.getAddressLine(0)
                    callback(addressLine)
                } else {
                    callback("Unknown location")
                }
            }
        } else {
            // For older Android versions
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                val address = addresses[0]
                val addressLine = address.getAddressLine(0)
                callback(addressLine)
            } else {
                callback("Unknown location")
            }
        }
    } catch (e: Exception) {
        Log.e("Geocoder", "Error getting address", e)
        callback("Location: ${location.latitude}, ${location.longitude}")
    }
}

// If PaymentCard data class doesn't exist in your project, you can use this definition
// Otherwise, you can remove this and use your existing PaymentCard class
data class PaymentCard(
    val type: String,
    val lastFourDigits: String,
    val expiryDate: String
)