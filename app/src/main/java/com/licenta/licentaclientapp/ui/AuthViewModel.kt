package com.licenta.licentaclientapp.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.licenta.licentaclientapp.auth.Auth0Manager
import com.licenta.licentaclientapp.auth.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader
import org.json.JSONObject
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier

class AuthViewModel : ViewModel() {
    private val TAG = "AuthViewModel"
    private var auth0Manager: Auth0Manager? = null

    // Authentication state
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // User profile state
    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    // API user data state
    private val _apiUserData = MutableStateFlow<String?>(null)
    val apiUserData: StateFlow<String?> = _apiUserData.asStateFlow()

    // Navigation state for 404 handling
    private val _shouldNavigateToRegistration = MutableStateFlow(false)
    val shouldNavigateToRegistration: StateFlow<Boolean> = _shouldNavigateToRegistration.asStateFlow()

    // Initialize with context
    fun initialize(context: Context) {
        if (auth0Manager == null) {
            Log.d(TAG, "Initializing AuthViewModel and Auth0Manager")
            auth0Manager = Auth0Manager(context)

            // Collect authentication state from Auth0Manager
            viewModelScope.launch {
                auth0Manager?.isAuthenticated?.collect { isAuth ->
                    Log.d(TAG, "Auth state changed: $isAuth")
                    _isAuthenticated.value = isAuth

                    // If user just authenticated, fetch their data from API
                    if (isAuth) {
                        fetchUserDataFromAPI()
                    } else {
                        // Clear navigation flag when user logs out
                        _shouldNavigateToRegistration.value = false
                    }
                }
            }

            // Collect loading state from Auth0Manager
            viewModelScope.launch {
                auth0Manager?.isLoading?.collect { isLoading ->
                    _isLoading.value = isLoading
                }
            }

            // Collect error state from Auth0Manager
            viewModelScope.launch {
                auth0Manager?.error?.collect { error ->
                    _error.value = error
                }
            }

            // Collect user profile from Auth0Manager
            viewModelScope.launch {
                auth0Manager?.userProfile?.collect { profile ->
                    _userProfile.value = profile
                    // When user profile is updated and user is authenticated, fetch API data
                    if (profile != null && _isAuthenticated.value) {
                        fetchUserDataFromAPI()
                    }
                }
            }
        } else {
            Log.d(TAG, "AuthViewModel already initialized")
        }
    }

    // Login function
    fun login(context: Context, onComplete: (Boolean) -> Unit) {
        Log.d(TAG, "Login requested")
        if (auth0Manager == null) {
            Log.e(TAG, "Auth0Manager not initialized")
            _error.value = "Authentication service not initialized"
            onComplete(false)
            return
        }

        viewModelScope.launch {
            auth0Manager?.login(context) { success ->
                Log.d(TAG, "Login result: $success")
                if (success) {
                    // API call will be triggered by the authentication state change
                    Log.d(TAG, "Login successful, API data will be fetched automatically")
                }
                onComplete(success)
            }
        }
    }

    // Logout function
    fun logout(context: Context, onComplete: () -> Unit) {
        Log.d(TAG, "Logout requested")
        if (auth0Manager == null) {
            Log.e(TAG, "Auth0Manager not initialized")
            _error.value = "Authentication service not initialized"
            onComplete()
            return
        }

        viewModelScope.launch {
            auth0Manager?.logout(context) {
                Log.d(TAG, "Logout completed")
                // Clear API user data on logout
                _apiUserData.value = null
                // Clear navigation flag
                _shouldNavigateToRegistration.value = false
                onComplete()
            }
        }
    }

    // Fetch user data from API using email
    private fun fetchUserDataFromAPI() {
        val userEmail = _userProfile.value?.email
        if (userEmail.isNullOrEmpty()) {
            Log.w(TAG, "Cannot fetch user data: email not available")
            return
        }

        Log.d(TAG, "Fetching user data for email: $userEmail")

        viewModelScope.launch {
            try {
                val userData = getUserFromAPI(userEmail)
                _apiUserData.value = userData
                Log.d(TAG, "Successfully fetched user data from API")
                // Clear navigation flag if user data is successfully fetched
                _shouldNavigateToRegistration.value = false
            } catch (e: UserNotFoundException) {
                Log.w(TAG, "User not found in backend database, redirecting to registration")
                _shouldNavigateToRegistration.value = true
                _error.value = null // Don't show error for 404, just navigate
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching user data from API", e)
                _error.value = "Failed to fetch user data: ${e.message}"
                _shouldNavigateToRegistration.value = false
            }
        }
    }

    // Configure SSL for development (accepts self-signed certificates)
    private fun configureSSLForDevelopment(httpsConnection: HttpsURLConnection) {
        try {
            // Create a trust manager that accepts all certificates (DEV ONLY!)
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            // Install the all-trusting trust manager
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            httpsConnection.sslSocketFactory = sslContext.socketFactory

            // Create an all-trusting hostname verifier
            httpsConnection.hostnameVerifier = HostnameVerifier { _, _ -> true }

            Log.d(TAG, "SSL configured for development (accepts self-signed certificates)")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring SSL", e)
        }
    }

    // Make GET request to API
    private suspend fun getUserFromAPI(email: String): String = withContext(Dispatchers.IO) {
        val encodedEmail = java.net.URLEncoder.encode(email, "UTF-8")
        // FIXED: Use HTTPS URL to match your Spring Boot configuration
        val url = URL("https://10.0.2.2:8443/api/users/email/$encodedEmail")

        Log.d(TAG, "Making GET request to: $url")

        val connection = url.openConnection()

        // Cast to HttpURLConnection (works for both HTTP and HTTPS)
        val httpConnection = connection as HttpURLConnection

        // Check if it's HTTPS and configure SSL for development
        if (httpConnection is HttpsURLConnection) {
            configureSSLForDevelopment(httpConnection)
        }

        // Configure the connection
        httpConnection.apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        return@withContext try {
            val responseCode = httpConnection.responseCode
            Log.d(TAG, "API Response code: $responseCode")

            when (responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val reader = BufferedReader(InputStreamReader(httpConnection.inputStream))
                    val response = reader.use { it.readText() }
                    Log.d(TAG, "API Response: $response")
                    response
                }
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    Log.w(TAG, "User not found in backend database (404)")
                    throw UserNotFoundException("User not found in backend database")
                }
                else -> {
                    val errorReader = BufferedReader(InputStreamReader(httpConnection.errorStream ?: httpConnection.inputStream))
                    val errorResponse = errorReader.use { it.readText() }
                    Log.e(TAG, "API Error response: $errorResponse")
                    throw Exception("HTTP $responseCode: $errorResponse")
                }
            }
        } catch (e: UserNotFoundException) {
            Log.w(TAG, "User not found: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Network error: ${e.message}")
            throw e
        } finally {
            httpConnection.disconnect()
        }
    }

    // Clear any error
    fun clearError() {
        _error.value = null
    }

    // Clear API user data
    fun clearApiUserData() {
        _apiUserData.value = null
    }

    // Clear navigation flag (call this after navigation is handled)
    fun clearNavigationToRegistration() {
        _shouldNavigateToRegistration.value = false
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "AuthViewModel cleared")
    }
}

// Custom exception for user not found (404)
class UserNotFoundException(message: String) : Exception(message)