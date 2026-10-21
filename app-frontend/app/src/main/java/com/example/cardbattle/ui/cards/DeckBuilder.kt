@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.example.cardbattle.ui.cards

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cardbattle.data.local.LocalCard
import com.example.cardbattle.data.local.LocalDeck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val CARD_MIME =
    "application/vnd.cardbattle.card-id"

class DeckEditorState {
    var deckSlot by mutableStateOf<Int?>(null)
    var name by mutableStateOf("")
    var activeSlot by mutableIntStateOf(0)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    val cardIds = mutableStateListOf<String?>(null, null, null)

    val isOpen: Boolean
        get() = deckSlot != null

    fun open(slot: Int, deck: LocalDeck?) {
        deckSlot = slot
        name = deck?.name ?: "Deck ${slot + 1}"

        repeat(3) {
            cardIds[it] = deck?.cardIds?.getOrNull(it)
        }

        activeSlot = cardIds.indexOfFirst { it == null }
            .takeIf { it >= 0 } ?: 0

        error = null
    }

    fun close() {
        if (!busy) {
            deckSlot = null
            error = null
        }
    }

    fun addCard(id: String, target: Int = activeSlot): Boolean {
        if (!isOpen || busy || target !in 0..2) return false

        if (cardIds.withIndex().any {
                it.value == id && it.index != target
            }) {
            error = "That card is already in this deck."
            return false
        }

        cardIds[target] = id
        activeSlot = cardIds.indexOfFirst { it == null }
            .takeIf { it >= 0 } ?: target
        error = null
        return true
    }

    fun removeCard(index: Int) {
        if (!busy) {
            cardIds[index] = null
            activeSlot = index
            error = null
        }
    }
}

