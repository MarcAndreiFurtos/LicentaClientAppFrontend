package com.licenta.licentaclientapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.auth0.android.Auth0
import com.auth0.android.provider.WebAuthProvider
import com.licenta.licentaclientapp.ui.AppNavigation
import com.licenta.licentaclientapp.ui.AuthViewModel
import com.licenta.licentaclientapp.ui.theme.LicentaClientAppTheme

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")

        if (intent != null) {
            handleIntent(intent)
        }

        setContent {
            LicentaClientAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(authViewModel = authViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent called")

        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        Log.d(TAG, "handleIntent called with action: ${intent.action}, data: ${intent.data}")

        if (intent.data != null) {
            Log.d(TAG, "Processing potential Auth0 redirect URL: ${intent.data}")

            try {
                WebAuthProvider.resume(intent)
                Log.d(TAG, "WebAuthProvider.resume called successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing Auth0 callback: ${e.message}", e)
            }
        }
    }
}