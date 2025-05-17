package com.licenta.licentaclientapp.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// Location request for getting current location
private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
    .setWaitForAccurateLocation(false)
    .setMinUpdateIntervalMillis(5000)
    .setMaxUpdateDelayMillis(10000)
    .build()

// Function to check if location services are enabled
fun isLocationEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

// Improved function to fetch location
@SuppressLint("MissingPermission")
fun fetchLocation(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    setLoading: (Boolean) -> Unit,
    callback: (LatLng?) -> Unit
) {
    setLoading(true)

    // First check if location services are enabled
    if (!isLocationEnabled(context)) {
        Log.d("LocationFetch", "Location services disabled")
        setLoading(false)
        callback(null)
        return
    }

    // Check permissions
    if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        Log.d("LocationFetch", "Permission not granted")
        setLoading(false)
        callback(null)
        return
    }

    scope.launch {
        try {
            // First try to get current location
            val location = getCurrentLocation(context)
            if (location != null) {
                Log.d("LocationFetch", "Current location fetched: $location")
                callback(location)
                setLoading(false)
                return@launch
            }

            // If current location fails, fall back to last known location
            val lastLocation = getLastKnownLocation(context)
            Log.d("LocationFetch", "Last known location fetched: $lastLocation")
            callback(lastLocation)
        } catch (e: Exception) {
            Log.e("LocationFetch", "Error fetching location", e)
            callback(null)
        } finally {
            setLoading(false)
        }
    }
}

// Function to get the current location
@SuppressLint("MissingPermission")
suspend fun getCurrentLocation(context: Context): LatLng? {
    val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    return suspendCancellableCoroutine { cont ->
        try {
            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    fusedClient.removeLocationUpdates(this)
                    val location = result.lastLocation
                    if (location != null) {
                        cont.resume(LatLng(location.latitude, location.longitude))
                    } else {
                        cont.resume(null)
                    }
                }
            }

            fusedClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            // Set a timeout
            cont.invokeOnCancellation {
                fusedClient.removeLocationUpdates(locationCallback)
            }

        } catch (e: Exception) {
            Log.e("Location", "Exception in current location fetch", e)
            cont.resume(null)
        }
    }
}

// Improved function to get last known location
@SuppressLint("MissingPermission")
suspend fun getLastKnownLocation(context: Context): LatLng? {
    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    return suspendCancellableCoroutine { cont ->
        try {
            fusedClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        cont.resume(LatLng(location.latitude, location.longitude))
                    } else {
                        Log.d("Location", "Last location was null")
                        cont.resume(null)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("Location", "Failed to get last location", e)
                    cont.resume(null)
                }

        } catch (e: Exception) {
            Log.e("Location", "Exception in last location fetch", e)
            cont.resume(null)
        }
    }
}

@Composable
fun HomeScreen(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Tag for logging
    val TAG = "HomeScreen"

    // Static initial location (San Francisco)
    val defaultLocation = LatLng(37.7749, -122.4194)

    // State variables
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
                    { location -> deviceLocation = location }
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
        position = CameraPosition.fromLatLngZoom(defaultLocation, 10f)
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
                { location -> deviceLocation = location }
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
            TopBar(
                onLogout = {
                    scope.launch {
                        authViewModel.logout(context) {
                            onLogout()
                        }
                    }
                },
                navController = navController
            )
        },
        bottomBar = { CallToActionButton(navController) }
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(paddingValues)
        ) {
            // Updated to use our clickable LocationSearchBar
            LocationSearchBar(navController)
            SavedAddresses()
            AddDebitCardButton(navController)

            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
            ) {
                // Map content
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
                                { location -> deviceLocation = location }
                            )
                        }
                    }
                ) {
                    // Only draw marker if we have a location
                    deviceLocation?.let { location ->
                        Marker(
                            state = MarkerState(position = location),
                            title = "Your Location"
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
            }
        }
    }
}

@Composable
fun TopBar(onLogout: () -> Unit, navController: NavController) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Welcome to Sgr Pickup",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        // Add menu button with dropdown
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Account"
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("My Profile") },
                    onClick = {
                        showMenu = false
                        navController.navigate("profile_screen")
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile"
                        )
                    }
                )

                DropdownMenuItem(
                    text = { Text("Log Out") },
                    onClick = {
                        showMenu = false
                        onLogout()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Log Out"
                        )
                    }
                )
            }
        }
    }
}



@Composable
fun SavedAddresses() {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        SavedAddressItem("Home", "123 Main Street")
        SavedAddressItem("Work", "456 Office Rd")
    }
}

@Composable
fun SavedAddressItem(title: String, address: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Place,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(24.dp)
        )
        Column(Modifier.padding(start = 8.dp)) {
            Text(text = title, fontWeight = FontWeight.Bold)
            Text(text = address, color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun AddDebitCardButton(navController: NavController) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Button(
            onClick = {
                // Navigate to DebitCardScreen
                navController.navigate("debit_card_screen")
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Add Debit Card", fontSize = 16.sp, modifier = Modifier.padding(8.dp))
        }
    }
}


// New ProfileScreen component
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Picture
            Box(
                modifier = Modifier
                    .padding(vertical = 24.dp)
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.LightGray)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // You can replace this with an actual image when available
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile Picture",
                    modifier = Modifier.size(64.dp),
                    tint = Color.White
                )

                // For an actual image, you would use:
                // Image(
                //     painter = painterResource(id = R.drawable.profile_picture),
                //     contentDescription = "Profile Picture",
                //     modifier = Modifier.fillMaxSize(),
                //     contentScale = ContentScale.Crop
                // )
            }

            // User details
            Text(
                text = "John Doe", // Replace with actual user name
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "john.doe@example.com", // Replace with actual email
                fontSize = 16.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Delivery History header
            Text(
                text = "Delivery History",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textAlign = TextAlign.Left
            )

            // Sample delivery history
            // In a real app, this would come from a database or API
            val deliveryHistory = remember {
                listOf(
                    DeliveryHistoryItem("Package #12345", "May 10, 2025", "Delivered"),
                    DeliveryHistoryItem("Package #12344", "May 5, 2025", "Delivered"),
                    DeliveryHistoryItem("Package #12343", "April 28, 2025", "Delivered"),
                    DeliveryHistoryItem("Package #12342", "April 15, 2025", "Delivered"),
                    DeliveryHistoryItem("Package #12341", "April 2, 2025", "Delivered")
                )
            }

            // Display delivery history
            LazyColumn {
                items(deliveryHistory) { delivery ->
                    DeliveryHistoryItemCard(delivery)
                }
            }
        }
    }
}

// Data class for delivery history items
data class DeliveryHistoryItem(
    val id: String,
    val date: String,
    val status: String
)

// UI component for a delivery history item
@Composable
fun DeliveryHistoryItemCard(delivery: DeliveryHistoryItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = delivery.id,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = delivery.date,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }

            // Status chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        when (delivery.status) {
                            "Delivered" -> Color(0xFF4CAF50)
                            "In Transit" -> Color(0xFF2196F3)
                            else -> Color(0xFFFF9800)
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = delivery.status,
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}