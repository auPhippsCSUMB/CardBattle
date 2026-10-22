package com.example.cardbattle.ui.battle

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.example.cardbattle.data.remote.*
import com.example.cardbattle.ui.cards.RemoteCardArt
import com.example.cardbattle.ui.cards.cardApiError
import com.example.cardbattle.ui.cards.cardClassColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

enum class RoundWinner {
    PLAYER,
    OPPONENT,
    TIE,
}

private val classAdvantages = mapOf(
    CardType.WARRIOR to setOf(
        CardType.BRAWLER,
        CardType.RANGER,
    ),
    CardType.BRAWLER to setOf(
        CardType.RANGER,
        CardType.MAGE,
    ),
    CardType.RANGER to setOf(
        CardType.MAGE,
        CardType.ROGUE,
    ),
    CardType.MAGE to setOf(
        CardType.ROGUE,
        CardType.WARRIOR,
    ),
    CardType.ROGUE to setOf(
        CardType.WARRIOR,
        CardType.BRAWLER,
    ),
)

internal fun compareClasses(
    player: CardType,
    opponent: CardType,
): RoundWinner =
    when {
        player == opponent -> RoundWinner.TIE

        opponent in classAdvantages.getValue(player) ->
            RoundWinner.PLAYER

        else -> RoundWinner.OPPONENT
    }

data class BattleCard(
    val name: String,
    val type: CardType,
    val source: CardResponse? = null,
)

data class BattleDeck(
    val id: Long,
    val name: String,
    val ownerId: Long?,
    val cards: List<BattleCard>,
)

data class BattleMatch(
    val player: BattleDeck,
    val opponent: BattleDeck,
)

class BattleViewModel : ViewModel() {
    var selectedDeckId by mutableStateOf<Long?>(null)
    var friendCode by mutableStateOf("00000")

    var match by mutableStateOf<BattleMatch?>(null)
        private set

    var roundIndex by mutableIntStateOf(0)
        private set

    var revealed by mutableStateOf(false)
        private set

    var finished by mutableStateOf(false)
        private set

    var statsRefreshCounter by mutableIntStateOf(0)
        private set

    private var pendingRequest: ApiDeckBattleRequest? = null

    suspend fun play(
        api: CardBattleClient,
        playerDeckId: Long,
        opponentDeckId: Long,
        userId: Long,
    ) {
        if (opponentDeckId == 0L) {
            pendingRequest = null

            val player = loadOwnDeck(
                api,
                playerDeckId,
                userId,
            )

            val opponent = botDeck()

            startPrepared(
                player.copy(cards = player.cards.shuffled()),
                opponent.copy(cards = opponent.cards.shuffled()),
            )

            // Practice matches never write wins or losses.
            return
        }

        val previous = pendingRequest

        val request = if (
            previous != null &&
            previous.playerDeckId == playerDeckId &&
            previous.opponentDeckId == opponentDeckId
        ) {
            previous
        } else {
            ApiDeckBattleRequest(
                requestId = UUID.randomUUID().toString(),
                playerDeckId = playerDeckId,
                opponentDeckId = opponentDeckId,
            ).also {
                pendingRequest = it
            }
        }

        val result = api.playDeckBattle(request)

        val player = convertDeck(
            result.player.deck,
            result.player.cards,
        )

        val opponent = convertDeck(
            result.opponent.deck,
            result.opponent.cards,
        )

        // Keep the exact order resolved by the server.
        startPrepared(player, opponent)

        pendingRequest = null
        statsRefreshCounter++
    }

    private fun startPrepared(
        player: BattleDeck,
        opponent: BattleDeck,
    ) {
        require(player.cards.size == 3)
        require(opponent.cards.size == 3)

        roundIndex = 0
        revealed = false
        finished = false
        match = BattleMatch(player, opponent)
    }

    fun revealRound() {
        revealed = true
    }

    fun nextRound() {
        if (!revealed) return

        if (roundIndex < 2) {
            revealed = false
            roundIndex++
        } else {
            finished = true
        }
    }

