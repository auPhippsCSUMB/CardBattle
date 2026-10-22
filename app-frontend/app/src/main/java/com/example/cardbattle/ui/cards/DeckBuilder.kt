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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cardbattle.data.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val CARD_MIME =
    "application/vnd.cardbattle.inventory-id"

class DeckEditorState {
    var isOpen by mutableStateOf(false)
    var serverDeck by mutableStateOf<ApiDeck?>(null)
    var name by mutableStateOf("")
    var activeSlot by mutableIntStateOf(0)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    val slots = mutableStateListOf<ApiInventoryEntry?>(
        null, null, null
    )

    fun open(
        deck: ApiDeck?,
        entries: List<ApiDeckEntry>,
        inventory: List<ApiInventoryEntry>,
    ) {
        require(entries.size <= 3) {
            "This deck contains more than three cards."
        }

        val resolved = entries.map { entry ->
            requireNotNull(
                inventory.firstOrNull { it.id == entry.inventoryId }
            ) {
                "A deck entry references a copy missing from your inventory."
            }
        }

        serverDeck = deck
        name = deck?.name ?: "New deck"

        repeat(3) {
            slots[it] = resolved.getOrNull(it)
        }

        activeSlot = slots.indexOfFirst { it == null }
            .takeIf { it >= 0 } ?: 0

        error = null
        isOpen = true
    }

    fun close() {
        if (!busy) {
            isOpen = false
            error = null
        }
    }

    fun addCard(
        copy: ApiInventoryEntry,
        target: Int = activeSlot,
    ): Boolean {
        if (!isOpen || busy || target !in 0..2) return false

        if (slots.withIndex().any {
                it.index != target && it.value?.id == copy.id
            }) {
            error = "That owned copy is already in this deck."
            return false
        }

        slots[target] = copy

        activeSlot = slots.indexOfFirst { it == null }
            .takeIf { it >= 0 } ?: target

        error = null
        return true
    }

    fun removeCard(index: Int) {
        if (!busy) {
            slots[index] = null
            activeSlot = index
            error = null
        }
    }

    suspend fun save(
        api: CardBattleClient,
        userId: Long,
    ) {
        val chosen = slots.filterNotNull()

        require(chosen.size == 3) {
            "Fill all three card slots."
        }
        require(chosen.map { it.id }.distinct().size == 3) {
            "Choose three different owned copies."
        }
        require(chosen.all { it.card != null }) {
            "One of these cards is missing its details."
        }

        val trimmedName = name.trim()
        require(trimmedName.length in 1..150) {
            "Deck names must contain 1–150 characters."
        }

        val saved = serverDeck ?: api.createDeck(trimmedName).also {

            serverDeck = it
        }

        if (saved.name != trimmedName) {
            serverDeck = api.renameDeck(saved.id, trimmedName)
        }

        val existing = api.deckEntries(saved.id)

        if (
            existing.map { it.inventoryId } ==
            chosen.map { it.id }
        ) {
            existing.zip(chosen).forEach { (entry, copy) ->
                val type = checkNotNull(copy.card).type

                if (entry.type != type) {
                    api.updateDeckEntryClass(
                        deckId = saved.id,
                        membershipId = entry.id,
                        type = type,
                    )
                }
            }
        } else {
            existing.forEach { entry ->
                api.removeDeckEntry(
                    deckId = saved.id,
                    membershipId = entry.id,
                )
            }

            chosen.forEach { copy ->
                api.addDeckEntry(
                    deckId = saved.id,
                    inventoryId = copy.id,
                    userId = userId,
                    type = checkNotNull(copy.card).type,
                )
            }
        }
    }
}

