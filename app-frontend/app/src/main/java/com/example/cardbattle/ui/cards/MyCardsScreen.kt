package com.example.cardbattle.ui.cards

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.cardbattle.data.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class LibraryTile(
    val key: String,
    val card: CardResponse,
    val ownership: ApiInventoryEntry? = null,
)

internal fun cardApiError(error: Exception): String =
    when (error) {
        is BackendHttpException -> when (error.statusCode) {
            400 ->
                "The server rejected this request. Check the entered values."

            401 ->
                "Your sign-in expired. Log out and sign in again."

            403 ->
                "You do not have permission to do that."

            404 ->
                "The requested card, deck, or artwork was not found."

            409 ->
                "This item is still referenced by other records. " +
                        "Remove it from decks first. " +
                        "Battle history may also prevent deletion."

            413 ->
                "The drawing is too large. The maximum is 1 MiB."

            else ->
                "The server returned HTTP ${error.statusCode}. Try again."
        }

        else ->
            error.message ?: "The request failed. Please try again."
    }

internal fun cardClassColor(type: CardType): Color =
    when (type) {
        CardType.WARRIOR -> Color(0xFF963D45)
        CardType.BRAWLER -> Color(0xFF925027)
        CardType.RANGER -> Color(0xFF326448)
        CardType.MAGE -> Color(0xFF69469B)
        CardType.ROGUE -> Color(0xFF364C68)
    }

