package com.licenta.licentaclientapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.licenta.licentaclientapp.auth.UserProfile

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel,
    navController: NavHostController = rememberNavController()
) {
    // Create a ViewModel to handle card details if needed
    val cardViewModel = remember { CardViewModel() }
    // Observe authentication state
    val isAuthenticated by authViewModel.isAuthenticated.collectAsState()
    val userProfile by authViewModel.userProfile.collectAsState()
    val shouldNavigateToRegistration by authViewModel.shouldNavigateToRegistration.collectAsState()

    // Handle 404 navigation
    LaunchedEffect(shouldNavigateToRegistration) {
        if (shouldNavigateToRegistration) {
            navController.navigate("registration_screen") {
                // Don't pop the current screen, so user can go back if needed
                launchSingleTop = true
            }
            // Clear the navigation flag after handling
            authViewModel.clearNavigationToRegistration()
        }
    }

    NavHost(navController = navController, startDestination = "splash_screen") {
        // Splash screen - entry point
        composable("splash_screen") {
            SplashScreen(
                authViewModel = authViewModel,
                onAuthenticated = { isAuth ->
                    if (isAuth) {
                        navController.navigate("home_screen") {
                            popUpTo("splash_screen") { inclusive = true }
                        }
                    } else {
                        navController.navigate("login_screen") {
                            popUpTo("splash_screen") { inclusive = true }
                        }
                    }
                }
            )
        }

        // Login screen
        composable("login_screen") {
            LoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = {
                    // Navigate to home screen on successful login
                    navController.navigate("home_screen") {
                        popUpTo("login_screen") { inclusive = true }
                    }
                }
            )
        }

        // Registration screen
        composable("registration_screen") {
            RegistrationScreen(
                authViewModel = authViewModel,
                onRegistrationComplete = {
                    // After successful registration, navigate back to home screen
                    // since the user is already authenticated with Auth0
                    navController.navigate("home_screen") {
                        popUpTo("registration_screen") { inclusive = true }
                    }
                },
                onBackPressed = {
                    // Navigate back to previous screen (could be login or home)
                    navController.popBackStack()
                }
            )
        }

        // Home screen
        composable("home_screen") {
            // Remove the conditional redirect that's causing the loop
            HomeScreen(
                navController = navController,
                authViewModel = authViewModel,
                onLogout = {
                    // Navigate to login screen after logout
                    navController.navigate("login_screen") {
                        popUpTo("home_screen") { inclusive = true }
                    }
                }
            )
        }

        // Debit card screen
        composable("debit_card_screen") {
            DebitCardScreen(
                onBackPressed = { navController.popBackStack() },
                onSaveCard = { cardDetails ->
                    // Save card details to the ViewModel
                    cardViewModel.saveCardDetails(cardDetails)
                    // Navigate back to previous screen
                    navController.popBackStack()
                }
            )
        }

        // Profile screen
        composable("profile_screen") {
            ProfileScreen(
                navController = navController,
                authViewModel = authViewModel
            )
        }

        // Add the pickup address screen with the required functionality
        composable("pickup_address_screen") {
            PickupAddressScreen(
                navController = navController,
                authViewModel = authViewModel,
                cardViewModel = cardViewModel
            )
        }

        // Pickup loading screen - handles the polling of pickup status
        composable("pickup_loading_screen/{pickupId}") { backStackEntry ->
            val pickupId = backStackEntry.arguments?.getString("pickupId")?.toLongOrNull() ?: 0L
            PickupLoadingScreen(
                navController = navController,
                pickupId = pickupId
            )
        }

        // Pickup completed screen - shows success message
        composable("pickup_completed_screen/{pickupId}") { backStackEntry ->
            val pickupId = backStackEntry.arguments?.getString("pickupId")?.toLongOrNull() ?: 0L
            PickupCompletedScreen(
                navController = navController,
                pickupId = pickupId
            )
        }
    }
}