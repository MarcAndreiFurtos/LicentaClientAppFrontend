
package com.licenta.licentaclientapp.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
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
    scope: CoroutineScope,
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