@Composable
fun DeckSection(
    cards: List<LocalCard>,
    editor: DeckEditorState,
    loadDecks: suspend () -> List<LocalDeck>,
    onSaveDeck: suspend (LocalDeck) -> Unit,
    onDeleteDeck: suspend (Int) -> Unit,
) {
    var decks by remember {
        mutableStateOf<List<LocalDeck>>(emptyList())
    }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val currentLoader by rememberUpdatedState(loadDecks)

    LaunchedEffect(reload) {
        loading = true
        loadError = false

        try {
            decks = currentLoader()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            loadError = true
        } finally {
            loading = false
        }
    }

    BackHandler(enabled = editor.isOpen) {
        editor.close()
    }

    when {
        loading -> {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        loadError -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Could not load decks.")
                TextButton(onClick = { reload++ }) {
                    Text("Retry")
                }
            }
        }

        !editor.isOpen -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SavedDeckTile(
                    title = "Starter Deck",
                    subtitle = "Coming soon",
                    symbol = "3 cards",
                    modifier = Modifier.weight(1f),
                    onClick = {},
                )

                repeat(2) { slot ->
                    val deck = decks.firstOrNull {
                        it.slot == slot
                    }

                    SavedDeckTile(
                        title = deck?.name ?: "New deck",
                        subtitle = if (deck == null) {
                            "Empty"
                        } else {
                            "Tap to edit"
                        },
                        symbol = if (deck == null) "+" else "3 cards",
                        modifier = Modifier.weight(1f),
                        onClick = { editor.open(slot, deck) },
                    )
                }
            }
        }

        else -> {
            val existing = decks.firstOrNull {
                it.slot == editor.deckSlot
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = {
                        if (it.length <= 40) editor.name = it
                    },
                    label = { Text("Deck name") },
                    singleLine = true,
                    enabled = !editor.busy,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    repeat(3) { index ->
                        DeckCardSlot(
                            index = index,
                            card = cards.firstOrNull {
                                it.id == editor.cardIds[index]
                            },
                            cards = cards,
                            editor = editor,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Text(
                    text = "Select a slot, then tap or right-click a card. " +
                            "You can also long-press and drag a card into a slot.",
                    style = MaterialTheme.typography.bodySmall,
                )

                editor.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { editor.close() },
                        enabled = !editor.busy,
                    ) {
                        Text("Cancel")
                    }

                    if (existing != null) {
                        TextButton(
                            onClick = { confirmDelete = true },
                            enabled = !editor.busy,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor =
                                    MaterialTheme.colorScheme.error
                            ),
                        ) {
                            Text("Delete")
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Button(
                        enabled = !editor.busy &&
                                editor.name.isNotBlank() &&
                                editor.cardIds.all { id ->
                                    id != null && cards.any { it.id == id }
                                } &&
                                editor.cardIds.distinct().size == 3,
                        onClick = {
                            val deck = LocalDeck(
                                slot = checkNotNull(editor.deckSlot),
                                name = editor.name.trim(),
                                cardIds = editor.cardIds.filterNotNull(),
                            )

                            editor.busy = true
                            editor.error = null

                            scope.launch {
                                try {
                                    onSaveDeck(deck)

                                    decks = decks.filterNot {
                                        it.slot == deck.slot
                                    } + deck

                                    editor.busy = false
                                    editor.close()
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    editor.error =
                                        "Could not save the deck. Try again."
                                } finally {
                                    editor.busy = false
                                }
                            }
                        },
                    ) {
                        Text(if (editor.busy) "Saving…" else "Save")
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = {
                if (!editor.busy) confirmDelete = false
            },
            title = { Text("Delete deck?") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Delete this deck? Its cards will stay in your collection."
                    )
                    editor.error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !editor.busy,
                    onClick = {
                        val slot = checkNotNull(editor.deckSlot)
                        editor.busy = true
                        editor.error = null

                        scope.launch {
                            try {
                                onDeleteDeck(slot)
                                decks = decks.filterNot {
                                    it.slot == slot
                                }
                                confirmDelete = false
                                editor.busy = false
                                editor.close()
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                editor.error =
                                    "Could not delete the deck. Try again."
                            } finally {
                                editor.busy = false
                            }
                        }
                    },
                ) {
                    Text(if (editor.busy) "Deleting…" else "Delete")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !editor.busy,
                    onClick = { confirmDelete = false },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SavedDeckTile(
    title: String,
    subtitle: String,
    symbol: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 120.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                symbol,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                title,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DeckCardSlot(
    index: Int,
    card: LocalCard?,
    cards: List<LocalCard>,
    editor: DeckEditorState,
    modifier: Modifier,
) {
    var hovering by remember { mutableStateOf(false) }

    val acceptDrop by rememberUpdatedState<(String) -> Boolean>(
        { id ->
            if (cards.any { it.id == id }) {
                editor.addCard(id, index)
            } else {
                false
            }
        }
    )

    val target = remember(index, editor) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                hovering = true
            }

            override fun onExited(event: DragAndDropEvent) {
                hovering = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                hovering = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                hovering = false

                val clip = event.toAndroidDragEvent().clipData
                    ?: return false

                if (clip.itemCount != 1) return false

                val id = clip.getItemAt(0).text?.toString()
                    ?: return false

                return acceptDrop(id)
            }
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .border(
                    width = if (
                        hovering || editor.activeSlot == index
                    ) 3.dp else 1.dp,
                    color = if (
                        hovering || editor.activeSlot == index
                    ) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = RoundedCornerShape(12.dp),
                )
                .dragAndDropTarget(
                    shouldStartDragAndDrop = {
                        !editor.busy && CARD_MIME in it.mimeTypes()
                    },
                    target = target,
                )
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (card == null) {
                TextButton(
                    onClick = { editor.activeSlot = index },
                    enabled = !editor.busy,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text(
                        "+",
                        style = MaterialTheme.typography.displaySmall,
                    )
                }
            } else {
                CollectionCard(
                    card = card,
                    onClick = {
                        if (!editor.busy) editor.activeSlot = index
                    },
                )
            }
        }

        TextButton(
            onClick = { editor.removeCard(index) },
            enabled = card != null && !editor.busy,
            contentPadding = PaddingValues(4.dp),
        ) {
            Text("Remove")
        }
    }
}

@Composable
fun Modifier.deckCardGestures(
    cardId: String,
    editor: DeckEditorState,
): Modifier {
    if (!editor.isOpen || editor.busy) return this

    return this
        .dragAndDropSource { _ ->
            DragAndDropTransferData(
                clipData = ClipData(
                    "CardBattle card",
                    arrayOf(CARD_MIME),
                    ClipData.Item(cardId),
                )
            )
        }
        .pointerInput(cardId, editor) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()

                    if (
                        event.type == PointerEventType.Press &&
                        event.buttons.isSecondaryPressed
                    ) {
                        event.changes.forEach { it.consume() }
                        editor.addCard(cardId)
                    }
                }
            }
        }
}