@Composable
fun DeckSection(
    api: CardBattleClient,
    userId: Long,
    decks: List<ApiDeck>,
    inventory: List<ApiInventoryEntry>,
    editor: DeckEditorState,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

    fun perform(action: suspend () -> Unit) {
        if (editor.busy) return

        editor.busy = true
        editor.error = null

        scope.launch {
            try {
                action()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                editor.error = cardApiError(failure)
            } finally {
                editor.busy = false
            }
        }
    }

    fun finish() {
        editor.busy = false
        editor.close()
        onChanged()
    }

    BackHandler(enabled = editor.isOpen) {
        if (!editor.busy) {
            editor.close()
            onChanged()
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (editor.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        if (!editor.isOpen) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    DeckTile(
                        title = "Starter Deck",
                        subtitle = "Coming soon",
                        enabled = false,
                        onClick = {},
                    )
                }

                items(decks, key = { it.id }) { deck ->
                    DeckTile(
                        title = deck.name,
                        subtitle = "Tap to edit",
                        enabled = !editor.busy,
                        onClick = {
                            perform {
                                val current = api.getDeck(deck.id)
                                val entries = api.deckEntries(deck.id)

                                editor.open(
                                    current,
                                    entries,
                                    inventory,
                                )
                            }
                        },
                    )
                }

                repeat(maxOf(1, 2 - decks.size)) {
                    item {
                        DeckTile(
                            title = "New deck",
                            subtitle = "+",
                            enabled = !editor.busy,
                            onClick = {
                                editor.open(
                                    deck = null,
                                    entries = emptyList(),
                                    inventory = inventory,
                                )
                            },
                        )
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = editor.name,
                onValueChange = {
                    if (it.length <= 150) editor.name = it
                },
                label = { Text("Deck name") },
                singleLine = true,
                enabled = !editor.busy,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(3) { index ->
                    DeckCardSlot(
                        index = index,
                        api = api,
                        inventory = inventory,
                        editor = editor,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Text(
                "Select a slot, then tap or right-click a card. " +
                        "You can also long-press and drag it into a slot.",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextButton(
                    enabled = !editor.busy,
                    onClick = {
                        editor.close()
                        onChanged()
                    },
                ) {
                    Text("Cancel")
                }

                TextButton(
                    enabled = !editor.busy && inventory.size >= 3,
                    onClick = {
                        val choices = inventory.shuffled().take(3)

                        repeat(3) {
                            editor.slots[it] = choices[it]
                        }

                        editor.error = null
                    },
                ) {
                    Text("Randomize")
                }

                if (editor.serverDeck != null) {
                    TextButton(
                        enabled = !editor.busy,
                        onClick = { confirmDelete = true },
                    ) {
                        Text("Delete")
                    }
                }
            }

            Button(
                enabled = !editor.busy &&
                        editor.name.trim().length in 1..150 &&
                        editor.slots.all { it != null },
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    perform {
                        editor.save(api, userId)
                        finish()
                    }
                },
            ) {
                Text(if (editor.busy) "Saving…" else "Save deck")
            }
        }

        editor.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )

            if (editor.isOpen) {
                Text(
                    "Some requests may already have saved. " +
                            "Retry Save to apply this layout again.",
                    style = MaterialTheme.typography.bodySmall,
                )
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
                    Text("Its cards will stay in your collection.")

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
                        perform {
                            api.deleteDeck(
                                checkNotNull(editor.serverDeck).id
                            )

                            confirmDelete = false
                            finish()
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
private fun DeckTile(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .width(120.dp)
            .heightIn(min = 110.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, textAlign = TextAlign.Center)
            Text(subtitle, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun DeckCardSlot(
    index: Int,
    api: CardBattleClient,
    inventory: List<ApiInventoryEntry>,
    editor: DeckEditorState,
    modifier: Modifier,
) {
    val copy = editor.slots[index]

    val acceptDrop by rememberUpdatedState<(Long) -> Boolean>(
        { id ->
            val owned = inventory.firstOrNull { it.id == id }

            if (owned == null) {
                false
            } else {
                editor.addCard(owned, index)
            }
        }
    )

    val target = remember(index, editor) {
        object : DragAndDropTarget {
            override fun onDrop(
                event: DragAndDropEvent,
            ): Boolean {
                val clip = event.toAndroidDragEvent().clipData
                    ?: return false

                if (clip.itemCount != 1) return false

                val id = clip.getItemAt(0)
                    .text
                    ?.toString()
                    ?.toLongOrNull()
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
            Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .border(
                    width = if (editor.activeSlot == index) {
                        3.dp
                    } else {
                        1.dp
                    },
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp),
                )
                .dragAndDropTarget(
                    shouldStartDragAndDrop = {
                        !editor.busy && CARD_MIME in it.mimeTypes()
                    },
                    target = target,
                )
                .padding(5.dp),
            contentAlignment = Alignment.Center,
        ) {
            val card = copy?.card

            if (card == null) {
                TextButton(
                    enabled = !editor.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    onClick = { editor.activeSlot = index },
                ) {
                    Text(
                        "+",
                        style = MaterialTheme.typography.displaySmall,
                    )
                }
            } else {
                CollectionCard(
                    card = card,
                    api = api,
                    enabled = !editor.busy,
                    onClick = { editor.activeSlot = index },
                )
            }
        }

        TextButton(
            enabled = copy != null && !editor.busy,
            onClick = { editor.removeCard(index) },
        ) {
            Text("Remove")
        }
    }
}

@Composable
fun Modifier.deckCardGestures(
    copy: ApiInventoryEntry,
    editor: DeckEditorState,
): Modifier {
    if (!editor.isOpen || editor.busy) return this

    return this
        .dragAndDropSource { _ ->
            DragAndDropTransferData(
                clipData = ClipData(
                    "CardBattle owned card",
                    arrayOf(CARD_MIME),
                    ClipData.Item(copy.id.toString()),
                )
            )
        }
        .pointerInput(copy, editor) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()

                    if (
                        event.type == PointerEventType.Press &&
                        event.buttons.isSecondaryPressed
                    ) {
                        event.changes.forEach { it.consume() }
                        editor.addCard(copy)
                    }
                }
            }
        }
}