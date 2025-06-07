package com.licenta.licentaclientapp.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

data class RegistrationUiState(
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val emailError: String? = null,
    val firstNameError: String? = null,
    val lastNameError: String? = null,
    val generalError: String? = null,
    val isLoading: Boolean = false,
    val isRegistrationSuccessful: Boolean = false,
    val isFormValid: Boolean = false,
    val isEmailReadOnly: Boolean = false // New field to handle Auth0 pre-filled email
)

class RegistrationViewModel : ViewModel() {
    private val TAG = "RegistrationViewModel"

    private val _uiState = MutableStateFlow(RegistrationUiState())
    val uiState: StateFlow<RegistrationUiState> = _uiState.asStateFlow()

    // Initialize with Auth0 user profile if available
    fun initializeWithAuth0Profile(authViewModel: AuthViewModel) {
        viewModelScope.launch {
            authViewModel.userProfile.collect { userProfile ->
                if (userProfile != null && _uiState.value.email.isEmpty()) {
                    Log.d(TAG, "Initializing registration with Auth0 profile: ${userProfile.email}")

                    // Extract first and last name from Auth0 profile if available
                    val names = extractNamesFromAuth0Profile(userProfile)

                    _uiState.value = _uiState.value.copy(
                        email = userProfile.email ?: "",
                        firstName = names.first,
                        lastName = names.second,
                        isEmailReadOnly = true, // Email is read-only when coming from Auth0
                        emailError = null
                    )
                    updateFormValidity()
                }
            }
        }
    }

    // Extract first and last names from Auth0 user profile
    private fun extractNamesFromAuth0Profile(userProfile: com.licenta.licentaclientapp.auth.UserProfile): Pair<String, String> {
        // Try to get names from Auth0 profile
        val fullName = userProfile.name ?: ""

        // Try to extract from nickname if name is not available
        val displayName = if (fullName.isNotBlank()) fullName else (userProfile.firstName ?: "")

        return when {
            // If we have a display name, try to split it
            displayName.isNotEmpty() -> {
                val nameParts = displayName.trim().split("\\s+".toRegex())
                when (nameParts.size) {
                    1 -> Pair(nameParts[0], "")
                    2 -> Pair(nameParts[0], nameParts[1])
                    else -> Pair(nameParts[0], nameParts.drop(1).joinToString(" "))
                }
            }
            // Default to empty if no name information is available
            else -> Pair("", "")
        }
    }

    // Update email field (only if not read-only)
    fun updateEmail(email: String) {
        if (!_uiState.value.isEmailReadOnly) {
            _uiState.value = _uiState.value.copy(
                email = email,
                emailError = validateEmail(email),
                generalError = null
            )
            updateFormValidity()
        }
    }

    // Update first name field
    fun updateFirstName(firstName: String) {
        _uiState.value = _uiState.value.copy(
            firstName = firstName.trim(),
            firstNameError = validateFirstName(firstName.trim()),
            generalError = null
        )
        updateFormValidity()
    }

    // Update last name field
    fun updateLastName(lastName: String) {
        _uiState.value = _uiState.value.copy(
            lastName = lastName.trim(),
            lastNameError = validateLastName(lastName.trim()),
            generalError = null
        )
        updateFormValidity()
    }

    // Validate email format
    private fun validateEmail(email: String): String? {
        return when {
            email.isBlank() -> "Email is required"
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Please enter a valid email address"
            else -> null
        }
    }

    // Validate first name
    private fun validateFirstName(firstName: String): String? {
        return when {
            firstName.isBlank() -> "First name is required"
            firstName.length < 2 -> "First name must be at least 2 characters"
            firstName.length > 50 -> "First name must be less than 50 characters"
            !firstName.matches(Regex("^[a-zA-Z\\s'-]+$")) -> "First name can only contain letters, spaces, hyphens, and apostrophes"
            else -> null
        }
    }

