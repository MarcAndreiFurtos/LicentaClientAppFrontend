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


data class User(
    val id: Long? = null,
    val email: String,
    val firstName: String,
    val lastName: String,
    val connectedAccount: String
)

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

        Log.d("HomeScreen", "SSL configured for development")
    } catch (e: Exception) {
        Log.e("HomeScreen", "Error configuring SSL", e)
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

    val TAG = "HomeScreen"

    val defaultLocation = LatLng(37.7749, -122.4194)

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

    var user by remember { mutableStateOf<User?>(null) }
    var isLoadingUser by remember { mutableStateOf(false) }
    var userLoadError by remember { mutableStateOf<String?>(null) }
    var isLoadingStripe by remember { mutableStateOf(false) }
    var stripeError by remember { mutableStateOf<String?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        hasLocationPermission = fineLocationGranted || coarseLocationGranted

        if (hasLocationPermission) {
            isLocationEnabled = isLocationEnabled(context)

            if (isLocationEnabled) {
                fetchLocation(
                    context,
                    scope,
                    { isLoading -> isLoadingLocation = isLoading },
                    { location -> deviceLocation = location }
                )
            } else {
                showLocationServicesDialog = true
            }
        }
    }

    suspend fun fetchUserData(email: String): User? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://licenta-backend.westeurope.cloudapp.azure.com:8443/api/users/email/$email")
            Log.d(TAG, "Fetching user data from: $url")

            val connection = url.openConnection() as HttpURLConnection

            if (connection is HttpsURLConnection) {
                configureSSLForDevelopment(connection)
            }

            connection.apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "User API Response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.use { it.readText() }
                Log.d(TAG, "User data received: $response")

                val jsonObject = JSONObject(response)
                User(
                    id = jsonObject.optLong("id", -1L).takeIf { it != -1L },
                    email = jsonObject.getString("email"),
                    firstName = jsonObject.getString("firstName"),
                    lastName = jsonObject.getString("lastName"),
                    connectedAccount = jsonObject.optString("connectedAccount", "")
                )
            } else {
                Log.e(TAG, "Failed to fetch user data: HTTP $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user data", e)
            null
        }
    }

    suspend fun initiateStripeConnect(userId: Long): String? = withContext(Dispatchers.IO) {
        try {
            val postUrl = URL("https://licenta-backend.westeurope.cloudapp.azure.com:8443/api/stripe/$userId")
            Log.d(TAG, "Creating Stripe account: $postUrl")

            val postConnection = postUrl.openConnection() as HttpURLConnection

            if (postConnection is HttpsURLConnection) {
                configureSSLForDevelopment(postConnection)
            }

            postConnection.apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
            }

            val postResponseCode = postConnection.responseCode
            Log.d(TAG, "Stripe POST Response code: $postResponseCode")

            if (postResponseCode == HttpURLConnection.HTTP_OK) {
                val accountReader = BufferedReader(InputStreamReader(postConnection.inputStream))
                val accountIdResponse = accountReader.use { it.readText() }
                Log.d(TAG, "Stripe account ID response: $accountIdResponse")

                val accountId = accountIdResponse.trim().removeSurrounding("\"")
                Log.d(TAG, "Stripe account ID: $accountId")

                val putUrl = URL("https://licenta-backend.westeurope.cloudapp.azure.com:8443/api/stripe/$userId")
                Log.d(TAG, "Getting Stripe account link with PUT: $putUrl")

                val putConnection = putUrl.openConnection() as HttpURLConnection

                if (putConnection is HttpsURLConnection) {
                    configureSSLForDevelopment(putConnection)
                }

                val jsonPayload = JSONObject().apply {
                    put("returnUrl", "https://connect.stripe.com/hosted/setup/c/complete")
                    put("refreshUrl", "https://connect.stripe.com/hosted/setup/c/complete")
                }

                putConnection.apply {
                    requestMethod = "PUT"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                }

                val writer = OutputStreamWriter(putConnection.outputStream)
                writer.write(jsonPayload.toString())
                writer.flush()
                writer.close()

                val putResponseCode = putConnection.responseCode
                Log.d(TAG, "Stripe PUT Response code: $putResponseCode")

                if (putResponseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(putConnection.inputStream))
                    val response = reader.use { it.readText() }
                    Log.d(TAG, "Stripe link response: $response")


                    return@withContext try {

                        val jsonObject = JSONObject(response)
                        jsonObject.optString("url", null)
                    } catch (e: org.json.JSONException) {

                        Log.d(TAG, "Response is not JSON, treating as plain URL string")

                        val cleanResponse = response.trim().removeSurrounding("\"")

                        if (cleanResponse.startsWith("http")) {
                            cleanResponse
                        } else {
                            Log.e(TAG, "Expected URL but got: $cleanResponse")
                            null
                        }
                    }
                } else {
                    val errorReader = BufferedReader(InputStreamReader(putConnection.errorStream ?: putConnection.inputStream))
                    val errorResponse = errorReader.use { it.readText() }
                    Log.e(TAG, "Failed to get Stripe link: HTTP $putResponseCode - $errorResponse")
                    return@withContext null
                }
            } else {
                val errorReader = BufferedReader(InputStreamReader(postConnection.errorStream ?: postConnection.inputStream))
                val errorResponse = errorReader.use { it.readText() }
                Log.e(TAG, "Failed to create Stripe account: HTTP $postResponseCode - $errorResponse")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating Stripe Connect", e)
            return@withContext null
        }
    }

    fun handleAddConnectedAccount() {
        val currentUser = user
        if (currentUser?.id == null) {
            stripeError = "User ID not available. Please try logging out and back in."
            return
        }

        isLoadingStripe = true
        stripeError = null

        scope.launch {
            try {
                val stripeUrl = initiateStripeConnect(currentUser.id)

                if (stripeUrl != null) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(stripeUrl))
                    context.startActivity(intent)

                } else {
                    stripeError = "Failed to initiate Stripe Connect. Please try again."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling Stripe Connect", e)
                stripeError = "An error occurred: ${e.message}"
            } finally {
                isLoadingStripe = false
            }
        }
    }

    val mapProperties by remember(hasLocationPermission) {
        mutableStateOf(
            MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                maxZoomPreference = 20.0f,
                minZoomPreference = 3.0f
            )
        )
    }

    val uiSettings by remember {
        mutableStateOf(
            MapUiSettings(
                myLocationButtonEnabled = hasLocationPermission,
                zoomControlsEnabled = true,
                mapToolbarEnabled = false
            )
        )
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 10f)
    }

    LaunchedEffect(Unit) {
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        hasLocationPermission = hasFineLocationPermission || hasCoarseLocationPermission

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

        authViewModel.userProfile.collect { userProfile ->
            if (userProfile != null && user == null && !isLoadingUser) {
                isLoadingUser = true
                userLoadError = null

                val userData = fetchUserData(userProfile.email ?: "")
                user = userData
                isLoadingUser = false

                if (userData == null) {
                    userLoadError = "Failed to load user data"
                }
            }
        }
    }

    LaunchedEffect(deviceLocation) {
        deviceLocation?.let { location ->
            Log.d(TAG, "Moving camera to location: $location")
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngZoom(location, 15f)
            )
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Location Permission") },
            text = { Text("This app needs access to your location to show your position on the map and provide better pickup services.") },
            confirmButton = {
                Button(
                    onClick = {
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

    if (stripeError != null) {
        AlertDialog(
            onDismissRequest = { stripeError = null },
            title = { Text("Stripe Connect Error") },
            text = { Text(stripeError!!) },
            confirmButton = {
                Button(
                    onClick = { stripeError = null }
                ) {
                    Text("OK")
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
            LocationSearchBar(navController)

            StripeConnectedAccount(
                user = user,
                isLoading = isLoadingUser,
                error = userLoadError
            )

            AddDebitCardButton(
                isLoading = isLoadingStripe,
                onClick = { handleAddConnectedAccount() }
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = mapProperties,
                    uiSettings = uiSettings,
                    onMapLoaded = {
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
                    deviceLocation?.let { location ->
                        Marker(
                            state = MarkerState(position = location),
                            title = "Your Location"
                        )
                    }
                }

                if (isLoadingLocation) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

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
                                    Button(
                                        onClick = {
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
fun StripeConnectedAccount(
    user: User?,
    isLoading: Boolean,
    error: String?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = "Payment Account",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Payment Account",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    when {
                        isLoading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Loading payment information...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontSize = 14.sp
                                )
                            }
                        }

                        error != null -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        user != null -> {
                            if (user.connectedAccount.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Connected",
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Stripe Account: ${user.connectedAccount}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Not Connected",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "You do not have a Stripe connected account yet",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        else -> {
                            Text(
                                text = "Payment information unavailable",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
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
fun AddDebitCardButton(
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Button(
            onClick = onClick,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
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
                    Text("Setting up Stripe Connect...", fontSize = 16.sp)
                }
            } else {
                Text("Add Connected Account", fontSize = 16.sp, modifier = Modifier.padding(8.dp))
            }
        }
    }
}