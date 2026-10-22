package com.example.cardbattle.api.controller

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream

data class CardArtResponse(
        val imgURL: String,
)

@RestController
@RequestMapping("/api/v1/cards")
class CardArtController(
        private val artService: CardArtService,
) {
    companion object {
        private const val MAX_BYTES = 1_048_576
        private const val MAX_DIMENSION = 1024

        private val PNG_SIGNATURE = byteArrayOf(
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
        )
    }

    @PutMapping(
            "/{cardId}/art",
            consumes = ["image/png"],
            produces = ["application/json"],
    )
    fun uploadArt(
            @PathVariable cardId: Int,
            authentication: JwtAuthenticationToken,
            request: HttpServletRequest,
    ): CardArtResponse {
        validateCardId(cardId)

        if (request.contentLengthLong > MAX_BYTES) {
            throw ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Drawing must be no larger than 1 MiB",
            )
        }


        val uploaded = request.inputStream.readNBytes(MAX_BYTES + 1)

        if (uploaded.size > MAX_BYTES) {
            throw ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Drawing must be no larger than 1 MiB",
            )
        }

        val png = validateAndNormalizePng(uploaded)

        return artService.save(
                cardId = cardId,
                googleSubject = authentication.token.subject,
                png = png,
        )
    }

    @GetMapping(
            "/{cardId}/art",
            produces = ["image/png"],
    )
    fun downloadArt(
            @PathVariable cardId: Int,
    ): ResponseEntity<ByteArray> {
        validateCardId(cardId)

        val png = artService.load(cardId)

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .contentLength(png.size.toLong())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(png)
    }

    private fun validateCardId(cardId: Int) {
        if (cardId <= 0) {
            badRequest("Card ID must be positive")
        }
    }

    private fun validateAndNormalizePng(bytes: ByteArray): ByteArray {
        if (
                bytes.size < PNG_SIGNATURE.size ||
                !bytes.copyOfRange(0, PNG_SIGNATURE.size)
                        .contentEquals(PNG_SIGNATURE)
        ) {
            badRequest("Drawing must be a valid PNG")
        }

        try {
            MemoryCacheImageInputStream(
                    ByteArrayInputStream(bytes)
            ).use { input ->
                val readers = ImageIO.getImageReadersByFormatName("png")

                if (!readers.hasNext()) {
                    throw IllegalStateException("PNG reader unavailable")
                }

                val reader = readers.next()

                try {
                    reader.setInput(input, true, true)

                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)

                    if (
                            width !in 1..MAX_DIMENSION ||
                            height !in 1..MAX_DIMENSION
                    ) {
                        badRequest(
                                "Drawing must be 1024 × 1024 pixels or smaller"
                        )
                    }

                    val image = reader.read(0)
                            ?: badRequest("Drawing could not be decoded")

                    val output = ByteArrayOutputStream()

                    if (!ImageIO.write(image, "png", output)) {
                        throw IllegalStateException("PNG writer unavailable")
                    }

                    val normalized = output.toByteArray()

                    if (normalized.size > MAX_BYTES) {
                        throw ResponseStatusException(
                                HttpStatus.PAYLOAD_TOO_LARGE,
                                "Saved drawing must be no larger than 1 MiB",
                        )
                    }

                    return normalized
                } finally {
                    reader.dispose()
                }
            }
        } catch (error: IOException) {
            throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Drawing could not be read as a PNG",
                    error,
            )
        }
    }

    private fun badRequest(message: String): Nothing {
        throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message,
        )
    }
}

@Service
class CardArtService(
        private val jdbc: JdbcTemplate,
) {
    @Transactional
    fun save(
            cardId: Int,
            googleSubject: String,
            png: ByteArray,
    ): CardArtResponse {
        val ownedCard = jdbc.query(
                """
            SELECT c.id
            FROM public."Card" c
            JOIN public."User" u ON u.id = c.author
            WHERE c.id = ? AND u.google_subject = ?
            FOR UPDATE OF c
            """.trimIndent(),
                { row, _ -> row.getInt("id") },
                cardId,
                googleSubject,
        ).firstOrNull()

        if (ownedCard == null) {
            throw ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Card not found",
            )
        }

        jdbc.update(
                """
            INSERT INTO public.card_art (card_id, png_bytes)
            VALUES (?, ?)
            ON CONFLICT (card_id)
            DO UPDATE SET png_bytes = EXCLUDED.png_bytes
            """.trimIndent(),
                cardId,
                png,
        )

        val imagePath = "/api/v1/cards/$cardId/art"

        jdbc.update(
                """
            UPDATE public."Card"
            SET imgurl = ?
            WHERE id = ?
            """.trimIndent(),
                imagePath,
                cardId,
        )

        return CardArtResponse(imgURL = imagePath)
    }

    @Transactional(readOnly = true)
    fun load(cardId: Int): ByteArray {
        val images = jdbc.query(
                """
            SELECT a.png_bytes
            FROM public.card_art a
            JOIN public."Card" c ON c.id = a.card_id
            WHERE a.card_id = ?
            """.trimIndent(),
                { row, _ -> row.getBytes("png_bytes") },
                cardId,
        )

        return images.firstOrNull()
                ?: throw ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "This card has no drawing",
                )
    }
}