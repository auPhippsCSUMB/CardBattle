package com.example.cardbattle.data.local

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException

@Serializable
data class LocalDeck(
    val slot: Int,
    val name: String,
    val cardIds: List<String>,
)

class LocalDeckStore(
    context: Context,
    userId: Int,
) {
    private val cardStore = LocalCardStore(context, userId)

    private val file = AtomicFile(
        File(
            context.applicationContext.filesDir,
            "local-decks-$userId.json",
        )
    )

    private val json = Json {
        ignoreUnknownKeys = true
    }

    companion object {
        private val mutex = Mutex()
    }

    suspend fun load(): List<LocalDeck> =
        withContext(Dispatchers.IO) {
            mutex.withLock { readDecks() }
        }

    suspend fun save(deck: LocalDeck) {
        require(deck.slot in 0..1) {
            "Invalid custom deck slot."
        }
        require(deck.name.trim().length in 1..40) {
            "Deck names must contain 1–40 characters."
        }
        require(
            deck.cardIds.size == 3 &&
                    deck.cardIds.distinct().size == 3
        ) {
            "Choose three different cards."
        }

        val knownCards = cardStore.load().map { it.id }.toSet()
        require(deck.cardIds.all { it in knownCards }) {
            "One of these cards is no longer available."
        }

        withContext(Dispatchers.IO) {
            mutex.withLock {
                val updated = readDecks().filterNot {
                    it.slot == deck.slot
                } + deck.copy(name = deck.name.trim())

                writeDecks(updated)
            }
        }
    }

    suspend fun delete(slot: Int) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                writeDecks(
                    readDecks().filterNot { it.slot == slot }
                )
            }
        }
    }

    private fun readDecks(): List<LocalDeck> {
        val input = try {
            file.openRead()
        } catch (error: FileNotFoundException) {
            if (file.baseFile.exists()) throw error
            return emptyList()
        }

        return input.bufferedReader(Charsets.UTF_8).use {
            json.decodeFromString<List<LocalDeck>>(it.readText())
        }
    }

    private fun writeDecks(decks: List<LocalDeck>) {
        val bytes = json.encodeToString(decks)
            .toByteArray(Charsets.UTF_8)

        val output = file.startWrite()

        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }
}