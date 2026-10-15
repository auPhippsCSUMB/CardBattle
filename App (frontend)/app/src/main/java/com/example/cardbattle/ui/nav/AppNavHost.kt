package com.example.cardbattle.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.cardbattle.ui.home.HomeScreen
import com.example.cardbattle.ui.login.LoginScreen
import com.example.cardbattle.ui.login.SignUpScreen
import kotlinx.serialization.Serializable

@Serializable
data object LoginRoute

@Serializable
data object SignUpRoute

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
                onLoginSuccess = {
                    navController.navigate(HomeRoute) {
                        popUpTo<LoginRoute> { inclusive = true }
                    }
                },
                onNavigateToSignUp = {
                    navController.navigate(SignUpRoute)
                }
            )
        }

        composable<SignUpRoute> {
            SignUpScreen(
                onSignUpSuccess = {
                    navController.navigate(HomeRoute) {
                        popUpTo<LoginRoute> { inclusive = true }
                    }
                },
                onBackToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable<HomeRoute> {
            HomeScreen(
                onNavigateToBattle = { /* TODO: navController.navigate(BattleRoute) */ },
                onNavigateToNewCards = { /* TODO: navController.navigate(NewCardsRoute) */ },
                onNavigateToYourCards = { /* TODO: navController.navigate(YourCardsRoute) */ },
                onNavigateToSettings = { /* TODO: navController.navigate(SettingsRoute) */ }
            )
        }
    }
}