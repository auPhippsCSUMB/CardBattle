package com.example.cardbattle.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.cardbattle.ui.theme.CardBattleTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal fun calculateWinRate(wins: Int, losses: Int): String {
    val total = wins.toLong() + losses.toLong()
    if (total == 0L) return "0%"
    return "${(wins.toDouble() / total * 100).roundToInt()}%"
}

@Composable
fun HomeScreen(
    displayName: String = "Player",
    onSaveDisplayName: suspend (String) -> Unit = {},
    onLogout: () -> Unit = {},
    cardsCreated: Int? = null,
    wins: Int? = null,
    losses: Int? = null,
    onNavigateToBattle: (() -> Unit)? = null,
    onNavigateToNewCards: (() -> Unit)? = null,
    onNavigateToYourCards: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var editing by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var profileMenuExpanded by remember { mutableStateOf(false) }

    fun openScreen(action: (() -> Unit)?, name: String) {
        if (action != null) {
            action()
        } else {
            scope.launch {
                snackbar.showSnackbar("$name screen is coming soon.")
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Horizontal +
                                        WindowInsetsSides.Bottom
                            )
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HomeActionButton(
                        label = "Battle",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            openScreen(onNavigateToBattle, "Battle")
                        },
                    )
                    HomeActionButton(
                        label = "New Cards",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            openScreen(onNavigateToNewCards, "New Cards")
                        },
                    )
                    HomeActionButton(
                        label = "My Cards",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (cardsCreated) {
                                null -> {
                                    scope.launch {
                                        snackbar.showSnackbar(
                                            "Couldn't check your cards yet. Please try again."
                                        )
                                    }
                                }

                                0 -> {
                                    scope.launch {
                                        snackbar.showSnackbar("Create a card first!")
                                    }
                                }

                                else -> {
                                    openScreen(onNavigateToYourCards, "My Cards")
                                }
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Box {
                    Card(
                        onClick = { profileMenuExpanded = true },
                        modifier = Modifier.widthIn(max = 280.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = displayName.trim()
                                        .firstOrNull()
                                        ?.uppercaseChar()
                                        ?.toString() ?: "?",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            Column(modifier = Modifier.widthIn(max = 160.dp)) {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "Your profile",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Text(
                                text = "▾",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = profileMenuExpanded,
                        onDismissRequest = { profileMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit name") },
                            onClick = {
                                profileMenuExpanded = false
                                draftName = displayName
                                errorMessage = null
                                editing = true
                            },
                        )

                        DropdownMenuItem(
                            text = { Text("Log out") },
                            onClick = {
                                profileMenuExpanded = false
                                onLogout()
                            },
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(
                        text = "Welcome",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )

                    when {
                        cardsCreated == null -> {
                            Text(
                                text = "Create cards, build your collection, " +
                                        "and get ready to battle.",
                                textAlign = TextAlign.Center,
                                color =
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Create some cards to get started!",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                            )
                        }

                        cardsCreated == 0 -> {
                            Text(
                                text = "Your collection starts here",
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "You haven't created any cards yet. " +
                                        "Tap New Cards below to make your first one.",
                                textAlign = TextAlign.Center,
                                color =
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        else -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor =
                                        MaterialTheme.colorScheme.primaryContainer
                                ),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = cardsCreated.toString(),
                                        style = MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme
                                            .onPrimaryContainer,
                                    )
                                    Text(
                                        text = if (cardsCreated == 1) {
                                            "Card created"
                                        } else {
                                            "Cards created"
                                        },
                                        color = MaterialTheme.colorScheme
                                            .onPrimaryContainer,
                                    )
                                }
                            }

                            val winRate = if (wins != null && losses != null) {
                                calculateWinRate(wins, losses)
                            } else {
                                "—"
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                HomeStatCard(
                                    label = "Wins",
                                    value = wins?.toString() ?: "—",
                                    modifier = Modifier.weight(1f),
                                )
                                HomeStatCard(
                                    label = "Losses",
                                    value = losses?.toString() ?: "—",
                                    modifier = Modifier.weight(1f),
                                )
                                HomeStatCard(
                                    label = "Win rate",
                                    value = winRate,
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            Text(
                                text = "Ready for your next battle?",
                                textAlign = TextAlign.Center,
                                color =
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = {
                if (!saving) editing = false
            },
            title = { Text("Edit display name") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        label = { Text("Display name") },
                        singleLine = true,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    errorMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !saving &&
                            draftName.trim().length in 1..150,
                    onClick = {
                        saving = true
                        errorMessage = null
                        scope.launch {
                            try {
                                onSaveDisplayName(draftName.trim())
                                editing = false
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                errorMessage =
                                    "Could not save name. Please try again."
                            } finally {
                                saving = false
                            }
                        }
                    },
                ) {
                    Text(if (saving) "Saving…" else "Save")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !saving,
                    onClick = { editing = false },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun HomeActionButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun HomeStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyHomePreview() {
    CardBattleTheme {
        HomeScreen(
            displayName = "Gideon",
            cardsCreated = 0,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PopulatedHomePreview() {
    CardBattleTheme {
        HomeScreen(
            displayName = "Gideon",
            cardsCreated = 8,
            wins = 18,
            losses = 7,
        )
    }
}