package com.example.cardbattle.api

import com.example.cardbattle.api.controller.CardsController
import com.example.cardbattle.api.dataObjects.CardResponse
import com.example.cardbattle.api.dataObjects.CardType
import com.example.cardbattle.api.controller.DecksController
import com.example.cardbattle.api.dataObjects.DeckResponse
import com.example.cardbattle.api.dataObjects.DeckCardResponse
import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.full.primaryConstructor
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Keep the published card contract aligned with the implemented controller and response. */
class OpenApiContractTests {
    private val document: Map<String, Any> =
        Yaml().load(Files.readString(Path.of("../docs/openapi.yaml")))

    @Test
    fun `documented card operations match the implemented routes`() {
        val actual = CardsController::class.java.declaredMethods.flatMap { method ->
            listOfNotNull(
                method.getAnnotation(GetMapping::class.java)?.value?.first()?.let { it to "get" },
                method.getAnnotation(PostMapping::class.java)?.value?.first()?.let { it to "post" },
                method.getAnnotation(PatchMapping::class.java)?.value?.first()?.let { it to "patch" },
                method.getAnnotation(DeleteMapping::class.java)?.value?.first()?.let { it to "delete" },
            )
        }.toSet()
        val paths = document["paths"] as Map<*, *>
        val documented = paths.entries.filter { (path, _) ->
            path.toString().startsWith("/api/v1/cards") ||
                path.toString().startsWith("/api/v1/allCards") ||
                path == "/api/v1/randomCard"
        }.flatMap { (path, operations) ->
            (operations as Map<*, *>).keys
                .filter { it in setOf("get", "post", "patch", "delete", "put") }
                .map { path.toString() to it.toString() }
        }.toSet()
        assertEquals(actual, documented)
    }

    @Test
    fun `card schema matches the response and enum without obsolete fields`() {
        val components = document["components"] as Map<*, *>
        val schemas = components["schemas"] as Map<*, *>
        val card = schemas["Card"] as Map<*, *>
        val fields = (card["properties"] as Map<*, *>).keys
        assertEquals(CardResponse::class.primaryConstructor!!.parameters.map { it.name }.toSet(), fields)
        val cardType = schemas["CardType"] as Map<*, *>
        assertEquals(CardType.entries.map { it.name }, cardType["enum"])
        val patch = schemas["CardPatch"] as Map<*, *>
        assertFalse(patch.containsKey("minProperties"), "An empty PATCH is a valid no-op")
    }

    @Test
    fun `documented deck operations include only the implemented routes`() {
        val expected = setOf(
            "/api/v1/decks" to "post",
            "/api/v1/decks/{deckId}" to "get",
            "/api/v1/decks/{deckId}" to "delete",
            "/api/v1/decks/{deckId}/cards" to "post",
            "/api/v1/decks/{deckId}/cards/{cardInDeckId}" to "patch",
            "/api/v1/decks/{deckId}/cards/{cardInDeckId}" to "delete",
        )
        val paths = document["paths"] as Map<*, *>
        val documented = paths.entries.filter { (path, _) ->
            path.toString().startsWith("/api/v1/decks")
        }.flatMap { (path, operations) ->
            (operations as Map<*, *>).keys
                .filter { it in setOf("get", "post", "patch", "delete", "put") }
                .map { path.toString() to it.toString() }
        }.toSet()
        assertEquals(expected, documented)

        // The remaining controller methods intentionally stay TODOs for later batches.
        val controller = DecksController::class.java
        assertEquals(Int::class.javaPrimitiveType, controller.declaredMethods.single { it.name == "getDeck" }.parameterTypes[0])
        val schemas = (document["components"] as Map<*, *>)["schemas"] as Map<*, *>
        val deck = schemas["Deck"] as Map<*, *>
        assertEquals(
            DeckResponse::class.primaryConstructor!!.parameters.map { it.name }.toSet(),
            (deck["properties"] as Map<*, *>).keys,
        )
        val entry = schemas["DeckCard"] as Map<*, *>
        assertEquals(
            DeckCardResponse::class.primaryConstructor!!.parameters.map { it.name }.toSet(),
            (entry["properties"] as Map<*, *>).keys,
        )
        val patch = schemas["DeckCardPatch"] as Map<*, *>
        assertFalse(patch.containsKey("minProperties"), "Empty deck-card PATCH is a no-op")
    }
}