    fun clearMatch() {
        match = null
        pendingRequest = null
        roundIndex = 0
        revealed = false
        finished = false
    }

    fun roundWinner(index: Int): RoundWinner {
        val current = checkNotNull(match)

        return compareClasses(
            current.player.cards[index].type,
            current.opponent.cards[index].type,
        )
    }

    fun score(forPlayer: Boolean): Int {
        if (match == null) return 0

        val completedRounds = if (finished) {
            3
        } else {
            roundIndex + if (revealed) 1 else 0
        }

        return (0 until completedRounds).count { index ->
            val winner = roundWinner(index)

            if (forPlayer) {
                winner != RoundWinner.OPPONENT
            } else {
                winner != RoundWinner.PLAYER
            }
        }
    }
}

private fun botDeck(): BattleDeck =
    BattleDeck(
        id = 0,
        name = "BOT",
        ownerId = null,
        cards = List(3) { index ->
            BattleCard(
                name = "Bot Warrior ${index + 1}",
                type = CardType.WARRIOR,
            )
        },
    )

private fun convertDeck(
    deck: ApiDeck,
    entries: List<ApiDeckEntry>,
): BattleDeck {
    require(entries.size == 3) {
        "This deck needs exactly three cards before it can battle."
    }

    require(entries.map { it.inventoryId }.distinct().size == 3) {
        "This deck must contain three different owned copies."
    }

    require(entries.all {
        it.deckId == deck.id && it.userId == deck.userId
    }) {
        "The deck contains an invalid membership."
    }

    return BattleDeck(
        id = deck.id,
        name = deck.name,
        ownerId = deck.userId,
        cards = entries.map { entry ->
            val card = requireNotNull(entry.card) {
                "One of the deck's cards could not be loaded."
            }

            BattleCard(
                name = card.name,
                type = card.type,
                source = card,
            )
        },
    )
}

private suspend fun loadOwnDeck(
    api: CardBattleClient,
    deckId: Long,
    userId: Long,
): BattleDeck {
    val deck = api.getDeck(deckId)

    require(deck.userId == userId) {
        "Select one of your own decks."
    }

    return convertDeck(
        deck = deck,
        entries = api.deckEntries(deckId),
    )
}

@Composable
fun BattleScreen(
    api: CardBattleClient,
    repository: CardRepository,
    userId: Long,
    state: BattleViewModel,
    onBack: () -> Unit,
) {
    BackHandler {
        if (state.match != null) {
            state.clearMatch()
        } else {
            onBack()
        }
    }

    val currentMatch = state.match

    if (currentMatch == null) {
        BattleSetupScreen(
            api = api,
            repository = repository,
            userId = userId,
            state = state,
            onBack = onBack,
        )
    } else {
        BattleMatchScreen(
            api = api,
            state = state,
            match = currentMatch,
            onBack = { state.clearMatch() },
        )
    }
}

