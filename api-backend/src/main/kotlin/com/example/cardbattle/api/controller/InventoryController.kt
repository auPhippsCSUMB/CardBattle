package com.example.cardbattle.api.controller

import com.example.cardbattle.api.dataObjects.*
import com.example.cardbattle.api.inventory.InventoryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.*

/**
 * Manages owned card copies belonging to the signed-in user.
 */
@Tag(
        name = "Inventory",
        description = "Manage owned card copies, separately from their card designs.",
)
@ApiResponses(
        value = [
            ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired bearer token",
                    content = [Content()],
            ),
        ]
)
@RestController
@RequestMapping("/api/v1/inventory")
class InventoryController(
        private val inventoryService: InventoryService,
) {

    @Operation(
            summary = "List your owned card copies",
            description = "Returns a paginated inventory with embedded card details. " +
                    "Ordering is inventory ID ascending. Other users' inventory " +
                    "is not exposed through the userId filter. " +
                    "No matching copies returns an empty page.",
    )
    @ApiResponse(
            responseCode = "200",
            description = "Page of owned copies returned",
    )
    @GetMapping
    fun listInventory(
            @Parameter(description = "Zero-based page number")
            @RequestParam(defaultValue = "0")
            page: Int,

            @Parameter(description = "Page size, from 1 through 100")
            @RequestParam(defaultValue = "20")
            size: Int,

            @RequestParam(required = false)
            userId: Int?,

            @Parameter(description = "Optional card-class filter")
            @RequestParam(required = false)
            type: CardType?,

            @Parameter(hidden = true)
            authentication: JwtAuthenticationToken,
    ): PageResponse<OwnershipResponse> =
            inventoryService.listInventory(
                    authentication.token.subject,
                    page,
                    size,
                    userId,
                    type,
            )

    @Operation(
            summary = "Add a card copy to your inventory",
            description = "request.cardId is the underlying Card.id. " +
                    "request.userId must match the signed-in user. " +
                    "A nonnegative value is required. " +
                    "The returned id is User_owns_card.id, which is used " +
                    "when adding this copy to a deck.",
    )
    @ApiResponse(
            responseCode = "201",
            description = "Owned copy created",
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createOwnership(
            @RequestBody request: OwnershipCreateRequest,
            @Parameter(hidden = true)
            authentication: JwtAuthenticationToken,
    ): OwnershipResponse =
            inventoryService.createOwnership(
                    request,
                    authentication.token.subject,
            )

    @Operation(
            summary = "Update an owned copy's value",
            description = "ownershipId is User_owns_card.id. " +
                    "Only value can change, and it must be nonnegative. " +
                    "Omitting value leaves it unchanged; explicit null is rejected. " +
                    "The copy must belong to the signed-in user.",
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated owned copy returned",
    )
    @PatchMapping("/{ownershipId}")
    fun updateOwnership(
            @PathVariable ownershipId: Int,
            @RequestBody request: OwnershipPatchRequest,
            @Parameter(hidden = true)
            authentication: JwtAuthenticationToken,
    ): OwnershipResponse =
            inventoryService.updateOwnership(
                    ownershipId,
                    request,
                    authentication.token.subject,
            )

    @Operation(
            summary = "Remove an owned copy",
            description = "Deletes your inventory copy rather than the shared " +
                    "card design. Related memberships and battle references " +
                    "follow the configured database deletion rules. " +
                    "Blocking constraints return 409.",
    )
    @ApiResponse(
            responseCode = "204",
            description = "Owned copy removed",
            content = [Content()],
    )
    @DeleteMapping("/{ownershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteOwnership(
            @PathVariable ownershipId: Int,
            @Parameter(hidden = true)
            authentication: JwtAuthenticationToken,
    ) {
        inventoryService.deleteOwnership(
                ownershipId,
                authentication.token.subject,
        )
    }
}