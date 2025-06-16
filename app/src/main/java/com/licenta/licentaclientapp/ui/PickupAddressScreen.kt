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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import org.json.JSONObject
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import java.util.*

// Data classes for the API requests/responses
data class PickupRequest(
    val userId: Long,
    val driverLocation: String,
    val pickupLocation: String,
    val sackSizeLiters: Int
)

data class PickupResponse(
    val id: Long,
    val status: String,
    val user: User, // Changed from userId to user object
    val driverLocation: String,
    val pickupLocation: String,
) {
    // Helper property to get userId from the user object
    val userId: Long get() = user.id ?: -1L
}

// Configure SSL for development
private fun configureSSLForDevelopment(httpsConnection: HttpsURLConnection) {
    try {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        httpsConnection.sslSocketFactory = sslContext.socketFactory
        httpsConnection.hostnameVerifier = HostnameVerifier { _, _ -> true }

        Log.d("PickupAddressScreen", "SSL configured for development")
    } catch (e: Exception) {
        Log.e("PickupAddressScreen", "Error configuring SSL", e)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupAddressScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
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

    // API related state
    var isSubmittingPickup by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var user by remember { mutableStateOf<User?>(null) }

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
    suspend fun submitPickupRequest(pickupRequest: PickupRequest): PickupResponse? = withContext(Dispatchers.IO) {
        var attempt = 0
        val maxRetries = 3
        val baseDelay = 2000L // 2 seconds

        while (attempt < maxRetries) {
            try {
                attempt++
                Log.d(TAG, "Submitting pickup request (attempt $attempt/$maxRetries)")

                val url = URL("https://10.0.2.2:8443/api/sgrPickup")
                val connection = url.openConnection() as HttpURLConnection

                if (connection is HttpsURLConnection) {
                    configureSSLForDevelopment(connection)
                }

                // Create JSON payload
                val jsonPayload = JSONObject().apply {
                    put("userId", pickupRequest.userId)
                    put("driverLocation", pickupRequest.driverLocation)
                    put("pickupLocation", pickupRequest.pickupLocation)
                    put("sackSizeLiters", pickupRequest.sackSizeLiters)
                }

                connection.apply {
                    requestMethod = "POST"
                    // Increase timeout values
                    connectTimeout = 30000  // 30 seconds
                    readTimeout = 30000     // 30 seconds
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Connection", "close") // Prevent connection reuse issues
                    doOutput = true
                    useCaches = false
                }

                // Write JSON payload
                connection.outputStream.use { outputStream ->
                    OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                        writer.write(jsonPayload.toString())
                        writer.flush()
                    }
                }

                val responseCode = connection.responseCode
                Log.d(TAG, "Pickup submission response code: $responseCode (attempt $attempt)")

                when (responseCode) {
                    HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_CREATED -> {
                        val response = connection.inputStream.use { inputStream ->
                            BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                                reader.readText()
                            }
                        }
                        Log.d(TAG, "Pickup response: $response")

                        // Updated response parsing with defensive handling
                        val jsonObject = JSONObject(response)

                        // Try to get userId directly first, then fall back to user.id
                        val userId = when {
                            jsonObject.has("userId") -> jsonObject.getLong("userId")
                            jsonObject.has("user") -> {
                                val userObject = jsonObject.getJSONObject("user")
                                userObject.optLong("id", -1L)
                            }
                            else -> -1L
                        }

                        // For the user object, either parse it or reconstruct from existing data
                        val user = when {
                            jsonObject.has("user") -> {
                                val userObject = jsonObject.getJSONObject("user")
                                User(
                                    id = userObject.optLong("id", -1L).takeIf { it != -1L },
                                    email = userObject.optString("email", ""),
                                    firstName = userObject.optString("firstName", ""),
                                    lastName = userObject.optString("lastName", ""),
                                    connectedAccount = userObject.optString("connectedAccount", "")
                                )
                            }
                            else -> {
                                // If no user object, create one with just the ID
                                User(
                                    id = userId.takeIf { it != -1L },
                                    email = "",
                                    firstName = "",
                                    lastName = "",
                                    connectedAccount = ""
                                )
                            }
                        }

                        return@withContext PickupResponse(
                            id = jsonObject.getLong("id"),
                            status = jsonObject.getString("status"),
                            user = user,
                            driverLocation = jsonObject.getString("driverLocation"),
                            pickupLocation = jsonObject.getString("pickupLocation")
                        )
                    }
                    else -> {
                        val errorResponse = try {
                            connection.errorStream?.use { errorStream ->
                                BufferedReader(InputStreamReader(errorStream, "UTF-8")).use { reader ->
                                    reader.readText()
                                }
                            } ?: "No error details available"
                        } catch (e: Exception) {
                            "Error reading error response: ${e.message}"
                        }

                        Log.e(TAG, "HTTP Error $responseCode (attempt $attempt): $errorResponse")

                        // Don't retry for client errors (4xx)
                        if (responseCode in 400..499) {
                            Log.e(TAG, "Client error, not retrying")
                            return@withContext null
                        }

                        // Retry for server errors (5xx) and other issues
                        if (attempt < maxRetries) {
                            val delay = baseDelay * attempt
                            Log.d(TAG, "Retrying in ${delay}ms...")
                            kotlinx.coroutines.delay(delay)
                            continue
                        }
                    }
                }

            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "Socket timeout on attempt $attempt", e)

                if (attempt < maxRetries) {
                    val delay = baseDelay * attempt
                    Log.d(TAG, "Timeout occurred, retrying in ${delay}ms...")
                    kotlinx.coroutines.delay(delay)
                    continue
                } else {
                    Log.e(TAG, "All retry attempts exhausted due to timeout")
                    throw Exception("Connection timeout after $maxRetries attempts. Please check your internet connection and try again.")
                }

            } catch (e: java.net.ConnectException) {
                Log.e(TAG, "Connection failed on attempt $attempt", e)

                if (attempt < maxRetries) {
                    val delay = baseDelay * attempt
                    Log.d(TAG, "Connection failed, retrying in ${delay}ms...")
                    kotlinx.coroutines.delay(delay)
                    continue
                } else {
                    Log.e(TAG, "All retry attempts exhausted due to connection failure")
                    throw Exception("Unable to connect to server after $maxRetries attempts. Please check if the server is running.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error on attempt $attempt", e)

                // For unexpected errors, only retry if it's not the last attempt
                if (attempt < maxRetries && (e is java.io.IOException || e is javax.net.ssl.SSLException)) {
                    val delay = baseDelay * attempt
                    Log.d(TAG, "Unexpected error, retrying in ${delay}ms...")
                    kotlinx.coroutines.delay(delay)
                    continue
                } else {
                    throw Exception("Request failed: ${e.localizedMessage ?: e.message}")
                }
            }
        }

        return@withContext null
    }

    // Improved SSL configuration
    fun configureSSLForDevelopment(httpsConnection: HttpsURLConnection) {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("TLS") // Use TLS instead of SSL
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            httpsConnection.sslSocketFactory = sslContext.socketFactory
            httpsConnection.hostnameVerifier = HostnameVerifier { _, _ -> true }

            // Additional SSL settings
            httpsConnection.setRequestProperty("User-Agent", "Android-App")

            Log.d("PickupAddressScreen", "SSL configured for development")
        } catch (e: Exception) {
            Log.e("PickupAddressScreen", "Error configuring SSL", e)
            throw e // Rethrow to handle SSL configuration failures
        }
    }

    // Also update the fetchUserData function with similar improvements
    suspend fun fetchUserData(email: String): User? = withContext(Dispatchers.IO) {
        var attempt = 0
        val maxRetries = 2

        while (attempt < maxRetries) {
            try {
                attempt++
                val url = URL("https://10.0.2.2:8443/api/users/email/$email")
                Log.d(TAG, "Fetching user data from: $url (attempt $attempt)")

                val connection = url.openConnection() as HttpURLConnection

                if (connection is HttpsURLConnection) {
                    configureSSLForDevelopment(connection)
                }

                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 20000  // 20 seconds
                    readTimeout = 20000     // 20 seconds
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Connection", "close")
                    useCaches = false
                }

                val responseCode = connection.responseCode
                Log.d(TAG, "User API Response code: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                            reader.readText()
                        }
                    }
                    Log.d(TAG, "User data received: $response")

                    val jsonObject = JSONObject(response)
                    return@withContext User(
                        id = jsonObject.optLong("id", -1L).takeIf { it != -1L },
                        email = jsonObject.getString("email"),
                        firstName = jsonObject.getString("firstName"),
                        lastName = jsonObject.getString("lastName"),
                        connectedAccount = jsonObject.optString("connectedAccount", "")
                    )
                } else {
                    Log.e(TAG, "Failed to fetch user data: HTTP $responseCode")
                    if (attempt < maxRetries && responseCode >= 500) {
                        kotlinx.coroutines.delay(2000)
                        continue
                    }
                    return@withContext null
                }
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "Timeout fetching user data (attempt $attempt)", e)
                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(2000)
                    continue
                }
                return@withContext null
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching user data (attempt $attempt)", e)
                if (attempt < maxRetries && (e is java.io.IOException || e is javax.net.ssl.SSLException)) {
                    kotlinx.coroutines.delay(2000)
                    continue
                }
                return@withContext null
            }
        }
        return@withContext null
    }
    // Function to fetch user data (same as in HomeScreen)

    // Function to handle pickup confirmation
    fun handlePickupConfirmation() {
        val currentUser = user
        val sackSizeInt = sackSize.toIntOrNull()

        if (currentUser?.id == null) {
            submitError = "User ID not available. Please try logging out and back in."
            return
        }

        if (sackSizeInt == null || sackSizeInt <= 0) {
            submitError = "Please enter a valid sack size."
            return
        }

        if (currentAddress == "Loading location..." || currentAddress.isEmpty()) {
            submitError = "Please wait for location to load or enter a valid address."
            return
        }

        isSubmittingPickup = true
        submitError = null

        scope.launch {
            try {
                val pickupRequest = PickupRequest(
                    userId = currentUser.id,
                    driverLocation = currentAddress,
                    pickupLocation = currentAddress,
                    sackSizeLiters = sackSizeInt
                )

                val pickupResponse = submitPickupRequest(pickupRequest)

                if (pickupResponse != null) {
                    // Navigate to loading screen with pickup ID
                    navController.navigate("pickup_loading_screen/${pickupResponse.id}") {
                        // Clear the back stack so user can't go back to this screen
                        popUpTo("pickup_address_screen") { inclusive = true }
                    }
                } else {
                    submitError = "Failed to submit pickup request. Please try again."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling pickup confirmation", e)
                submitError = "An error occurred: ${e.message}"
            } finally {
                isSubmittingPickup = false
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

        // Fetch user data
        authViewModel.userProfile.collect { userProfile ->
            if (userProfile != null && user == null) {
                val userData = fetchUserData(userProfile.email ?: "")
                user = userData
            }
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

    // Submit error dialog
    if (submitError != null) {
        AlertDialog(
            onDismissRequest = { submitError = null },
            title = { Text("Pickup Request Error") },
            text = { Text(submitError!!) },
            confirmButton = {
                Button(
                    onClick = { submitError = null }
                ) {
                    Text("OK")
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
            // Google Maps Implementation (same as before)
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

                // Show permission/location services button if needed (same as before)
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

            // Address Confirmation (same as before)
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

            // Sack Size Selection (same as before)
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

            // Action Button - Updated to handle API submission
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { handlePickupConfirmation() },
                enabled = !isSubmittingPickup,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (isSubmittingPickup) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SUBMITTING...")
                    }
                } else {
                    Text(
                        "CONFIRM PICKUP",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Add some space at the bottom for better scrolling experience
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// Helper function to get address from LatLng (same as before)
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