@Composable
private fun BattleSetupScreen(
    api: CardBattleClient,
    repository: CardRepository,
    userId: Long,
    state: BattleViewModel,
    onBack: () -> Unit,
) {
    var decks by remember {
        mutableStateOf<List<ApiDeck>>(emptyList())
    }

    var selectedDeck by remember {
        mutableStateOf<BattleDeck?>(null)
    }

    var loadingDecks by remember { mutableStateOf(true) }
    var loadingSelection by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    BackHandler(enabled = busy) {}

    LaunchedEffect(userId, refresh) {
        loadingDecks = true
        error = null

        try {
            decks = repository.loadDecks(userId)

            if (decks.none { it.id == state.selectedDeckId }) {
                state.selectedDeckId = decks.firstOrNull()?.id
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = cardApiError(failure)
        } finally {
            loadingDecks = false
        }
    }

    LaunchedEffect(state.selectedDeckId, refresh) {
        selectedDeck = null

        val id = state.selectedDeckId
            ?: return@LaunchedEffect

        loadingSelection = true
        error = null

        try {
            selectedDeck = loadOwnDeck(api, id, userId)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = cardApiError(failure)
        } finally {
            loadingSelection = false
        }
    }

    val opponentId = state.friendCode.toLongOrNull()

    val validOpponentCode =
        opponentId != null &&
                opponentId in 0L..Int.MAX_VALUE.toLong()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    enabled = !busy,
                    onClick = onBack,
                ) {
                    Text("Back")
                }

                Text(
                    "Battle",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.weight(1f))

                TextButton(
                    enabled = !busy &&
                            !loadingDecks &&
                            !loadingSelection,
                    onClick = { refresh++ },
                ) {
                    Text("Refresh")
                }
            }

            if (loadingDecks || loadingSelection || busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            Text(
                "Choose your deck",
                style = MaterialTheme.typography.titleLarge,
            )

            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    enabled = decks.isNotEmpty() &&
                            !loadingDecks &&
                            !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { menuOpen = true },
                ) {
                    Text(
                        decks.firstOrNull {
                            it.id == state.selectedDeckId
                        }?.name ?: "Select a deck",
                    )
                }

                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    decks.forEach { deck ->
                        DropdownMenuItem(
                            text = {
                                Text("${deck.name} · #${deck.id}")
                            },
                            onClick = {
                                selectedDeck = null
                                state.selectedDeckId = deck.id
                                menuOpen = false
                            },
                        )
                    }
                }
            }

            if (!loadingDecks && decks.isEmpty()) {
                Text(
                    "Create three cards and save a deck in My Cards first.",
                    textAlign = TextAlign.Center,
                )
            }

            state.selectedDeckId?.let { id ->
                Text(
                    "Your deck ID",
                    style = MaterialTheme.typography.labelLarge,
                )

                Text(
                    id.toString().padStart(5, '0'),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    "Share this number with a friend.",
                    textAlign = TextAlign.Center,
                )
            }

            selectedDeck?.let { deck ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    deck.cards.forEach { card ->
                        BattleCardView(
                            card = card,
                            api = api,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Text("3 cards ready")
            }

            HorizontalDivider()

            OutlinedTextField(
                value = state.friendCode,
                onValueChange = { value ->
                    state.friendCode = value
                        .filter { it in '0'..'9' }
                        .take(10)
                },
                enabled = !busy,
                label = { Text("Friend's deck ID") },
                supportingText = {
                    Text("Enter 00000 to battle the bot.")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            Button(
                enabled = selectedDeck != null &&
                        validOpponentCode &&
                        !loadingDecks &&
                        !loadingSelection &&
                        !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val ownId = state.selectedDeckId
                        ?: return@Button

                    val friendId = state.friendCode.toLongOrNull()
                        ?: return@Button

                    busy = true
                    error = null

                    scope.launch {
                        try {
                            state.play(
                                api = api,
                                playerDeckId = ownId,
                                opponentDeckId = friendId,
                                userId = userId,
                            )
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: Exception) {
                            error = cardApiError(failure)
                        } finally {
                            busy = false
                        }
                    }
                },
            ) {
                Text(if (busy) "Preparing battle…" else "Start battle")
            }

            Text(
                "Win a round: 1 point. Tie: 1 point each.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BattleMatchScreen(
    api: CardBattleClient,
    state: BattleViewModel,
    match: BattleMatch,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var rematching by remember { mutableStateOf(false) }
    var rematchError by remember {
        mutableStateOf<String?>(null)
    }

    BackHandler(enabled = rematching) {}

    val playerScore = state.score(forPlayer = true)
    val opponentScore = state.score(forPlayer = false)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TextButton(
                enabled = !rematching,
                onClick = onBack,
                modifier = Modifier.align(Alignment.Start),
            ) {
                Text(
                    if (state.finished) "Choose decks"
                    else "Leave battle"
                )
            }

            Text(
                "${match.player.name} vs ${match.opponent.name}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Text(
                "$playerScore : $opponentScore",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
            )

            if (state.finished) {
                Text(
                    when {
                        playerScore > opponentScore -> "You win!"
                        playerScore < opponentScore -> "You lose!"
                        else -> "It's a tie!"
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )

                repeat(3) { index ->
                    Text(
                        "Round ${index + 1}: " +
                                when (state.roundWinner(index)) {
                                    RoundWinner.PLAYER -> "You win"
                                    RoundWinner.OPPONENT -> "Opponent wins"
                                    RoundWinner.TIE -> "Tie — 1 point each"
                                }
                    )
                }

                rematchError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }

                Button(
                    enabled = !rematching,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        rematching = true
                        rematchError = null

                        scope.launch {
                            try {
                                state.play(
                                    api = api,
                                    playerDeckId = match.player.id,
                                    opponentDeckId = match.opponent.id,
                                    userId = checkNotNull(
                                        match.player.ownerId
                                    ),
                                )
                            } catch (failure: CancellationException) {
                                throw failure
                            } catch (failure: Exception) {
                                rematchError = cardApiError(failure)
                            } finally {
                                rematching = false
                            }
                        }
                    },
                ) {
                    Text(
                        if (rematching) "Preparing rematch…"
                        else "Rematch"
                    )
                }

                OutlinedButton(
                    enabled = !rematching,
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Choose decks")
                }
            } else {
                val index = state.roundIndex
                val winner = state.roundWinner(index)

                var arrived by remember(match, index) {
                    mutableStateOf(false)
                }

                LaunchedEffect(match, index) {
                    arrived = true

                    if (!state.revealed) {
                        delay(850)
                        state.revealRound()
                    }
                }

                Text(
                    "Round ${index + 1} of 3",
                    style = MaterialTheme.typography.headlineSmall,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BattleCardView(
                        card = match.player.cards[index],
                        api = api,
                        modifier = Modifier.weight(1f),
                        arrived = arrived,
                        defeated = state.revealed &&
                                winner == RoundWinner.OPPONENT,
                    )

                    BattleCardView(
                        card = match.opponent.cards[index],
                        api = api,
                        modifier = Modifier.weight(1f),
                        arrived = arrived,
                        defeated = state.revealed &&
                                winner == RoundWinner.PLAYER,
                    )
                }

                Text(
                    text = if (!state.revealed) {
                        "Ready…"
                    } else {
                        when (winner) {
                            RoundWinner.PLAYER ->
                                "Your ${match.player.cards[index].type.label} wins!"

                            RoundWinner.OPPONENT ->
                                "Their ${match.opponent.cards[index].type.label} wins!"

                            RoundWinner.TIE ->
                                "Tie! Both decks earn 1 point."
                        }
                    },
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.heightIn(min = 60.dp),
                )

                Button(
                    enabled = state.revealed,
                    onClick = { state.nextRound() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (index == 2) "See results"
                        else "Next round"
                    )
                }
            }
        }
    }
}

@Composable
private fun BattleCardView(
    card: BattleCard,
    api: CardBattleClient,
    modifier: Modifier = Modifier,
    arrived: Boolean = true,
    defeated: Boolean = false,
) {
    val entrance by animateFloatAsState(
        targetValue = if (arrived) 1f else 0.65f,
        animationSpec = tween(350),
        label = "cardEntrance",
    )

    val fall by animateFloatAsState(
        targetValue = if (defeated) 1f else 0f,
        animationSpec = tween(650),
        label = "cardDefeat",
    )

    Card(
        modifier = modifier.graphicsLayer {
            scaleX = entrance
            scaleY = entrance
            alpha = 1f - fall * 0.8f
            translationY = fall * 100f
            rotationZ = fall * 12f
        },
        colors = CardDefaults.cardColors(
            containerColor = cardClassColor(card.type),
            contentColor = Color.White,
        ),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                card.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            val source = card.source

            if (source == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "BOT",
                        color = Color.Black,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                    )
                }
            } else {
                RemoteCardArt(
                    card = source,
                    api = api,
                )
            }

            Text(
                card.type.label,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}