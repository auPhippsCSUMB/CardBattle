package com.example.cardbattle.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.cardbattle.ui.theme.CardBattleTheme
import kotlin.math.roundToInt
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    CardBattleTheme {
        HomeScreen()
    }
}

internal fun calculateWinRate(wins: Int, losses: Int): String {
    val totalGames = wins + losses
    if (totalGames == 0) return "0%"
    val winRate = (wins.toDouble() / totalGames.toDouble()) * 100
    return "${winRate.roundToInt()}%"
}

@Composable
fun HomeScreen(
    displayName: String = "Player",
    onSaveDisplayName: suspend (String) -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf(displayName) }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
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
                                    text = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            Column {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "Level 12",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (!editing) {
                            TextButton(onClick = {
                                draftName = displayName
                                errorMessage = null
                                editing = true
                            }) {
                                Text("Edit name")
                            }
                        } else {
                            OutlinedTextField(
                                value = draftName,
                                onValueChange = { draftName = it },
                                label = { Text("Display name") },
                                singleLine = true,
                                enabled = !saving,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        editing = false
                                        errorMessage = null
                                    },
                                    enabled = !saving,
                                ) {
                                    Text("Cancel")
                                }

                                Button(
                                    enabled = !saving && draftName.isNotBlank(),
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
                                                errorMessage = "Could not save name. Please try again."
                                            } finally {
                                                saving = false
                                            }
                                        }
                                    },
                                ) {
                                    Text(if (saving) "Saving…" else "Save")
                                }
                            }

                            errorMessage?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = "Welcome back!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        label = "Wins",
                        value = "18",
                        accent = MaterialTheme.colorScheme.primary,
                    )
                    StatCard(
                        label = "Losses",
                        value = "7",
                        accent = MaterialTheme.colorScheme.error,
                    )
                    StatCard(
                        label = "Win Rate",
                        value = calculateWinRate(18, 7),
                        accent = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ActionButton(label = "Battle")
                ActionButton(label = "New Cards")
                ActionButton(label = "My Cards")
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    accent: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(0.3f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionButton(label: String) {
    Button(
        onClick = { },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(0.28f),
    ) {
        Text(text = label)
    }
}