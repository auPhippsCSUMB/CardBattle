package com.example.cardbattle.data.local

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import com.example.cardbattle.data.remote.CardType
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
data class LocalCard(
    val id: String,
    val name: String,
    val type: CardType,
    val pngBase64: String,
    val createdAt: Long = 0L,
)

class LocalCardStore(
    context: Context,
    userId: Int,
) {
    private val file = AtomicFile(
        File(
            context.applicationContext.filesDir,
            "local-cards-$userId.json",
        )
    )

    private val json = Json {
        ignoreUnknownKeys = true
    }

    companion object {
        private val mutex = Mutex()
    }

    suspend fun load(): List<LocalCard> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                readCards()
            }
        }

    suspend fun save(
        id: String,
        name: String,
        type: CardType,
        pngBytes: ByteArray,
    ) {
        require(name.trim().length in 1..150) {
            "Card name must contain 1–150 characters."
        }
        require(pngBytes.isNotEmpty() && pngBytes.size <= 1_048_576) {
            "Drawing must be no larger than 1 MiB."
        }

        withContext(Dispatchers.IO) {
            mutex.withLock {
                val existing = readCards()
                val previous = existing.firstOrNull { it.id == id }

                val card = LocalCard(
                    id = id,
                    name = name.trim(),
                    type = type,
                    pngBase64 = Base64.encodeToString(
                        pngBytes,
                        Base64.NO_WRAP,
                    ),
                    createdAt = previous?.createdAt
                        ?: System.currentTimeMillis(),
                )

                // Updating a card preserves its original position and date.
                val updated = if (previous == null) {
                    existing + card
                } else {
                    existing.map {
                        if (it.id == id) card else it
                    }
                }

                val bytes = json.encodeToString(updated)
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
    }

    private fun readCards(): List<LocalCard> {
        val input = try {
            file.openRead()
        } catch (error: FileNotFoundException) {
            if (file.baseFile.exists()) throw error
            return emptyList()
        }

        return input.bufferedReader(Charsets.UTF_8).use {
            json.decodeFromString<List<LocalCard>>(it.readText())
        }
    }
}