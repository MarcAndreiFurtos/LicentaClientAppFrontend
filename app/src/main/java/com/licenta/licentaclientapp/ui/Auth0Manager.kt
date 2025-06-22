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

    private val clientId = "HDOLkDs1fTI88EG6TODypOHIBlAzpddA"
    private val domain = "dev-kotvcrcjprj3uksp.us.auth0.com"

    private val scheme = "demo"

    private val auth0 = Auth0(clientId, domain)

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var accessToken: String? = null

    private var idToken: String? = null

    init {
        Log.d(TAG, "Auth0Manager initialized")
    }

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
                val audience = "https://$domain/api/v2/"
                Log.d(TAG, "Using audience: $audience")

                WebAuthProvider.login(auth0)
                    .withScheme(scheme)
                    .withScope("openid profile email")
                    .withAudience(audience)
                    .withParameters(mapOf("prompt" to "login"))
                    .start(context, object : Callback<Credentials, AuthenticationException> {
                        override fun onSuccess(result: Credentials) {
                            Log.d(TAG, "Login successful")

                            val accessTokenPreview = result.accessToken?.take(10) ?: "null"
                            val idTokenPreview = result.idToken?.take(10) ?: "null"
                            Log.d(TAG, "Received tokens - Access: $accessTokenPreview..., ID: $idTokenPreview...")

                            accessToken = result.accessToken
                            idToken = result.idToken

                            val email = result.user.email ?: ""
                            val name = result.user.name ?: ""
                            val pictureUrl = result.user.pictureURL ?: ""

                            val firstName = name.split(" ").firstOrNull() ?: ""

                            Log.d(TAG, "User profile retrieved: $name, $email")

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

                            val errorMsg = when {
                                error.isNetworkError -> "Network error. Please check your connection."
                                error.isCanceled -> "Authentication was canceled."
                                else -> error.getDescription() ?: "Authentication failed."
                            }

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

                            accessToken = null
                            idToken = null

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

                            accessToken = null
                            idToken = null

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

data class UserProfile(
    val email: String,
    val name: String,
    val firstName: String,
    val pictureUrl: String
)