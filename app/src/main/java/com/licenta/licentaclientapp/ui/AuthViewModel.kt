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

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _apiUserData = MutableStateFlow<String?>(null)
    val apiUserData: StateFlow<String?> = _apiUserData.asStateFlow()

    private val _shouldNavigateToRegistration = MutableStateFlow(false)
    val shouldNavigateToRegistration: StateFlow<Boolean> = _shouldNavigateToRegistration.asStateFlow()

    fun initialize(context: Context) {
        if (auth0Manager == null) {
            Log.d(TAG, "Initializing AuthViewModel and Auth0Manager")
            auth0Manager = Auth0Manager(context)

            viewModelScope.launch {
                auth0Manager?.isAuthenticated?.collect { isAuth ->
                    Log.d(TAG, "Auth state changed: $isAuth")
                    _isAuthenticated.value = isAuth

                    if (isAuth) {
                        fetchUserDataFromAPI()
                    } else {
                        _shouldNavigateToRegistration.value = false
                    }
                }
            }

            viewModelScope.launch {
                auth0Manager?.isLoading?.collect { isLoading ->
                    _isLoading.value = isLoading
                }
            }

            viewModelScope.launch {
                auth0Manager?.error?.collect { error ->
                    _error.value = error
                }
            }

            viewModelScope.launch {
                auth0Manager?.userProfile?.collect { profile ->
                    _userProfile.value = profile
                    if (profile != null && _isAuthenticated.value) {
                        fetchUserDataFromAPI()
                    }
                }
            }
        } else {
            Log.d(TAG, "AuthViewModel already initialized")
        }
    }

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
                    Log.d(TAG, "Login successful, API data will be fetched automatically")
                }
                onComplete(success)
            }
        }
    }

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
                _apiUserData.value = null
                _shouldNavigateToRegistration.value = false
                onComplete()
            }
        }
    }

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
                _shouldNavigateToRegistration.value = false
            } catch (e: UserNotFoundException) {
                Log.w(TAG, "User not found in backend database, redirecting to registration")
                _shouldNavigateToRegistration.value = true
                _error.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching user data from API", e)
                _error.value = "Failed to fetch user data: ${e.message}"
                _shouldNavigateToRegistration.value = false
            }
        }
    }

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

            Log.d(TAG, "SSL configured for development (accepts self-signed certificates)")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring SSL", e)
        }
    }

    private suspend fun getUserFromAPI(email: String): String = withContext(Dispatchers.IO) {
        val encodedEmail = java.net.URLEncoder.encode(email, "UTF-8")
        val url = URL("https://licenta-backend.westeurope.cloudapp.azure.com:8443/api/users/email/$encodedEmail")

        Log.d(TAG, "Making GET request to: $url")

        val connection = url.openConnection()

        val httpConnection = connection as HttpURLConnection

        if (httpConnection is HttpsURLConnection) {
            configureSSLForDevelopment(httpConnection)
        }

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

    fun clearError() {
        _error.value = null
    }

    fun clearApiUserData() {
        _apiUserData.value = null
    }

    fun clearNavigationToRegistration() {
        _shouldNavigateToRegistration.value = false
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "AuthViewModel cleared")
    }
}

class UserNotFoundException(message: String) : Exception(message)