package com.example.cardbattle.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.cardbattle.data.auth.AuthSession
import com.example.cardbattle.data.remote.BackendHttpException
import com.example.cardbattle.data.remote.UserProfile
import com.example.cardbattle.ui.home.HomeScreen
import com.example.cardbattle.ui.login.LoginScreen
import com.example.cardbattle.ui.login.SignUpScreen
import kotlinx.serialization.Serializable
import com.example.cardbattle.data.remote.updateDisplayName

@Serializable
data object LoginRoute

@Serializable
data object SignUpRoute

@Serializable
data object HomeRoute

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val authSession = remember { AuthSession() }
    var currentUser by remember { mutableStateOf<UserProfile?>(null) }

    NavHost(
        navController = navController,
        startDestination = LoginRoute,
        modifier = modifier,
    ) {
        composable<LoginRoute> {
            LoginScreen(
                onSignedIn = { idToken, user ->
                    authSession.save(idToken)
                    currentUser = user
                    navController.navigate(HomeRoute) {
                        popUpTo<LoginRoute> { inclusive = true }
                    }
                },
                onNavigateToSignUp = {
                    navController.navigate(SignUpRoute)
                }
            )
        }

        composable<HomeRoute> {
            HomeScreen(
                displayName = currentUser?.displayName ?: "Player",
                onSaveDisplayName = { newName ->
                    val token = checkNotNull(authSession.idToken) { "Not signed in" }
                    try {
                        currentUser = updateDisplayName(token, newName)
                    } catch (error: BackendHttpException) {
                        if (error.statusCode == 401) {
                            authSession.clear()
                            currentUser = null
                            navController.navigate(LoginRoute) {
                                popUpTo<HomeRoute> { inclusive = true }
                            }
                        } else {
                            throw error
                        }
                    }
                },
                onLogout = {
                    authSession.clear()
                    currentUser = null
                    navController.navigate(LoginRoute) {
                        popUpTo<HomeRoute> { inclusive = true }
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