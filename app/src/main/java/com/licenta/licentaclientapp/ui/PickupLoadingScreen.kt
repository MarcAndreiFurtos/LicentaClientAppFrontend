package com.licenta.licentaclientapp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier

// Data classes
data class PickupStatus(
    val id: Long,
    val status: String
)

data class EtaResponse(
    val estimatedTime: String?,
    val distance: String?,
    val driverLocation: LatLng?,
    val pickupLocation: LatLng?
)

// Updated data class to handle driver address instead of coordinates
data class SgrPickupData(
    val id: Long,
    val status: String,
    val driverLocation: String?, // Changed from driverLatitude/driverLongitude
    val pickupLatitude: Double?,
    val pickupLongitude: Double?,
    val pickupLocation: String? = null,
    val sackSizeLiters: Int? = null,
    val userId: Long? = null,
    val driverId: Long? = null,
    val cardId: Long? = null
)

// DTO for payment request
data class SgrPickupDto(
    val driverLocation: String ,
    val pickupLocation: String ,
    val sackSizeLiters: Int ,
    val userId: Long ,
    val driverId: Long ,
    val cardId: Long
)

// Add this data class to store geocoded results
data class GeocodedLocation(
    val address: String,
    val latitude: Double,
    val longitude: Double
)

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

        Log.d("PickupTrackingScreen", "SSL configured for development")
    } catch (e: Exception) {
        Log.e("PickupTrackingScreen", "Error configuring SSL", e)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupLoadingScreen(
    navController: NavController,
    pickupId: Long
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val TAG = "PickupTrackingScreen"

    // Location services
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Add geocoding function
    suspend fun geocodeAddress(address: String): LatLng? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🌍 Geocoding address: $address")

            // Using Android's Geocoder (requires API key in manifest)
            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())

            @Suppress("DEPRECATION")
            val addresses =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    // For API 33+, use the new async method
                    var result: List<android.location.Address>? = null
                    geocoder.getFromLocationName(address, 1) { addressList ->
                        result = addressList
                    }
                    // Wait for result (in real implementation, you might want to use proper async handling)
                    var attempts = 0
                    while (result == null && attempts < 50) { // Wait up to 5 seconds
                        delay(100)
                        attempts++
                    }
                    result
                } else {
                    // For older APIs, use the deprecated synchronous method
                    geocoder.getFromLocationName(address, 1)
                }

            if (!addresses.isNullOrEmpty()) {
                val location = addresses[0]
                val latLng = LatLng(location.latitude, location.longitude)
                Log.d(TAG, "✅ Geocoded '$address' to: ${location.latitude}, ${location.longitude}")
                latLng
            } else {
                Log.w(TAG, "⚠️ No geocoding results found for address: $address")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error geocoding address: $address", e)
            null
        }
    }

    // Alternative geocoding using Google Maps API (if you have an API key)
    suspend fun geocodeAddressWithGoogleAPI(address: String, apiKey: String): LatLng? =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val encodedAddress = java.net.URLEncoder.encode(address, "UTF-8")
                val url =
                    URL("https://maps.googleapis.com/maps/api/geocode/json?address=$encodedAddress&key=$apiKey")
                Log.d(TAG, "🌍 Geocoding with Google API: $address")

                connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.use { it.readText() }

                    val jsonObject = JSONObject(response)
                    val status = jsonObject.getString("status")

                    if (status == "OK") {
                        val results = jsonObject.getJSONArray("results")
                        if (results.length() > 0) {
                            val firstResult = results.getJSONObject(0)
                            val geometry = firstResult.getJSONObject("geometry")
                            val location = geometry.getJSONObject("location")
                            val lat = location.getDouble("lat")
                            val lng = location.getDouble("lng")

                            Log.d(TAG, "✅ Google API geocoded '$address' to: $lat, $lng")
                            return@withContext LatLng(lat, lng)
                        }
                    }
                }

                Log.w(TAG, "⚠️ Google API geocoding failed for address: $address")
                null
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error with Google API geocoding: $address", e)
                null
            } finally {
                connection?.disconnect()
            }
        }


    // Function to get user location
    fun getUserLocation(locationClient: FusedLocationProviderClient) {
        try {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                locationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        val userLatLng = LatLng(location.latitude, location.longitude)
                        Log.d(TAG, "User location: ${location.latitude}, ${location.longitude}")
                        userLocation = userLatLng
                    } else {
                        Log.w(TAG, "User location is null")
                    }
                }.addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to get user location", exception)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted", e)
        }
    }

    // Permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocationPermission) {
            // Get user location after permission is granted
            getUserLocation(fusedLocationClient)
        }
    }

    // State management
    var pickupStatus by remember { mutableStateOf<PickupStatus?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var statusCheckCount by remember { mutableStateOf(0) }
    var isPolling by remember { mutableStateOf(true) }

    // ETA and location data
    var etaData by remember { mutableStateOf<EtaResponse?>(null) }
    var sgrPickupData by remember { mutableStateOf<SgrPickupData?>(null) }
    var isLocationPolling by remember { mutableStateOf(false) }

    // Map state
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
    var driverMarker by remember { mutableStateOf<Marker?>(null) }
    var pickupMarker by remember { mutableStateOf<Marker?>(null) }
    var userMarker by remember { mutableStateOf<Marker?>(null) }

    // New state variables for address-based driver location
    var driverLocationCoords by remember { mutableStateOf<LatLng?>(null) }
    var isGeocodingDriver by remember { mutableStateOf(false) }
    var geocodingError by remember { mutableStateOf<String?>(null) }

    // Payment state
    var isProcessingPayment by remember { mutableStateOf(false) }
    var paymentError by remember { mutableStateOf<String?>(null) }

    // Request location permissions on launch
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            getUserLocation(fusedLocationClient)
        }
    }

    // Function to fetch pickup status
    suspend fun fetchPickupStatus(id: Long): PickupStatus? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("https://10.0.2.2:8443/api/sgrPickup/$id/status")
            Log.d(TAG, "Fetching pickup status from: $url")

            connection = url.openConnection() as HttpURLConnection

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
            Log.d(TAG, "Status API Response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val statusString = reader.use { it.readText() }.trim()
                    .removePrefix("\"").removeSuffix("\"") // Remove quotes if present
                Log.d(TAG, "Status string received: '$statusString'")

                PickupStatus(
                    id = id,
                    status = statusString
                )
            } else {
                Log.e(TAG, "Failed to fetch pickup status: HTTP $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching pickup status", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    // Updated function to fetch full sgrPickup data with userId extracted from user object
    suspend fun fetchSgrPickupData(id: Long): SgrPickupData? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("https://10.0.2.2:8443/api/sgrPickup/$id")
            Log.d(TAG, "🌐 Fetching sgrPickup data from: $url")

            connection = url.openConnection() as HttpURLConnection

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
            Log.d(TAG, "📊 SgrPickup API Response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.use { it.readText() }.trim()
                Log.d(TAG, "📝 SgrPickup response received: '$response'")

                if (response.isNotEmpty()) {
                    try {
                        val jsonObject = JSONObject(response)

                        // Extract userId from user object if it exists
                        val userId = if (jsonObject.has("user") && !jsonObject.isNull("user")) {
                            val userObject = jsonObject.getJSONObject("user")
                            if (userObject.has("id") && !userObject.isNull("id")) {
                                userObject.getLong("id")
                            } else {
                                Log.w(TAG, "⚠️ User object found but no id field")
                                null
                            }
                        } else {
                            // Fallback to direct userId field if user object doesn't exist
                            if (jsonObject.has("userId") && !jsonObject.isNull("userId")) {
                                jsonObject.getLong("userId")
                            } else {
                                Log.w(TAG, "⚠️ Neither user object nor userId field found")
                                null
                            }
                        }

                        // Extract driverId from driver object if it exists
                        val driverId =
                            if (jsonObject.has("driver") && !jsonObject.isNull("driver")) {
                                val driverObject = jsonObject.getJSONObject("driver")
                                if (driverObject.has("id") && !driverObject.isNull("id")) {
                                    driverObject.getLong("id")
                                } else {
                                    Log.w(TAG, "⚠️ Driver object found but no id field")
                                    null
                                }
                            } else {
                                // Fallback to direct driverId field if driver object doesn't exist
                                if (jsonObject.has("driverId") && !jsonObject.isNull("driverId")) {
                                    jsonObject.getLong("driverId")
                                } else {
                                    Log.w(TAG, "⚠️ Neither driver object nor driverId field found")
                                    null
                                }
                            }

                        val pickupData = SgrPickupData(
                            id = jsonObject.getLong("id"),
                            status = jsonObject.getString("status"),
                            driverLocation = if (jsonObject.has("driverLocation") && !jsonObject.isNull(
                                    "driverLocation"
                                )
                            )
                                jsonObject.getString("driverLocation") else null,
                            pickupLatitude = if (jsonObject.has("pickupLatitude") && !jsonObject.isNull(
                                    "pickupLatitude"
                                )
                            )
                                jsonObject.getDouble("pickupLatitude") else null,
                            pickupLongitude = if (jsonObject.has("pickupLongitude") && !jsonObject.isNull(
                                    "pickupLongitude"
                                )
                            )
                                jsonObject.getDouble("pickupLongitude") else null,
                            pickupLocation = if (jsonObject.has("pickupLocation") && !jsonObject.isNull(
                                    "pickupLocation"
                                )
                            )
                                jsonObject.getString("pickupLocation") else null,
                            sackSizeLiters = if (jsonObject.has("sackSizeLiters") && !jsonObject.isNull(
                                    "sackSizeLiters"
                                )
                            )
                                jsonObject.getInt("sackSizeLiters") else null,
                            userId = userId, // Use the extracted userId
                            driverId = driverId, // Use the extracted driverId
                            cardId = if (jsonObject.has("cardId") && !jsonObject.isNull("cardId"))
                                jsonObject.getLong("cardId") else null
                        )
                        Log.d(
                            TAG,
                            "✅ SgrPickup data parsed: driverAddress='${pickupData.driverLocation}', pickup(${pickupData.pickupLatitude}, ${pickupData.pickupLongitude}), userId=$userId, driverId=$driverId"
                        )
                        return@withContext pickupData
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error parsing JSON response", e)
                        return@withContext null
                    }
                } else {
                    Log.w(TAG, "⚠️ Empty response received")
                    return@withContext null
                }
            } else {
                Log.e(TAG, "❌ Failed to fetch sgrPickup data: HTTP $responseCode")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception fetching sgrPickup data", e)
            return@withContext null
        } finally {
            connection?.disconnect()
            Log.d(TAG, "🔌 HTTP connection closed")
        }
    }


    // Updated function to fetch full sgrPickup data with all fields needed for payment


    // Function to send payment request
    suspend fun sendPaymentRequest(pickupData: SgrPickupData): Boolean =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://10.0.2.2:8443/api/sgrPickup/${pickupData.id}/pay")
                Log.d(TAG, "💳 Sending payment request to: $url")

                // Create the DTO object
                val paymentDto = SgrPickupDto(
                    driverLocation = pickupData.driverLocation ?: "",
                    pickupLocation = pickupData.pickupLocation ?: "",
                    sackSizeLiters = pickupData.sackSizeLiters ?: 0,
                    userId = pickupData.userId ?: 0,
                    driverId = pickupData.driverId ?: 0,
                    cardId = pickupData.cardId ?: 0
                )

                // Convert to JSON
                val jsonPayload = JSONObject().apply {
                    put("driverLocation", paymentDto.driverLocation)
                    put("pickupLocation", paymentDto.pickupLocation)
                    put("sackSizeLiters", paymentDto.sackSizeLiters)
                    put("userId", paymentDto.userId)
                    put("driverId", paymentDto.driverId)
                    put("cardId", paymentDto.cardId)
                }.toString()

                Log.d(TAG, "💳 Payment payload: $jsonPayload")

                connection = url.openConnection() as HttpURLConnection

                if (connection is HttpsURLConnection) {
                    configureSSLForDevelopment(connection)
                }

                connection.apply {
                    requestMethod = "PUT"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                }

                // Write the JSON payload
                val outputWriter = OutputStreamWriter(connection.outputStream)
                outputWriter.write(jsonPayload)
                outputWriter.flush()
                outputWriter.close()

                val responseCode = connection.responseCode
                Log.d(TAG, "💳 Payment API Response code: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                    Log.d(TAG, "✅ Payment request successful")
                    true
                } else {
                    Log.e(TAG, "❌ Payment request failed: HTTP $responseCode")
                    // Try to read error response
                    val errorReader = BufferedReader(
                        InputStreamReader(
                            connection.errorStream ?: connection.inputStream
                        )
                    )
                    val errorResponse = errorReader.use { it.readText() }
                    Log.e(TAG, "❌ Payment error response: $errorResponse")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "💥 Exception during payment request", e)
                false
            } finally {
                connection?.disconnect()
            }
        }

    fun parseEtaResponse(jsonString: String): EtaResponse? {
        return try {
            val jsonObject = JSONObject(jsonString)
            EtaResponse(
                estimatedTime = if (jsonObject.has("estimatedTime")) jsonObject.getString("estimatedTime") else null,
                distance = if (jsonObject.has("distance")) jsonObject.getString("distance") else null,
                driverLocation = null, // Will be populated from sgrPickupData
                pickupLocation = null   // Will be populated from sgrPickupData
            )
        } catch (e: Exception) {
            Log.e("PickupTrackingScreen", "Error parsing ETA JSON", e)
            null
        }
    }

    // Updated fetchEtaData function
    suspend fun fetchEtaData(id: Long): EtaResponse? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("https://10.0.2.2:8443/api/sgrPickup/$id/eta")
            Log.d(TAG, "🌐 Fetching ETA data from: $url")

            connection = url.openConnection() as HttpURLConnection

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
            Log.d(TAG, "📊 ETA API Response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.use { it.readText() }.trim()
                Log.d(TAG, "📝 ETA response received: '$response'")

                if (response.isNotEmpty()) {
                    val parsedEta = parseEtaResponse(response)
                    Log.d(
                        TAG,
                        "✅ ETA Response parsed successfully: ${parsedEta?.estimatedTime} (${parsedEta?.distance})"
                    )
                    return@withContext parsedEta
                } else {
                    Log.w(TAG, "⚠️ Empty ETA response received")
                    return@withContext null
                }
            } else {
                Log.e(TAG, "❌ Failed to fetch ETA data: HTTP $responseCode")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception fetching ETA data", e)
            return@withContext null
        } finally {
            connection?.disconnect()
            Log.d(TAG, "🔌 HTTP connection closed")
        }
    }

    // Updated map marker effect to handle address-based driver location
    LaunchedEffect(sgrPickupData, userLocation, googleMap) {
        Log.d(
            TAG,
            "🗺️ Map marker effect triggered - googleMap: ${googleMap != null}, sgrPickupData: ${sgrPickupData != null}, userLocation: ${userLocation != null}"
        )

        if (googleMap != null) {
            // Update user location marker
            userLocation?.let { userLoc ->
                if (userMarker == null) {
                    userMarker = googleMap!!.addMarker(
                        MarkerOptions()
                            .position(userLoc)
                            .title("Your Location")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                    )
                    Log.d(TAG, "👤 User marker added at: ${userLoc.latitude}, ${userLoc.longitude}")
                } else {
                    userMarker!!.position = userLoc
                    Log.d(
                        TAG,
                        "👤 User marker updated to: ${userLoc.latitude}, ${userLoc.longitude}"
                    )
                }
            }

            // Update markers based on sgrPickupData
            sgrPickupData?.let { pickup ->
                Log.d(TAG, "📊 Processing pickup data - status: ${pickup.status}")
                Log.d(TAG, "📍 Driver address: '${pickup.driverLocation}'")
                Log.d(TAG, "📍 Pickup location: ${pickup.pickupLatitude}, ${pickup.pickupLongitude}")

                // Handle driver location (geocode address if available)
                if (!pickup.driverLocation.isNullOrBlank()) {
                    // Launch geocoding in a separate coroutine
                    scope.launch {
                        isGeocodingDriver = true
                        geocodingError = null

                        val driverCoords = geocodeAddress(pickup.driverLocation)

                        isGeocodingDriver = false

                        if (driverCoords != null) {
                            driverLocationCoords = driverCoords

                            // Update driver marker on main thread
                            withContext(Dispatchers.Main) {
                                if (driverMarker == null) {
                                    driverMarker = googleMap!!.addMarker(
                                        MarkerOptions()
                                            .position(driverCoords)
                                            .title("Driver Location")
                                            .snippet(pickup.driverLocation)
                                            .icon(
                                                BitmapDescriptorFactory.defaultMarker(
                                                    BitmapDescriptorFactory.HUE_BLUE
                                                )
                                            )
                                    )
                                    Log.d(
                                        TAG,
                                        "🚗 Driver marker added at: ${driverCoords.latitude}, ${driverCoords.longitude} for address: ${pickup.driverLocation}"
                                    )
                                } else {
                                    driverMarker!!.position = driverCoords
                                    driverMarker!!.snippet = pickup.driverLocation
                                    Log.d(
                                        TAG,
                                        "🚗 Driver marker updated to: ${driverCoords.latitude}, ${driverCoords.longitude} for address: ${pickup.driverLocation}"
                                    )
                                }
                            }
                        } else {
                            geocodingError =
                                "Could not locate driver address: ${pickup.driverLocation}"
                            Log.w(
                                TAG,
                                "⚠️ Failed to geocode driver address: ${pickup.driverLocation}"
                            )
                        }
                    }
                } else {
                    Log.d(TAG, "⚠️ Driver address not available yet")
                    // Remove driver marker if address is not available
                    driverMarker?.remove()
                    driverMarker = null
                    driverLocationCoords = null
                }

                // Update pickup location marker
                if (pickup.pickupLatitude != null && pickup.pickupLongitude != null) {
                    val pickupLoc = LatLng(pickup.pickupLatitude, pickup.pickupLongitude)
                    if (pickupMarker == null) {
                        pickupMarker = googleMap!!.addMarker(
                            MarkerOptions()
                                .position(pickupLoc)
                                .title("Pickup Location")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                        )
                        Log.d(
                            TAG,
                            "📦 Pickup marker added at: ${pickupLoc.latitude}, ${pickupLoc.longitude}"
                        )
                    } else {
                        // Pickup location shouldn't change, but update just in case
                        pickupMarker!!.position = pickupLoc
                        Log.d(
                            TAG,
                            "📦 Pickup marker position confirmed at: ${pickupLoc.latitude}, ${pickupLoc.longitude}"
                        )
                    }
                }
            } ?: Log.d(TAG, "⚠️ No sgrPickupData available yet")

            // Adjust camera to show all available markers
            val markersToShow = mutableListOf<LatLng>()
            userLocation?.let { markersToShow.add(it) }
            driverLocationCoords?.let { markersToShow.add(it) }
            sgrPickupData?.let { pickup ->
                pickup.pickupLatitude?.let { lat ->
                    pickup.pickupLongitude?.let { lng ->
                        markersToShow.add(LatLng(lat, lng))
                    }
                }
            }

            Log.d(TAG, "📷 Markers to show: ${markersToShow.size}")
            if (markersToShow.size > 1) {
                val bounds = LatLngBounds.builder()
                markersToShow.forEach { bounds.include(it) }
                googleMap!!.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds.build(), 100)
                )
                Log.d(TAG, "📷 Camera adjusted to show ${markersToShow.size} markers")
            } else if (markersToShow.size == 1) {
                googleMap!!.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(markersToShow[0], 15f)
                )
                Log.d(TAG, "📷 Camera focused on single marker")
            }
        } else {
            Log.d(TAG, "⚠️ GoogleMap not ready yet")
        }
    }

