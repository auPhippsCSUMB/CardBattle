package com.example.cardbattle.api.controller

import com.example.cardbattle.api.dataObjects.OwnershipCreateRequest
import com.example.cardbattle.api.dataObjects.OwnershipPatchRequest
import com.example.cardbattle.api.dataObjects.OwnershipResponse
import com.example.cardbattle.api.dataObjects.PageResponse
import com.example.cardbattle.api.dataObjects.CardType
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/inventory")
class InventoryController {

    @GetMapping
    fun listInventory(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) userId: Long?,
        @RequestParam(required = false) type: CardType?
    ): PageResponse<OwnershipResponse> {
        val ownerId = userId ?: 7L
        val sample = StubDataFactory.sampleOwnership(userId = ownerId)
        return StubDataFactory.page(
            listOf(
                if (type == null) sample else sample.copy(card = sample.card?.copy(type = type))
            ),
            page,
            size
        )
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createOwnership(@RequestBody request: OwnershipCreateRequest): OwnershipResponse =
        StubDataFactory.sampleOwnership(
            userId = request.userId,
            cardId = request.cardId
        ).copy(value = request.value)

    @PatchMapping("/{ownershipId}")
    fun updateOwnership(
        @PathVariable ownershipId: Long,
        @RequestBody request: OwnershipPatchRequest
    ): OwnershipResponse {
        val sample = StubDataFactory.sampleOwnership(ownershipId)
        return sample.copy(value = request.value ?: sample.value)
    }

    @DeleteMapping("/{ownershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteOwnership(@PathVariable ownershipId: Long) {
    }
}
