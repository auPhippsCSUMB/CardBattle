package com.example.cardbattle.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.cardbattle.ui.home.HomeScreen
import com.example.cardbattle.ui.login.LoginScreen
import kotlinx.serialization.Serializable

@Serializable
data object LoginRoute

@Serializable
data object HomeRoute

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = LoginRoute,
        modifier = modifier,
    ) {
        composable<LoginRoute> {
            LoginScreen(
                onTokenReceived = {
                    navController.navigate(HomeRoute) {
                        popUpTo<LoginRoute> { inclusive = true }
                    }
                }
            )
        }

        composable<HomeRoute> {
            HomeScreen(
                onLogout = {
                    navController.navigate(LoginRoute) {
                        popUpTo<HomeRoute> { inclusive = true }
                    }
                }
            )
        }
    }
}
