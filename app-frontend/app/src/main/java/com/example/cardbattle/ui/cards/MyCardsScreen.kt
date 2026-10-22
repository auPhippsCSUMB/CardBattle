package com.example.cardbattle.ui.cards

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardbattle.data.local.LocalCard
import com.example.cardbattle.data.remote.CardType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.cardbattle.data.local.LocalDeck

@Composable
fun MyCardsScreen(
    onBack: () -> Unit,
    loadCards: suspend () -> List<LocalCard>,
    onEditCard: (LocalCard) -> Unit,
    onDeleteCard: suspend (String) -> Unit,
    loadDecks: suspend () -> List<LocalDeck>,
    onSaveDeck: suspend (LocalDeck) -> Unit,
    onDeleteDeck: suspend (Int) -> Unit,
) {
    var cards by remember { mutableStateOf<List<LocalCard>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedCard by remember { mutableStateOf<LocalCard?>(null) }
    var reload by remember { mutableStateOf(0) }


    val currentLoader by rememberUpdatedState(loadCards)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var deleteTarget by remember { mutableStateOf<LocalCard?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val deckEditor = remember { DeckEditorState() }

    LaunchedEffect(reload) {
        loading = true
        errorMessage = null

        try {
            cards = currentLoader()
                .asReversed()
                .sortedByDescending { it.createdAt }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorMessage = "Could not load your cards. Please try again."
        } finally {
            loading = false
        }
    }

    fun showDeckMessage(starter: Boolean) {
        scope.launch {
            snackbar.showSnackbar(
                if (starter) {
                    "Your starter deck is coming soon."
                } else {
                    "Deck creation is coming soon."
                }
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    enabled = !deckEditor.busy,
                    onClick = {
                        if (deckEditor.isOpen) {
                            deckEditor.close()
                        } else {
                            onBack()
                        }
                    },
                ) {
                    Text("Back")
                }

                Text(
                    text = "My Cards",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DeckSection(
                    cards = cards,
                    editor = deckEditor,
                    loadDecks = loadDecks,
                    onSaveDeck = onSaveDeck,
                    onDeleteDeck = onDeleteDeck,
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (loading || errorMessage != null) {
                        "Your cards"
                    } else {
                        "Your cards (${cards.size})"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = "Newest first",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                loading -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            textAlign = TextAlign.Center,
                        )

                        TextButton(onClick = { reload++ }) {
                            Text("Try again")
                        }
                    }
                }

                cards.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No cards yet.\n" +
                                    "Use New Cards on Home to create your first one!",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 20.dp),
                    ) {
                        items(
                            items = cards,
                            key = { it.id },
                        ) { card ->
                            Box(
                                modifier = Modifier.deckCardGestures(
                                    cardId = card.id,
                                    editor = deckEditor,
                                ),
                            ) {
                                CollectionCard(
                                    card = card,
                                    onClick = {
                                        if (deckEditor.isOpen) {
                                            deckEditor.addCard(card.id)
                                        } else {
                                            selectedCard = card
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedCard?.let { card ->
        val color = collectionCardColor(card.type)

        AlertDialog(
            onDismissRequest = { selectedCard = null },
            containerColor = color,
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = {
                Text(
                    text = card.name,
                    modifier = Modifier.fillMaxWidth(),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    LocalCardArt(
                        card = card,
                        fullSize = true,
                    )

                    Text(
                        text = card.type.label,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEditCard(card)
                        selectedCard = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.White
                    ),
                ) {
                    Text("Edit")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            selectedCard = null
                            deleteTarget = card
                            deleteError = null
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White
                        ),
                    ) {
                        Text("Delete")
                    }
                }
            },
        )
    }
    deleteTarget?.let { card ->
        AlertDialog(
            onDismissRequest = {
                if (!deleting) deleteTarget = null
            },
            title = { Text("Delete card?") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "\"${card.name}\" will be deleted from this device. " +
                                "This cannot be undone."
                    )

                    deleteError?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        if (!deleting) {
                            deleting = true
                            deleteError = null

                            scope.launch {
                                try {
                                    onDeleteCard(card.id)
                                    cards = cards.filterNot {
                                        it.id == card.id
                                    }
                                    deleteTarget = null
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    deleteError = error.message
                                        ?: "Could not delete the card. Try again."
                                } finally {
                                    deleting = false
                                }
                            }
                        }
                    },
                ) {
                    Text(if (deleting) "Deleting…" else "Delete")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleteTarget = null
                        selectedCard = card
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun DeckSlot(
    title: String,
    subtitle: String,
    starter: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 120.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (starter) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (starter) "3 cards" else "+",
                style = if (starter) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.headlineSmall
                },
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun CollectionCard(
    card: LocalCard,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = collectionCardColor(card.type),
            contentColor = Color.White,
        ),
    ) {
        Column(
            modifier = Modifier.padding(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = card.name,
                modifier = Modifier.fillMaxWidth(),
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            LocalCardArt(card = card)

            Text(
                text = card.type.label,
                modifier = Modifier.fillMaxWidth(),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LocalCardArt(
    card: LocalCard,
    fullSize: Boolean = false,
) {
    val image by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = card.pngBase64,
        key2 = fullSize,
    ) {
        value = withContext(Dispatchers.Default) {
            try {
                val bytes = Base64.decode(
                    card.pngBase64,
                    Base64.DEFAULT,
                )

                val options = BitmapFactory.Options().apply {
                    inSampleSize = if (fullSize) 1 else 4
                }

                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    options,
                )?.asImageBitmap()
            } catch (error: IllegalArgumentException) {
                null
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image

        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "${card.name} artwork",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = "Art",
                color = Color.Gray,
                fontSize = 10.sp,
            )
        }
    }
}

private fun collectionCardColor(type: CardType): Color =
    when (type) {
        CardType.WARRIOR -> Color(0xFF963D45)
        CardType.BRAWLER -> Color(0xFF925027)
        CardType.RANGER -> Color(0xFF326448)
        CardType.MAGE -> Color(0xFF69469B)
        CardType.ROGUE -> Color(0xFF364C68)
    }