    // Validate last name
    private fun validateLastName(lastName: String): String? {
        return when {
            lastName.isBlank() -> "Last name is required"
            lastName.length < 2 -> "Last name must be at least 2 characters"
            lastName.length > 50 -> "Last name must be less than 50 characters"
            !lastName.matches(Regex("^[a-zA-Z\\s'-]+$")) -> "Last name can only contain letters, spaces, hyphens, and apostrophes"
            else -> null
        }
    }

    // Update form validity
    private fun updateFormValidity() {
        val currentState = _uiState.value
        val isValid = currentState.email.isNotBlank() &&
                currentState.firstName.isNotBlank() &&
                currentState.lastName.isNotBlank() &&
                currentState.emailError == null &&
                currentState.firstNameError == null &&
                currentState.lastNameError == null

        _uiState.value = currentState.copy(isFormValid = isValid)
    }

    // Register user
    fun registerUser() {
        val currentState = _uiState.value

        if (!currentState.isFormValid || currentState.isLoading) {
            return
        }

        Log.d(TAG, "Starting user registration for email: ${currentState.email}")

        _uiState.value = currentState.copy(
            isLoading = true,
            generalError = null
        )

        viewModelScope.launch {
            try {
                val success = createUserAccount(
                    email = currentState.email,
                    firstName = currentState.firstName,
                    lastName = currentState.lastName
                )

                if (success) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRegistrationSuccessful = true,
                        generalError = null
                    )
                    Log.d(TAG, "User registration successful")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        generalError = "Registration failed. Please try again."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Registration error", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    generalError = getErrorMessage(e)
                )
            }
        }
    }

    // Configure SSL for development (same as in AuthViewModel)
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

            Log.d(TAG, "SSL configured for development")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring SSL", e)
        }
    }

    // Create user account via API
    private suspend fun createUserAccount(
        email: String,
        firstName: String,
        lastName: String
    ): Boolean = withContext(Dispatchers.IO) {
        val url = URL("https://10.0.2.2:8443/api/users")

        Log.d(TAG, "Making POST request to create user: $url")

        val connection = url.openConnection() as HttpURLConnection

        // Configure SSL if HTTPS
        if (connection is HttpsURLConnection) {
            configureSSLForDevelopment(connection)
        }

        // Create JSON payload
        val jsonPayload = JSONObject().apply {
            put("email", email)
            put("firstName", firstName)
            put("lastName", lastName)
        }

        return@withContext try {
            connection.apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
            }

            // Write JSON payload
            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(jsonPayload.toString())
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            Log.d(TAG, "Registration API Response code: $responseCode")

            when (responseCode) {
                HttpURLConnection.HTTP_CREATED, HttpURLConnection.HTTP_OK -> {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.use { it.readText() }
                    Log.d(TAG, "Registration successful: $response")
                    true
                }
                HttpURLConnection.HTTP_CONFLICT -> {
                    Log.w(TAG, "User already exists")
                    throw UserAlreadyExistsException("An account with this email already exists")
                }
                else -> {
                    val errorReader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
                    val errorResponse = errorReader.use { it.readText() }
                    Log.e(TAG, "Registration failed: HTTP $responseCode - $errorResponse")
                    false
                }
            }
        } catch (e: UserAlreadyExistsException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Network error during registration", e)
            throw e
        } finally {
            connection.disconnect()
        }
    }

    // Get user-friendly error message
    private fun getErrorMessage(exception: Exception): String {
        return when (exception) {
            is UserAlreadyExistsException -> exception.message ?: "Account already exists"
            is java.net.SocketTimeoutException -> "Connection timeout. Please check your internet connection and try again."
            is java.net.ConnectException -> "Unable to connect to server. Please try again later."
            is java.net.UnknownHostException -> "Network error. Please check your internet connection."
            else -> "Registration failed: ${exception.message ?: "Unknown error"}"
        }
    }

    // Clear error messages
    fun clearErrors() {
        _uiState.value = _uiState.value.copy(
            emailError = null,
            firstNameError = null,
            lastNameError = null,
            generalError = null
        )
    }

    // Reset form
    fun resetForm() {
        _uiState.value = RegistrationUiState()
    }
}

// Custom exception for user already exists
class UserAlreadyExistsException(message: String) : Exception(message)