@Composable
fun MyCardsScreen(
    api: CardBattleClient,
    repository: CardRepository,
    userId: Long,
    onBack: () -> Unit,
    onEditCard: suspend (CardResponse) -> Unit,
    onCardDeleted: (Long) -> Unit,
) {
    var inventory by remember {
        mutableStateOf<List<ApiInventoryEntry>>(emptyList())
    }

    var authored by remember {
        mutableStateOf<List<CardResponse>>(emptyList())
    }

    var decks by remember {
        mutableStateOf<List<ApiDeck>>(emptyList())
    }

    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<LibraryTile?>(null) }

    val editor = remember { DeckEditorState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(userId, refresh) {
        loading = true
        error = null

        try {
            val loadedAuthored = repository.loadAuthoredCards()
            val loadedInventory = repository.loadInventory(userId)
            val loadedDecks = repository.loadDecks(userId)

            val resolvedInventory = loadedInventory.map { entry ->
                if (entry.card != null) {
                    entry
                } else {
                    entry.copy(
                        card = api.getCatalogCard(entry.cardId)
                    )
                }
            }

            authored = loadedAuthored
            inventory = resolvedInventory
            decks = loadedDecks
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = cardApiError(failure)
        } finally {
            loading = false
        }
    }

    BackHandler(enabled = busy || editor.busy) {}

    fun perform(action: suspend () -> Unit) {
        if (busy || editor.busy) return

        busy = true
        error = null

        scope.launch {
            try {
                action()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                error = cardApiError(failure)
            } finally {
                busy = false
            }
        }
    }

    val ownedTiles = inventory.mapNotNull { owned ->
        owned.card?.let { card ->
            LibraryTile(
                key = "owned:${owned.id}",
                card = card,
                ownership = owned,
            )
        }
    }

    val ownedDesignIds = ownedTiles
        .map { it.card.id }
        .toSet()

    // Include authored designs that do not currently have an owned copy.
    // This also keeps designs from a partially completed save accessible.
    val createdWithoutCopy = authored
        .filterNot { it.id in ownedDesignIds }
        .map { card ->
            LibraryTile(
                key = "created:${card.id}",
                card = card,
            )
        }

    // Decks accept owned inventory copies, so only those appear
    // while the deck editor is open.
    val tiles = (
            ownedTiles +
                    if (editor.isOpen) emptyList() else createdWithoutCopy
            ).sortedByDescending { it.card.id }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    enabled = !busy && !editor.busy,
                    onClick = {
                        if (editor.isOpen) {
                            editor.close()
                            refresh++
                        } else {
                            onBack()
                        }
                    },
                ) {
                    Text("Back")
                }

                Text(
                    text = "My cards",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.weight(1f))

                TextButton(
                    enabled = !loading &&
                            !busy &&
                            !editor.busy &&
                            !editor.isOpen,
                    onClick = { refresh++ },
                ) {
                    Text("Refresh")
                }
            }

            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                if (!loading && selected == null) {
                    TextButton(
                        enabled = !busy && !editor.busy,
                        onClick = { refresh++ },
                    ) {
                        Text("Retry loading")
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (!loading) {
                        DeckSection(
                            api = api,
                            userId = userId,
                            decks = decks,
                            inventory = inventory,
                            editor = editor,
                            onChanged = { refresh++ },
                        )
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Your cards",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                if (!loading && error == null && tiles.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Create some cards to get started!",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                items(
                    items = tiles,
                    key = { it.key },
                ) { tile ->
                    val gestures = if (tile.ownership != null) {
                        Modifier.deckCardGestures(
                            copy = tile.ownership,
                            editor = editor,
                        )
                    } else {
                        Modifier
                    }

                    CollectionCard(
                        card = tile.card,
                        api = api,
                        modifier = gestures,
                        enabled = !busy && !editor.busy,
                        onClick = {
                            if (editor.isOpen) {
                                tile.ownership?.let {
                                    editor.addCard(it)
                                }
                            } else {
                                error = null
                                selected = tile
                            }
                        },
                    )
                }
            }
        }
    }

    selected?.let { tile ->
        var card by remember(tile.key) {
            mutableStateOf(tile.card)
        }

        var stats by remember(tile.key) {
            mutableStateOf<ApiStats?>(null)
        }

        var detailError by remember(tile.key) {
            mutableStateOf<String?>(null)
        }

        var deleteAction by remember(tile.key) {
            mutableStateOf<String?>(null)
        }

        LaunchedEffect(tile.key) {
            try {
                card = if (tile.card.authorId == userId) {
                    api.getMyCard(tile.card.id)
                } else {
                    api.getCatalogCard(tile.card.id)
                }

                stats = api.cardStats(card.id)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                detailError = cardApiError(failure)
            }
        }

        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    selected = null
                    error = null
                }
            },
            title = {
                Text(
                    text = card.name,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RemoteCardArt(
                        card = card,
                        api = api,
                    )

                    Text(
                        text = card.type.label,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )

                    stats?.let {
                        Text("${it.wins} wins · ${it.losses} losses")
                    }

                    detailError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    error?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    if (card.authorId == userId) {
                        Button(
                            enabled = !busy,
                            onClick = {
                                perform {
                                    onEditCard(card)
                                    selected = null
                                }
                            },
                        ) {
                            Text(
                                if (busy) "Opening…" else "Edit card"
                            )
                        }
                    }

                    if (tile.ownership != null) {
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                error = null
                                deleteAction = "copy"
                            },
                        ) {
                            Text("Remove from collection")
                        }
                    } else if (card.authorId == userId) {
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                error = null
                                deleteAction = "design"
                            },
                        ) {
                            Text("Delete card design")
                        }
                    }
                }
            },
            confirmButton = {},
        )

        deleteAction?.let { action ->
            AlertDialog(
                onDismissRequest = {
                    if (!busy) deleteAction = null
                },
                title = {
                    Text(
                        if (action == "copy") {
                            "Remove owned copy?"
                        } else {
                            "Delete card design?"
                        }
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            if (action == "copy") {
                                "This removes your owned copy. " +
                                        "If you created the design, it remains " +
                                        "in My Cards without an owned copy."
                            } else {
                                "This deletes the underlying design. " +
                                        "Owned copies or battle history " +
                                        "may block deletion."
                            }
                        )

                        error?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            perform {
                                if (action == "copy") {
                                    api.deleteOwnership(
                                        checkNotNull(tile.ownership).id
                                    )
                                } else {
                                    api.deleteCard(card.id)
                                    onCardDeleted(card.id)
                                }

                                deleteAction = null
                                selected = null
                                refresh++
                            }
                        },
                    ) {
                        Text(
                            if (busy) "Deleting…" else "Delete"
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !busy,
                        onClick = { deleteAction = null },
                    ) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
internal fun CollectionCard(
    card: CardResponse,
    api: CardBattleClient,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardClassColor(card.type),
            contentColor = Color.White,
        ),
    ) {
        Column(
            modifier = Modifier.padding(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = card.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )

            RemoteCardArt(
                card = card,
                api = api,
            )

            Text(
                text = card.type.label,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun RemoteCardArt(
    card: CardResponse,
    api: CardBattleClient,
    modifier: Modifier = Modifier,
) {
    var image by remember(card.id, card.imgURL) {
        mutableStateOf<ImageBitmap?>(null)
    }

    var failed by remember(card.id, card.imgURL) {
        mutableStateOf(false)
    }

    LaunchedEffect(card.id, card.imgURL) {
        if (card.imgURL.isNullOrBlank()) return@LaunchedEffect

        try {
            val bytes = api.downloadArt(card.id)

            image = withContext(Dispatchers.Default) {
                requireNotNull(
                    BitmapFactory.decodeByteArray(
                        bytes,
                        0,
                        bytes.size,
                    )
                ) {
                    "Artwork could not be decoded."
                }.asImageBitmap()
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            failed = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val loaded = image

        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = "${card.name} artwork",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = if (failed) "Art unavailable" else "Art",
                color = Color.DarkGray,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}