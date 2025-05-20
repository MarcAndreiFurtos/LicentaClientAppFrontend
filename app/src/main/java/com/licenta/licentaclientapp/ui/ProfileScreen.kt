package com.licenta.licentaclientapp.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavHostController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State to hold the selected image
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // State to track if the upload menu is open
    var showUploadMenu by remember { mutableStateOf(false) }

    // Image picker launcher
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        // Configure decoder to use ARGB_8888 instead of HARDWARE
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

                // Process and store the bitmap as requested
                val hexString = convertBitmapToHexString(bitmap)

                // Store the hex string in preferences
                val sharedPrefs = context.getSharedPreferences("profile_preferences", 0)
                sharedPrefs.edit().putString("profile_picture_hex", hexString).apply()
            }
        }
        showUploadMenu = false
    }

    // Load saved profile picture on init
    LaunchedEffect(Unit) {
        val sharedPrefs = context.getSharedPreferences("profile_preferences", 0)
        val savedHexString = sharedPrefs.getString("profile_picture_hex", null)

        savedHexString?.let {
            val bitmap = convertHexStringToBitmap(it)
            selectedImageBitmap = bitmap
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
            // Profile Picture (Clickable)
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
                    // Show the selected image
                    Image(
                        bitmap = selectedImageBitmap!!.asImageBitmap(),
                        contentDescription = "Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Show default icon
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile Picture",
                        modifier = Modifier.size(64.dp),
                        tint = Color.White
                    )
                }
            }

            // Upload menu dropdown
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

// Function to convert bitmap to hex string
private fun convertBitmapToHexString(bitmap: Bitmap): String {
    // Ensure bitmap is in a format that supports getPixels()
    val mutableBitmap = if (Build.VERSION.SDK_INT >= 26 && bitmap.config?.toString() == "HARDWARE") {
        bitmap.copy(Bitmap.Config.ARGB_8888, true)
    } else {
        if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }

    // Resize bitmap if too large to avoid memory issues
    val resizedBitmap = if (mutableBitmap.width > 300 || mutableBitmap.height > 300) {
        val ratio = mutableBitmap.width.toFloat() / mutableBitmap.height.toFloat()
        val width = 300
        val height = (width / ratio).toInt()
        Bitmap.createScaledBitmap(mutableBitmap, width, height, true)
    } else {
        mutableBitmap
    }

    // Create array of pixels
    val pixels = IntArray(resizedBitmap.width * resizedBitmap.height)
    resizedBitmap.getPixels(pixels, 0, resizedBitmap.width, 0, 0, resizedBitmap.width, resizedBitmap.height)

    // Convert to RGB values without commas and parentheses
    val rgbValues = StringBuilder()
    for (pixel in pixels) {
        val r = (pixel shr 16) and 0xff
        val g = (pixel shr 8) and 0xff
        val b = pixel and 0xff

        // Just append the numeric values without separators
        rgbValues.append(r).append(g).append(b)
    }

    // Convert to hex string
    return rgbValues.toString().chunked(2).joinToString("") {
        it.toIntOrNull()?.toString(16)?.padStart(2, '0') ?: "00"
    }
}

// Function to convert hex string back to bitmap
private fun convertHexStringToBitmap(hexString: String): Bitmap? {
    try {
        // Convert hex pairs back to integers
        val values = hexString.chunked(2).map { it.toInt(16) }

        // Group into RGB triplets
        val pixels = values.chunked(3).map { (r, g, b) ->
            (0xff shl 24) or (r shl 16) or (g shl 8) or b
        }.toIntArray()

        // Calculate dimensions (assuming square for simplicity)
        val size = kotlin.math.sqrt(pixels.size.toDouble()).toInt()

        // Create bitmap
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        return null
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