package com.licenta.licentaclientapp.ui


import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.licenta.licentaclientapp.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    authViewModel: AuthViewModel,
    onAuthenticated: (Boolean) -> Unit
) {
    val context = LocalContext.current

    // Initialize the auth view model
    LaunchedEffect(Unit) {
        authViewModel.initialize(context)

        // Simulate loading for at least 1.5 seconds to show splash
        delay(1500)

        // Check authentication status and navigate accordingly
        val isAuthenticated = authViewModel.isAuthenticated.value
        onAuthenticated(isAuthenticated)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Logo (Replace with your own logo resource)
            Image(
                painter = painterResource(id = R.drawable.premium),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(150.dp)
                    .padding(bottom = 24.dp)
            )

            // App Name
            Text(
                text = "Sgr Pickup",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            // Loading indicator
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}