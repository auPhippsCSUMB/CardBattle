package com.example.cardbattle.ui.nav

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.cardbattle.data.auth.AuthSession
import com.example.cardbattle.data.remote.*
import com.example.cardbattle.ui.cards.*
import com.example.cardbattle.ui.home.HomeScreen
import com.example.cardbattle.ui.login.LoginScreen
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
data object LoginRoute

@Serializable
data object HomeRoute

@Serializable
data object CreateCardRoute

@Serializable
data object MyCardsRoute

@Serializable
data class EditCardRoute(val cardId: Long)

class AppSessionViewModel : ViewModel() {
    val auth = AuthSession()

    var user by mutableStateOf<UserProfile?>(null)

    fun clear() {
        auth.clear()
        user = null
    }
}

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    val activity = checkNotNull(
        LocalActivity.current as? ComponentActivity
    ) {
        "AppNavHost requires a ComponentActivity."
    }

    val session = remember(activity) {
        ViewModelProvider(activity)[AppSessionViewModel::class.java]
    }

    val drafts = remember(activity) {
        ViewModelProvider(activity)[CardDraftsViewModel::class.java]
    }

    val api = remember(session) {
        CardBattleClient(
            tokenProvider = {
                checkNotNull(session.auth.idToken) {
                    "Sign in first."
                }
            }
        )
    }

    val repository = remember(api) {
        CardRepository(api)
    }

    fun returnHome() {
        navController.navigate(HomeRoute) {
            popUpTo<HomeRoute> { inclusive = false }
            launchSingleTop = true
        }
    }

    fun logOut() {
        session.clear()
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
                    drafts.selectUser(user.id)
                    session.auth.save(idToken)
                    session.user = user

                    navController.navigate(HomeRoute) {
                        popUpTo<LoginRoute> { inclusive = true }
                    }
                },
            )
        }

        composable<HomeRoute> {
            val user = session.user

            var cardsCreated by remember(user?.id) {
                mutableStateOf<Int?>(null)
            }
            var stats by remember(user?.id) {
                mutableStateOf<ApiStats?>(null)
            }

            LaunchedEffect(user?.id) {
                val userId = user?.id ?: return@LaunchedEffect

                try {
                    cardsCreated = api.listMyCards(
                        size = 1,
                    ).page.totalElements.toInt()
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    cardsCreated = null
                }

                try {
                    stats = api.userStats(userId.toLong())
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    stats = null
                }
            }

            HomeScreen(
                displayName = user?.displayName ?: "Player",
                cardsCreated = cardsCreated,
                wins = stats?.wins,
                losses = stats?.losses,

                onSaveDisplayName = { name ->
                    try {
                        session.user = api.changeDisplayName(name)
                    } catch (failure: BackendHttpException) {
                        if (failure.statusCode == 401) logOut()
                        throw failure
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
            val draft = drafts.newDraft

            CreateCardScreen(
                draft = draft,
                onBack = { navController.popBackStack() },

                onSave = { name, type, pngBytes ->
                    val userId = checkNotNull(session.user?.id) {
                        "Sign in before saving a card."
                    }

                    repository.saveNewDrawing(
                        saveState = draft.saveState,
                        userId = userId.toLong(),
                        name = name,
                        type = type,
                        pngBytes = pngBytes,
                    )

                    drafts.finishNewCard()
                    returnHome()
                },
            )
        }

        composable<MyCardsRoute> {
            val userId = session.user?.id

            if (userId == null) {
                LaunchedEffect(Unit) {
                    logOut()
                }
            } else {
                MyCardsScreen(
                    api = api,
                    repository = repository,
                    userId = userId.toLong(),
                    onBack = { navController.popBackStack() },

                    onEditCard = { selected ->
                        val existingDraft = drafts.editDrafts[selected.id]

                        if (existingDraft == null) {
                            val current = api.getMyCard(selected.id)

                            val drawing = if (
                                current.imgURL.isNullOrBlank()
                            ) {
                                null
                            } else {
                                repository.loadDrawing(current.id)
                            }

                            drafts.openEdit(current, drawing)
                        }

                        navController.navigate(
                            EditCardRoute(selected.id)
                        ) {
                            launchSingleTop = true
                        }
                    },

                    onCardDeleted = { cardId ->
                        drafts.editDrafts.remove(cardId)
                    },
                )
            }
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

                    onSave = { name, type, pngBytes ->
                        repository.saveEditedDrawing(
                            cardId = route.cardId,
                            name = name,
                            type = type,
                            pngBytes = pngBytes,
                        )

                        drafts.editDrafts.remove(route.cardId)
                        returnHome()
                    },
                )
            }
        }
    }
}