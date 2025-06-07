package com.licenta.licentaclientapp.auth

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.callback.Callback
import com.auth0.android.provider.WebAuthProvider
import com.auth0.android.result.Credentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class Auth0Manager(context: Context) {
    private val applicationContext = context.applicationContext
    private val TAG = "Auth0Manager"

    // Client ID and domain for Auth0
    private val clientId = "HDOLkDs1fTI88EG6TODypOHIBlAzpddA"
    private val domain = "dev-kotvcrcjprj3uksp.us.auth0.com"

    // Scheme for Auth0 callback
    private val scheme = "demo"

    // Set up Auth0 client
    private val auth0 = Auth0(clientId, domain)

    // State for login status
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    // State for user profile
    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    // State for loading status
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Access token
    private var accessToken: String? = null

    // ID token
    private var idToken: String? = null

    // Initialize auth state
    init {
        Log.d(TAG, "Auth0Manager initialized")
    }

    /**
     * Login function - handles Auth0 authentication
     */
    suspend fun login(context: Context, callback: (Boolean) -> Unit) {
        try {
            if (context !is ComponentActivity) {
                Log.e(TAG, "Context must be a ComponentActivity")
                _error.value = "Invalid context for authentication"
                callback(false)
                return
            }

            _isLoading.value = true
            _error.value = null
            Log.d(TAG, "Starting login process")

            withContext(Dispatchers.Main) {
                // Audience URL for API access
                val audience = "https://$domain/api/v2/"
                Log.d(TAG, "Using audience: $audience")

                // Start the web authentication process with more specific configuration
                WebAuthProvider.login(auth0)
                    .withScheme(scheme)
                    .withScope("openid profile email")
                    .withAudience(audience)
                    .withParameters(mapOf("prompt" to "login"))
                    .start(context, object : Callback<Credentials, AuthenticationException> {
                        override fun onSuccess(result: Credentials) {
                            Log.d(TAG, "Login successful")

                            // Log token information (partial for security)
                            val accessTokenPreview = result.accessToken?.take(10) ?: "null"
                            val idTokenPreview = result.idToken?.take(10) ?: "null"
                            Log.d(TAG, "Received tokens - Access: $accessTokenPreview..., ID: $idTokenPreview...")

                            // Store credentials securely in memory
                            accessToken = result.accessToken
                            idToken = result.idToken

                            // Extract user profile information
                            val email = result.user.email ?: ""
                            val name = result.user.name ?: ""
                            val pictureUrl = result.user.pictureURL ?: ""

                            // Extract first name from full name (simple approach)
                            val firstName = name.split(" ").firstOrNull() ?: ""

                            Log.d(TAG, "User profile retrieved: $name, $email")

                            // Update the state on the main thread
                            CoroutineScope(Dispatchers.Main).launch {
                                val profile = UserProfile(email, name, firstName, pictureUrl)
                                _userProfile.value = profile
                                _isAuthenticated.value = true
                                _isLoading.value = false
                                Log.d(TAG, "Authentication state updated: Authenticated=${_isAuthenticated.value}")
                                callback(true)
                            }
                        }

                        override fun onFailure(error: AuthenticationException) {
                            Log.e(TAG, "Login failed: ${error.message}, ${error.getDescription()}")

                            // Provide user-friendly error message
                            val errorMsg = when {
                                error.isNetworkError -> "Network error. Please check your connection."
                                error.isCanceled -> "Authentication was canceled."
                                else -> error.getDescription() ?: "Authentication failed."
                            }

                            // Update state on the main thread
                            CoroutineScope(Dispatchers.Main).launch {
                                _error.value = errorMsg
                                _isLoading.value = false
                                Log.d(TAG, "Authentication state updated: Error=$errorMsg")
                                callback(false)
                            }
                        }
                    })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during login: ${e.message}", e)
            _error.value = "Login error: ${e.message}"
            _isLoading.value = false
            callback(false)
        }
    }

    /**
     * Logout function - handles Auth0 logout
     */
    suspend fun logout(context: Context, callback: () -> Unit) {
        try {
            if (context !is ComponentActivity) {
                Log.e(TAG, "Context must be a ComponentActivity")
                _error.value = "Invalid context for logout"
                callback()
                return
            }

            _isLoading.value = true
            Log.d(TAG, "Starting logout process")

            withContext(Dispatchers.Main) {
                WebAuthProvider.logout(auth0)
                    .withScheme(scheme)
                    .withReturnToUrl("$scheme://$domain/android/com.licenta.licentaclientapp/callback")
                    .start(context, object : Callback<Void?, AuthenticationException> {
                        override fun onSuccess(result: Void?) {
                            Log.d(TAG, "Logout successful")

                            // Clear credentials from memory
                            accessToken = null
                            idToken = null

                            // Update state on the main thread
                            CoroutineScope(Dispatchers.Main).launch {
                                _userProfile.value = null
                                _isAuthenticated.value = false
                                _isLoading.value = false
                                Log.d(TAG, "Authentication state updated after logout: Authenticated=${_isAuthenticated.value}")
                                callback()
                            }
                        }

                        override fun onFailure(error: AuthenticationException) {
                            Log.e(TAG, "Logout failed: ${error.message}")

                            // Even if logout fails, we'll clear local state anyway
                            accessToken = null
                            idToken = null

                            // Update state on the main thread
                            CoroutineScope(Dispatchers.Main).launch {
                                _error.value = "Logout error: ${error.getDescription()}"
                                _userProfile.value = null
                                _isAuthenticated.value = false
                                _isLoading.value = false
                                callback()
                            }
                        }
                    })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during logout: ${e.message}", e)

            // Clear state even on exception
            accessToken = null
            idToken = null
            _userProfile.value = null
            _isAuthenticated.value = false
            _isLoading.value = false
            _error.value = "Logout error: ${e.message}"

            callback()
        }
    }
}

// User profile data class
data class UserProfile(
    val email: String,
    val name: String,
    val firstName: String,
    val pictureUrl: String
)