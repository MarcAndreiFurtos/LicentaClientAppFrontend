package com.licenta.licentaclientapp.ui

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.licenta.licentaclientapp.R
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: () -> Unit
) {
    val TAG = "LoginScreen"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isLoading by authViewModel.isLoading.collectAsState()
    val error by authViewModel.error.collectAsState()
    val isAuthenticated by authViewModel.isAuthenticated.collectAsState()

    // Track if login was attempted to show appropriate feedback
    var loginAttempted by remember { mutableStateOf(false) }

    // Initialize the auth view model
    LaunchedEffect(Unit) {
        Log.d(TAG, "LoginScreen initialization")
        authViewModel.initialize(context)
    }

    // Show snackbar for errors
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        if (error != null && loginAttempted) {
            Log.d(TAG, "Showing error: $error")
            snackbarHostState.showSnackbar(
                message = error ?: "Unknown error",
                duration = SnackbarDuration.Long
            )
            // Clear the error after showing it
            authViewModel.clearError()
        }
    }

    // Check if login was successful
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated && loginAttempted) {
            Log.d(TAG, "Login successful, calling onLoginSuccess")
            onLoginSuccess()
            loginAttempted = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
            ) {
                // App Logo
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .size(150.dp)
                        .padding(bottom = 16.dp)
                )

                // App Name
                Text(
                    text = "Sgr Pickup",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Welcome Text
                Text(
                    text = "Welcome to our pickup service app",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Login button
                Button(
                    onClick = {
                        Log.d(TAG, "Login button clicked")
                        loginAttempted = true
                        scope.launch {
                            authViewModel.login(context) { success ->
                                Log.d(TAG, "Login callback result: $success")
                                // Navigation is handled by LaunchedEffect
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text("Login", fontSize = 16.sp)
                    }
                }

                // Version info at the bottom
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = "Version 1.0.0",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
}