// Status polling effect with payment integration and navigation
    LaunchedEffect(pickupId) {
        scope.launch {
            while (isPolling) {
                try {
                    statusCheckCount++
                    Log.d(TAG, "Status check #$statusCheckCount for pickup ID: $pickupId")

                    val status = fetchPickupStatus(pickupId)

                    if (status != null) {
                        pickupStatus = status
                        isLoading = false
                        error = null
                        paymentError = null

                        // Start location polling when status becomes in_progress
                        Log.d(
                            TAG,
                            "Current status: '${status.status}', lowercase: '${status.status.lowercase()}', isLocationPolling: $isLocationPolling"
                        )
                        if (status.status.lowercase() == "in_progress" && !isLocationPolling) {
                            isLocationPolling = true
                            Log.d(
                                TAG,
                                "✅ Starting location polling - status changed to IN_PROGRESS"
                            )
                        }

                        // Check if pickup is completed or has a final status
                        when (status.status.lowercase()) {
                            "completed", "delivered", "finished", "cancelled", "canceled" -> {
                                Log.d(TAG, "🏁 Pickup completed with status: ${status.status}")
                                isPolling = false
                                isLocationPolling = false

                                // Trigger payment process for completed pickups
                                if (status.status.lowercase() in listOf(
                                        "completed",
                                        "delivered",
                                        "finished"
                                    )
                                ) {
                                    sgrPickupData?.let { pickupData ->
                                        Log.d(TAG, "💳 Initiating payment for completed pickup")
                                        isProcessingPayment = true
                                        paymentError = null

                                        val paymentSuccess = sendPaymentRequest(pickupData)

                                        isProcessingPayment = false

                                        if (paymentSuccess) {
                                            Log.d(
                                                TAG,
                                                "✅ Payment processed successfully - navigating to completed screen"
                                            )
                                            // Navigate to PickupCompletedScreen after successful payment
                                            navController.navigate("pickup_completed_screen/$pickupId") {
                                                popUpTo("pickup_loading_screen/$pickupId") {
                                                    inclusive = true
                                                }
                                            }
                                        } else {
                                            Log.e(TAG, "❌ Payment processing failed")
                                            paymentError =
                                                "Payment processing failed. Please try again."
                                        }
                                    } ?: run {
                                        Log.e(TAG, "❌ Cannot process payment - pickup data is null")
                                        paymentError =
                                            "Cannot process payment - pickup data unavailable"
                                    }
                                }
                            }

                            else -> {
                                Log.d(TAG, "📊 Status: ${status.status} - continuing polling")
                            }
                        }
                    } else {
                        error = "Failed to fetch pickup status"
                        Log.e(TAG, "Failed to fetch pickup status for ID: $pickupId")
                    }
                } catch (e: Exception) {
                    error = "Network error: ${e.message}"
                    Log.e(TAG, "Exception in status polling", e)
                }

                // Continue polling if still active
                if (isPolling && isActive) {
                    delay(5000) // Poll every 5 seconds
                }
            }
        }
    }

