package com.licenta.licentaclientapp.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import org.json.JSONObject
import org.json.JSONArray
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier

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

        Log.d("ProfileScreen", "SSL configured for development")
    } catch (e: Exception) {
        Log.e("ProfileScreen", "Error configuring SSL", e)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavHostController,
    authViewModel: AuthViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val TAG = "ProfileScreen"

    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var showUploadMenu by remember { mutableStateOf(false) }

    var userName by remember { mutableStateOf("Loading...") }
    var userEmail by remember { mutableStateOf("Loading...") }
    var userId by remember { mutableStateOf<Long?>(null) }
    var isLoadingUserData by remember { mutableStateOf(true) }
    var userDataError by remember { mutableStateOf<String?>(null) }

    var isUploadingImage by remember { mutableStateOf(false) }
    var uploadMessage by remember { mutableStateOf<String?>(null) }

    var deliveryHistory by remember { mutableStateOf<List<DeliveryHistoryItem>>(emptyList()) }
    var isLoadingHistory by remember { mutableStateOf(false) }
    var historyError by remember { mutableStateOf<String?>(null) }

    suspend fun fetchDeliveryHistory(userId: Long): List<DeliveryHistoryItem> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://licenta-backend.westeurope.cloudapp.azure.com:8443/api/sgrPickup/$userId/history")
            Log.d(TAG, "Fetching delivery history from: $url")

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
            Log.d(TAG, "History API Response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.use { it.readText() }
                Log.d(TAG, "History data received: $response")

                val jsonArray = JSONArray(response)
                val historyList = mutableListOf<DeliveryHistoryItem>()

                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)

                    val historyItem = DeliveryHistoryItem(
                        id = jsonObject.optString("id", "Package #${jsonObject.optLong("pickupId",
                            (i + 1).toLong()
                        )}"),
                        date = jsonObject.optString("date", jsonObject.optString("pickupDate", "Unknown Date")),
                        status = jsonObject.optString("status", "Delivered"),
                        description = jsonObject.optString("description", ""),
                        location = jsonObject.optString("location", ""),
                        recipientName = jsonObject.optString("recipientName", "")
                    )
                    historyList.add(historyItem)
                }

                Log.d(TAG, "Parsed ${historyList.size} history items")
                historyList
            } else {
                val errorReader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
                val errorResponse = errorReader.use { it.readText() }
                Log.e(TAG, "Failed to fetch delivery history: HTTP $responseCode - $errorResponse")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching delivery history", e)
            emptyList()
        }
    }

    suspend fun uploadProfilePictureToBackend(userId: Long, encryptedImage: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://licenta-backend.westeurope.cloudapp.azure.com:8443/api/users/profilePicture")
            Log.d(TAG, "Uploading profile picture to: $url")

            val connection = url.openConnection() as HttpURLConnection

            if (connection is HttpsURLConnection) {
                configureSSLForDevelopment(connection)
            }

            connection.apply {
                requestMethod = "PUT"
                connectTimeout = 30000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val jsonPayload = JSONObject().apply {
                put("userId", userId)
                put("incriptedImmage", encryptedImage)
            }

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(jsonPayload.toString())
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            Log.d(TAG, "Profile picture upload response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.use { it.readText() }
                Log.d(TAG, "Profile picture upload response: $response")
                true
            } else {
                val errorReader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
                val errorResponse = errorReader.use { it.readText() }
                Log.e(TAG, "Failed to upload profile picture: HTTP $responseCode - $errorResponse")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading profile picture", e)
            false
        }
    }

    suspend fun fetchUserDataById(userId: Long): ProfileUser? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://licenta-backend.westeurope.cloudapp.azure.com:8443/api/users/$userId")
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
                ProfileUser(
                    id = jsonObject.optLong("id", -1L).takeIf { it != -1L },
                    email = jsonObject.getString("email"),
                    firstName = jsonObject.getString("firstName"),
                    lastName = jsonObject.getString("lastName"),
                    connectedAccount = jsonObject.optString("connectedAccount", ""),
                    profilePicture = jsonObject.optString("profilePicture", "")
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

    suspend fun getUserIdFromEmail(email: String): Long? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://licenta-backend.westeurope.cloudapp.azure.com:8443/api/users/email/$email")
            Log.d(TAG, "Fetching user ID from: $url")

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
            Log.d(TAG, "User ID API Response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.use { it.readText() }

                val jsonObject = JSONObject(response)
                jsonObject.optLong("id", -1L).takeIf { it != -1L }
            } else {
                Log.e(TAG, "Failed to fetch user ID: HTTP $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user ID", e)
            null
        }
    }

    fun decryptProfilePictureFromHex(hexString: String): Bitmap? {
        return if (hexString.isNotEmpty()) {
            convertHexStringToBitmap(hexString)
        } else {
            null
        }
    }

    suspend fun loadLocalProfilePicture() {
        try {
            val sharedPrefs = context.getSharedPreferences("profile_preferences", 0)
            val savedHexString = sharedPrefs.getString("profile_picture_hex", null)
            if (!savedHexString.isNullOrEmpty()) {
                Log.d(TAG, "Loading profile picture from local storage")
                val bitmap = convertHexStringToBitmap(savedHexString)
                if (bitmap != null) {
                    selectedImageBitmap = bitmap
                    Log.d(TAG, "Profile picture loaded from local storage")
                } else {
                    Log.e(TAG, "Failed to convert local profile picture")
                }
            } else {
                Log.d(TAG, "No local profile picture found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading local profile picture", e)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            decoder.isMutableRequired = true
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                }
                selectedImageBitmap = bitmap

                val hexString = convertBitmapToHexString(bitmap)

                val sharedPrefs = context.getSharedPreferences("profile_preferences", 0)
                sharedPrefs.edit().putString("profile_picture_hex", hexString).apply()

                userId?.let { id ->
                    isUploadingImage = true
                    uploadMessage = "Uploading profile picture..."

                    val uploadSuccess = uploadProfilePictureToBackend(id, hexString)

                    if (uploadSuccess) {
                        uploadMessage = "Profile picture uploaded successfully!"
                        Log.d(TAG, "Profile picture uploaded successfully")
                    } else {
                        uploadMessage = "Failed to upload profile picture"
                        Log.e(TAG, "Failed to upload profile picture")
                    }

                    isUploadingImage = false

                    kotlinx.coroutines.delay(3000)
                    uploadMessage = null
                } ?: run {
                    Log.w(TAG, "User ID not available, skipping backend upload")
                    uploadMessage = "Profile picture saved locally (backend upload skipped)"
                    kotlinx.coroutines.delay(3000)
                    uploadMessage = null
                }
            }
        }
        showUploadMenu = false
    }
    suspend fun clearLocalProfilePicture() {
        try {
            val sharedPrefs = context.getSharedPreferences("profile_preferences", 0)
            sharedPrefs.edit().remove("profile_picture_hex").apply()
            Log.d(TAG, "Local profile picture cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing local profile picture", e)
        }
    }
    LaunchedEffect(Unit) {
        authViewModel.userProfile.collect { userProfile ->
            if (userProfile != null && isLoadingUserData) {
                try {
                    Log.d(TAG, "Loading user data for email: ${userProfile.email}")

                    clearLocalProfilePicture()
                    selectedImageBitmap = null

                    val fetchedUserId = getUserIdFromEmail(userProfile.email ?: "")

                    if (fetchedUserId != null) {
                        userId = fetchedUserId
                        Log.d(TAG, "Found user ID: $fetchedUserId")

                        val userData = fetchUserDataById(fetchedUserId)

                        if (userData != null) {
                            userName = "${userData.firstName} ${userData.lastName}".trim()
                            userEmail = userData.email

                            Log.d(TAG, "User data loaded: $userName, Profile picture length: ${userData.profilePicture.length}")

                            if (userData.profilePicture.isNotEmpty()) {
                                Log.d(TAG, "Loading profile picture from backend")
                                val profileBitmap = decryptProfilePictureFromHex(userData.profilePicture)
                                if (profileBitmap != null) {
                                    selectedImageBitmap = profileBitmap
                                    Log.d(TAG, "Profile picture loaded successfully from backend")

                                    val hexString = convertBitmapToHexString(profileBitmap)
                                    val sharedPrefs = context.getSharedPreferences("profile_preferences", 0)
                                    sharedPrefs.edit().putString("profile_picture_hex", hexString).apply()
                                    Log.d(TAG, "Fresh profile picture saved to local storage")
                                } else {
                                    Log.e(TAG, "Failed to convert profile picture from backend")
                                    selectedImageBitmap = null
                                }
                            } else {
                                Log.d(TAG, "No profile picture available in backend")
                                selectedImageBitmap = null
                            }

                            userDataError = null

                            isLoadingHistory = true
                            historyError = null

                            Log.d(TAG, "Fetching delivery history for user ID: $fetchedUserId")
                            val history = fetchDeliveryHistory(fetchedUserId)

                            if (history.isNotEmpty()) {
                                deliveryHistory = history
                                Log.d(TAG, "Loaded ${history.size} delivery history items")
                            } else {
                                historyError = "No delivery history found"
                                Log.d(TAG, "No delivery history found for user")
                            }

                            isLoadingHistory = false
                        } else {
                            userDataError = "Failed to load user data"
                            userName = userProfile.name ?: "Unknown User"
                            userEmail = userProfile.email ?: "Unknown Email"
                            selectedImageBitmap = null
                            isLoadingHistory = false
                        }
                    } else {
                        userDataError = "User not found"
                        userName = userProfile.name ?: "Unknown User"
                        userEmail = userProfile.email ?: "Unknown Email"
                        selectedImageBitmap = null
                        isLoadingHistory = false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading user data", e)
                    userDataError = "Error loading user data: ${e.message}"
                    userName = userProfile.name ?: "Unknown User"
                    userEmail = userProfile.email ?: "Unknown Email"
                    selectedImageBitmap = null
                    isLoadingHistory = false
                } finally {
                    isLoadingUserData = false
                }
            }
        }
    }

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
            Box(
                modifier = Modifier
                    .padding(vertical = 24.dp)
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.LightGray)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { showUploadMenu = true },
                contentAlignment = Alignment.Center
            ) {
                if (selectedImageBitmap != null) {
                    Image(
                        bitmap = selectedImageBitmap!!.asImageBitmap(),
                        contentDescription = "Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile Picture",
                        modifier = Modifier.size(64.dp),
                        tint = Color.White
                    )
                }

                if (isUploadingImage) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            uploadMessage?.let { message ->
                Text(
                    text = message,
                    fontSize = 14.sp,
                    color = if (message.contains("success")) Color.Green else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (showUploadMenu) {
                AlertDialog(
                    onDismissRequest = { showUploadMenu = false },
                    title = { Text("Upload Profile Picture") },
                    text = { Text("Choose a new profile picture from your gallery") },
                    confirmButton = {
                        Button(
                            onClick = {
                                imagePicker.launch("image/*")
                            }
                        ) {
                            Text("Choose Image")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUploadMenu = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (isLoadingUserData) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                Text(
                    text = "Loading user information...",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            } else {
                if (userDataError != null) {
                    Text(
                        text = "⚠️ $userDataError",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Text(
                    text = userName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = userEmail,
                    fontSize = 16.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }

            Text(
                text = "Delivery History",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textAlign = TextAlign.Left
            )

            when {
                isLoadingHistory -> {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    Text(
                        text = "Loading delivery history...",
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                }
                historyError != null -> {
                    Text(
                        text = historyError!!,
                        fontSize = 16.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
                deliveryHistory.isEmpty() -> {
                    Text(
                        text = "No delivery history available",
                        fontSize = 16.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
                else -> {
                    LazyColumn {
                        items(deliveryHistory) { delivery ->
                            DeliveryHistoryItemCard(delivery)
                        }
                    }
                }
            }
        }
    }
}

private fun convertBitmapToHexString(bitmap: Bitmap): String {
    val mutableBitmap = if (Build.VERSION.SDK_INT >= 26 && bitmap.config?.toString() == "HARDWARE") {
        bitmap.copy(Bitmap.Config.ARGB_8888, true)
    } else {
        if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }

    val resizedBitmap = if (mutableBitmap.width > 300 || mutableBitmap.height > 300) {
        val ratio = mutableBitmap.width.toFloat() / mutableBitmap.height.toFloat()
        val width = 300
        val height = (width / ratio).toInt()
        Bitmap.createScaledBitmap(mutableBitmap, width, height, true)
    } else {
        mutableBitmap
    }

    val width = resizedBitmap.width
    val height = resizedBitmap.height

    val pixels = IntArray(width * height)
    resizedBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val hexBuilder = StringBuilder()

    hexBuilder.append("${width}:${height}:")

    for (pixel in pixels) {
        hexBuilder.append(String.format("%08X", pixel))
    }

    return hexBuilder.toString()
}

private fun convertHexStringToBitmap(hexString: String): Bitmap? {
    try {
        if (hexString.isEmpty()) return null

        val parts = hexString.split(":", limit = 3)
        if (parts.size != 3) {
            Log.e("ProfileScreen", "Invalid hex string format - missing dimensions")
            return null
        }

        val width = parts[0].toInt()
        val height = parts[1].toInt()
        val pixelData = parts[2]

        if (width <= 0 || height <= 0 || width > 1000 || height > 1000) {
            Log.e("ProfileScreen", "Invalid dimensions: ${width}x${height}")
            return null
        }

        val expectedPixels = width * height
        val expectedHexLength = expectedPixels * 8

        if (pixelData.length != expectedHexLength) {
            Log.e("ProfileScreen", "Hex data length mismatch. Expected: $expectedHexLength, Got: ${pixelData.length}")
            return null
        }

        val pixels = IntArray(expectedPixels)

        for (i in 0 until expectedPixels) {
            val hexPixel = pixelData.substring(i * 8, (i + 1) * 8)
            pixels[i] = hexPixel.toLong(16).toInt()
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        Log.d("ProfileScreen", "Successfully converted hex to bitmap: ${width}x${height}")
        return bitmap
    } catch (e: Exception) {
        Log.e("ProfileScreen", "Error converting hex string to bitmap", e)
        return null
    }
}

data class ProfileUser(
    val id: Long? = null,
    val email: String,
    val firstName: String,
    val lastName: String,
    val connectedAccount: String,
    val profilePicture: String = ""
)

data class DeliveryHistoryItem(
    val id: String,
    val date: String,
    val status: String,
    val description: String = "",
    val location: String = "",
    val recipientName: String = ""
)

@Composable
fun DeliveryHistoryItemCard(delivery: DeliveryHistoryItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
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

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            when (delivery.status.lowercase()) {
                                "delivered" -> Color(0xFF4CAF50)
                                "in transit", "picked up" -> Color(0xFF2196F3)
                                "pending" -> Color(0xFFFF9800)
                                else -> Color(0xFF757575)
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

            if (delivery.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = delivery.description,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            if (delivery.location.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Location: ${delivery.location}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            if (delivery.recipientName.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Recipient: ${delivery.recipientName}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}