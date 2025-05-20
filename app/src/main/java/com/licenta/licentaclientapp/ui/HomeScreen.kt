package com.licenta.licentaclientapp.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch

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