// Location polling effect
    LaunchedEffect(isLocationPolling) {
        if (isLocationPolling) {
            Log.d(TAG, "🌍 Starting location polling loop")
            scope.launch {
                while (isLocationPolling && isActive) {
                    try {
                        Log.d(TAG, "🔄 Fetching location data...")

                        // Fetch both ETA and pickup data
                        val eta = fetchEtaData(pickupId)
                        val pickup = fetchSgrPickupData(pickupId)

                        if (eta != null) {
                            etaData = eta
                            Log.d(TAG, "⏱️ ETA updated: ${eta.estimatedTime} (${eta.distance})")
                        }

                        if (pickup != null) {
                            sgrPickupData = pickup
                            Log.d(
                                TAG,
                                "📊 Pickup data updated: status=${pickup.status}, driverLocation=${pickup.driverLocation}"
                            )

                            // Check if status changed to completed during location polling
                            if (pickup.status.lowercase() in listOf(
                                    "completed",
                                    "delivered",
                                    "finished",
                                    "cancelled",
                                    "canceled"
                                )
                            ) {
                                Log.d(TAG, "🏁 Pickup completed during location polling - stopping")
                                isLocationPolling = false
                                isPolling = false

                                // Process payment for completed pickups
                                if (pickup.status.lowercase() in listOf(
                                        "completed",
                                        "delivered",
                                        "finished"
                                    )
                                ) {
                                    Log.d(TAG, "💳 Processing payment for completed pickup")
                                    isProcessingPayment = true
                                    paymentError = null

                                    val paymentSuccess = sendPaymentRequest(pickup)

                                    isProcessingPayment = false

                                    if (paymentSuccess) {
                                        Log.d(
                                            TAG,
                                            "✅ Payment processed successfully - navigating to completed screen"
                                        )
                                        // Navigate to PickupCompletedScreen after successful payment
                                        navController.navigate("pickup_completed_screen/$pickupId") {
                                            popUpTo("pickup_loading_screen/$pickupId") {
                                                inclusive = true
                                            }
                                        }
                                    } else {
                                        Log.e(TAG, "❌ Payment processing failed")
                                        paymentError =
                                            "Payment processing failed. Please try again."
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "💥 Exception in location polling", e)
                    }

                    // Wait before next poll
                    if (isLocationPolling && isActive) {
                        delay(10000) // Poll every 10 seconds for location data
                    }
                }
            }
        } else {
            Log.d(TAG, "⏹️ Location polling stopped")
        }
    }

// UI
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Full-screen Map
        if (hasLocationPermission) {
            AndroidView(
                factory = { context ->
                    MapView(context).apply {
                        onCreate(null)
                        onResume()
                        getMapAsync { map ->
                            googleMap = map
                            map.uiSettings.isZoomControlsEnabled = true
                            map.uiSettings.isMyLocationButtonEnabled = true

                            try {
                                map.isMyLocationEnabled = hasLocationPermission
                            } catch (e: SecurityException) {
                                Log.e(TAG, "Location permission not granted for map", e)
                            }

                            Log.d(TAG, "🗺️ Google Map initialized")
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Location permission required screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOff,
                        contentDescription = "Location Permission Required",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Location permission required",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    ) {
                        Text("Grant Permission")
                    }
                }
            }
        }

        // Bottom Drawer/Overlay
        var isDrawerExpanded by remember { mutableStateOf(false) }

        // Draggable bottom sheet/drawer
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            // Drawer handle/header (always visible)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isDrawerExpanded = !isDrawerExpanded },
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(2.dp)
                            )
                            .align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Pickup ID and Status (always visible)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocalShipping,
                                contentDescription = "Pickup",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pickup #$pickupId",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Status indicator
                        when {
                            isLoading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }

                            error != null -> {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            pickupStatus != null -> {
                                val status = pickupStatus!!.status
                                val statusColor = when (status.lowercase()) {
                                    "pending" -> Color(0xFFFF9800) // Orange
                                    "in_progress" -> Color(0xFF2196F3) // Blue
                                    "completed", "delivered", "finished" -> Color(0xFF4CAF50) // Green
                                    "cancelled", "canceled" -> Color(0xFFF44336) // Red
                                    else -> MaterialTheme.colorScheme.onSurface
                                }

                                Text(
                                    text = status.uppercase(),
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }

                    // ETA info (always visible when available)
                    if (isLocationPolling && etaData != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "ETA",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            etaData!!.estimatedTime?.let { eta ->
                                Text(
                                    text = "ETA: $eta",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            etaData!!.distance?.let { distance ->
                                Text(
                                    text = " • $distance",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Expand/collapse indicator
                    Spacer(modifier = Modifier.height(8.dp))
                    Icon(
                        imageVector = if (isDrawerExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isDrawerExpanded) "Collapse" else "Expand",
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expandable content
            androidx.compose.animation.AnimatedVisibility(
                visible = isDrawerExpanded,
                enter = androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .heightIn(max = 400.dp) // Limit max height
                            .verticalScroll(rememberScrollState()) // Make scrollable if content is too long
                    ) {
                        // Detailed Status Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = "Status Details",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                when {
                                    isLoading -> {
                                        Text(
                                            "Loading status...",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }

                                    error != null -> {
                                        Text(
                                            text = error!!,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }

                                    pickupStatus != null -> {
                                        Text(
                                            text = "Check #$statusCheckCount",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // ETA Details (when expanded)
                        if (isLocationPolling && etaData != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = "Arrival Information",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))

                                    etaData!!.estimatedTime?.let { eta ->
                                        Text(
                                            text = "Estimated Time: $eta",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }

                                    etaData!!.distance?.let { distance ->
                                        Text(
                                            text = "Distance: $distance",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Geocoding status
                        if (isGeocodingDriver) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Locating driver...",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        // Geocoding error
                        geocodingError?.let { error ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Text(
                                    text = error,
                                    modifier = Modifier.padding(12.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        // Payment processing indicator
                        if (isProcessingPayment) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Processing payment...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        // Payment error
                        paymentError?.let { error ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = "Payment Error",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = error,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    // Retry payment button
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            sgrPickupData?.let { pickupData ->
                                                scope.launch {
                                                    isProcessingPayment = true
                                                    paymentError = null

                                                    val paymentSuccess =
                                                        sendPaymentRequest(pickupData)

                                                    if (!paymentSuccess) {
                                                        paymentError =
                                                            "Payment processing failed. Please try again."
                                                    }

                                                    isProcessingPayment = false
                                                }
                                            }
                                        },
                                        enabled = sgrPickupData != null && !isProcessingPayment,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Retry Payment")
                                    }
                                }
                            }
                        }

                        // Back button
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Back")
                        }
                    }
                }
            }
        }
    }
}