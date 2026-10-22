package com.example.cardbattle.api

import com.example.cardbattle.api.controller.CardsController
import com.example.cardbattle.api.dataObjects.CardResponse
import com.example.cardbattle.api.dataObjects.CardType
import com.example.cardbattle.api.controller.DecksController
import com.example.cardbattle.api.controller.InventoryController
import com.example.cardbattle.api.controller.UsersController
import com.example.cardbattle.api.user.UserController
import com.example.cardbattle.api.dataObjects.UserResponse
import com.example.cardbattle.api.dataObjects.OwnershipResponse
import com.example.cardbattle.api.dataObjects.DeckResponse
import com.example.cardbattle.api.dataObjects.DeckCardResponse
import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.full.primaryConstructor
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Keep the published contract aligned with implemented routes and response fields. */
class OpenApiContractTests {
    private val document: Map<String, Any> =
        Yaml().load(Files.readString(Path.of("../docs/openapi.yaml")))

    @Test
    fun `documented user operations match both controllers and expose only supported fields`() {
        val expected = listOf(UsersController::class.java, UserController::class.java).flatMap { controller ->
            val base = controller.getAnnotation(RequestMapping::class.java).value.single()
            controller.declaredMethods.flatMap { method ->
                listOfNotNull(
                    method.getAnnotation(GetMapping::class.java)?.let { base + it.value.firstOrNull().orEmpty() to "get" },
                    method.getAnnotation(PutMapping::class.java)?.let { base + it.value.firstOrNull().orEmpty() to "put" },
                    method.getAnnotation(PatchMapping::class.java)?.let { base + it.value.firstOrNull().orEmpty() to "patch" },
                    method.getAnnotation(DeleteMapping::class.java)?.let { base + it.value.firstOrNull().orEmpty() to "delete" },
                )
            }
        }.toSet()
        val paths = document["paths"] as Map<*, *>
        val actual = paths.entries.filter { it.key.toString().startsWith("/api/v1/users") }.flatMap { (path, operations) ->
            (operations as Map<*, *>).keys.filter { it in setOf("get", "put", "patch", "delete") }
                .map { path.toString() to it.toString() }
        }.toSet()
        assertEquals(expected, actual)
        val schemas = (document["components"] as Map<*, *>)["schemas"] as Map<*, *>
        assertEquals(UserResponse::class.primaryConstructor!!.parameters.map { it.name }.toSet(),
            ((schemas["User"] as Map<*, *>)["properties"] as Map<*, *>).keys)
        val patch = schemas["UserAdminPatch"] as Map<*, *>
        assertEquals(setOf("isAdmin"), (patch["properties"] as Map<*, *>).keys)
        assertFalse(patch.containsKey("minProperties"))
    }

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
    fun `documented inventory operations match the implemented controller and embed card details`() {
        val paths = document["paths"] as Map<*, *>
        val controller = InventoryController::class.java
        val basePath = controller.getAnnotation(RequestMapping::class.java).value.single()
        val expected = controller.declaredMethods.flatMap { method ->
            listOfNotNull(
                method.getAnnotation(GetMapping::class.java)?.let { basePath + it.value.firstOrNull().orEmpty() to "get" },
                method.getAnnotation(PostMapping::class.java)?.let { basePath + it.value.firstOrNull().orEmpty() to "post" },
                method.getAnnotation(PatchMapping::class.java)?.let { basePath + it.value.firstOrNull().orEmpty() to "patch" },
                method.getAnnotation(DeleteMapping::class.java)?.let { basePath + it.value.firstOrNull().orEmpty() to "delete" },
            )
        }.toSet()
        val documented = paths.entries.filter { (path, _) -> path.toString().startsWith(basePath) }
            .flatMap { (path, operations) ->
                (operations as Map<*, *>).keys
                    .filter { it in setOf("get", "post", "patch", "delete", "put") }
                    .map { path.toString() to it.toString() }
            }.toSet()
        assertEquals(expected, documented)
        val schemas = (document["components"] as Map<*, *>)["schemas"] as Map<*, *>
        val ownership = schemas["Ownership"] as Map<*, *>
        val fields = ownership["properties"] as Map<*, *>
        assertEquals(OwnershipResponse::class.primaryConstructor!!.parameters.map { it.name }.toSet(), fields.keys)
        assertEquals("#/components/schemas/Card", (fields["card"] as Map<*, *>)["\$ref"])
        assertEquals(true, (fields["value"] as Map<*, *>)["nullable"])
        val patch = schemas["OwnershipPatch"] as Map<*, *>
        assertFalse(patch.containsKey("minProperties"), "Empty inventory PATCH is a no-op")
        assertEquals(setOf("value"), (patch["properties"] as Map<*, *>).keys)
    }

    @Test
    fun `documented deck operations include only the implemented routes`() {
        val controller = DecksController::class.java
        val basePath = controller.getAnnotation(RequestMapping::class.java).value.single()
        val expected = controller.declaredMethods.flatMap { method ->
            listOfNotNull(
                method.getAnnotation(GetMapping::class.java)?.let { basePath + it.value.firstOrNull().orEmpty() to "get" },
                method.getAnnotation(PostMapping::class.java)?.let { basePath + it.value.firstOrNull().orEmpty() to "post" },
                method.getAnnotation(PatchMapping::class.java)?.let { basePath + it.value.firstOrNull().orEmpty() to "patch" },
                method.getAnnotation(DeleteMapping::class.java)?.let { basePath + it.value.firstOrNull().orEmpty() to "delete" },
            )
        }.toSet()
        val paths = document["paths"] as Map<*, *>
        val documented = paths.entries.filter { (path, _) ->
            path.toString().startsWith("/api/v1/decks")
        }.flatMap { (path, operations) ->
            (operations as Map<*, *>).keys
                .filter { it in setOf("get", "post", "patch", "delete", "put") }
                .map { path.toString() to it.toString() }
        }.toSet()
        assertEquals(expected, documented)

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
        val nestedCard = (entry["properties"] as Map<*, *>)["card"] as Map<*, *>
        assertEquals("#/components/schemas/Card", nestedCard["\$ref"])
        val patch = schemas["DeckCardPatch"] as Map<*, *>
        assertFalse(patch.containsKey("minProperties"), "Empty deck-card PATCH is a no-op")
        val deckPatch = schemas["DeckPatch"] as Map<*, *>
        assertFalse(deckPatch.containsKey("minProperties"), "Empty deck PATCH is a no-op")
        assertEquals(setOf("name"), (deckPatch["properties"] as Map<*, *>).keys)
    }
}
