package com.example.cardbattle.ui.nav

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.cardbattle.data.auth.AuthSession
import com.example.cardbattle.data.local.LocalCardStore
import com.example.cardbattle.data.remote.BackendHttpException
import com.example.cardbattle.data.remote.UserProfile
import com.example.cardbattle.data.remote.updateDisplayName
import com.example.cardbattle.ui.cards.CardDraftsViewModel
import com.example.cardbattle.ui.cards.CreateCardScreen
import com.example.cardbattle.ui.cards.MyCardsScreen
import com.example.cardbattle.ui.home.HomeScreen
import com.example.cardbattle.ui.login.LoginScreen
import kotlinx.serialization.Serializable
import com.example.cardbattle.data.local.LocalDeckStore

@Serializable
data object LoginRoute

@Serializable
data object HomeRoute

@Serializable
data object CreateCardRoute

@Serializable
data object MyCardsRoute

@Serializable
data class EditCardRoute(val cardId: String)

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val authSession = remember { AuthSession() }
    var currentUser by remember { mutableStateOf<UserProfile?>(null) }

    val activity = checkNotNull(
        LocalActivity.current as? ComponentActivity
    ) {
        "AppNavHost requires a ComponentActivity."
    }
    val context = activity.applicationContext

    val drafts = remember(activity) {
        ViewModelProvider(activity)[CardDraftsViewModel::class.java]
    }

    fun store(): LocalCardStore {
        val userId = checkNotNull(currentUser?.id) {
            "Sign in to access your cards."
        }

        return LocalCardStore(context, userId)
    }
    fun deckStore(): LocalDeckStore {
        val userId = checkNotNull(currentUser?.id) {
            "Sign in to access your decks."
        }

        return LocalDeckStore(context, userId)
    }

    fun returnHome() {
        navController.navigate(HomeRoute) {
            popUpTo<HomeRoute> { inclusive = false }
            launchSingleTop = true
        }
    }

    fun logOut() {
        authSession.clear()
        currentUser = null
        drafts.clear()

        navController.navigate(LoginRoute) {
            popUpTo<HomeRoute> { inclusive = true }
            launchSingleTop = true
        }
    }

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
                    drafts.selectUser(user.id)

                    navController.navigate(HomeRoute) {
                        popUpTo<LoginRoute> { inclusive = true }
                    }
                },
            )
        }

        composable<HomeRoute> {
            HomeScreen(
                displayName = currentUser?.displayName ?: "Player",

                onSaveDisplayName = { newName ->
                    val token = checkNotNull(authSession.idToken) {
                        "Not signed in"
                    }

                    try {
                        currentUser = updateDisplayName(token, newName)
                    } catch (error: BackendHttpException) {
                        if (error.statusCode == 401) logOut()
                        throw error
                    }
                },

                onNavigateToNewCards = {
                    navController.navigate(CreateCardRoute) {
                        launchSingleTop = true
                    }
                },

                onNavigateToYourCards = {
                    navController.navigate(MyCardsRoute) {
                        launchSingleTop = true
                    }
                },

                onLogout = { logOut() },
            )
        }

        composable<CreateCardRoute> {
            // Capture this draft so saving resets only the completed draft.
            val draft = drafts.newDraft

            CreateCardScreen(
                draft = draft,
                onBack = { navController.popBackStack() },

                onSaveLocal = { id, name, type, pngBytes ->
                    store().save(
                        id = id,
                        name = name,
                        type = type,
                        pngBytes = pngBytes,
                    )

                    returnHome()
                    drafts.finishNewCard()
                },
            )
        }

        composable<MyCardsRoute> {
            MyCardsScreen(
                onBack = { navController.popBackStack() },

                loadCards = { store().load() },

                onEditCard = { card ->
                    drafts.openEdit(card)

                    navController.navigate(EditCardRoute(card.id)) {
                        launchSingleTop = true
                    }
                },

                onDeleteCard = { id ->
                    val usedInDeck = deckStore().load().any {
                        id in it.cardIds
                    }

                    check(!usedInDeck) {
                        "Replace this card in its decks before deleting it."
                    }

                    store().delete(id)
                    drafts.editDrafts.remove(id)
                },

                loadDecks = { deckStore().load() },

                onSaveDeck = { deck ->
                    deckStore().save(deck)
                },

                onDeleteDeck = { slot ->
                    deckStore().delete(slot)
                },
            )
        }

        composable<EditCardRoute> { entry ->
            val route = entry.toRoute<EditCardRoute>()
            val draft = drafts.editDrafts[route.cardId]

            if (draft == null) {
                LaunchedEffect(route.cardId) {
                    navController.popBackStack()
                }
            } else {
                CreateCardScreen(
                    draft = draft,
                    onBack = { navController.popBackStack() },

                    onSaveLocal = { id, name, type, pngBytes ->
                        store().save(
                            id = id,
                            name = name,
                            type = type,
                            pngBytes = pngBytes,
                        )

                        returnHome()
                        drafts.editDrafts.remove(id)
                    },
                )
            }
        }
    }
}