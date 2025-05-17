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
                onComplete()
            }
        }
    }

    // Clear any error
    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "AuthViewModel cleared")
    }
}