package com.example.cardbattle.api.controller

import com.example.cardbattle.api.dataObjects.BattleCreateRequest
import com.example.cardbattle.api.dataObjects.BattleResponse
import com.example.cardbattle.api.dataObjects.PageResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/battles")
class BattlesController {

    @GetMapping
    fun listBattles(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) userId: Long?
    ): PageResponse<BattleResponse> {
        val battle = StubDataFactory.sampleBattle().copy(
            winningUser = userId ?: StubDataFactory.sampleBattle().winningUser
        )
        return StubDataFactory.page(listOf(battle), page, size)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createBattle(@RequestBody request: BattleCreateRequest): BattleResponse = BattleResponse(
        id = 25L,
        winningUser = request.winningUser,
        losingUser = request.losingUser,
        winningCard = request.winningCard,
        losingCard = request.losingCard,
        occurredAt = "2026-10-06T18:45:00Z"
    )

    @GetMapping("/{battleId}")
    fun getBattle(@PathVariable battleId: Long): BattleResponse = StubDataFactory.sampleBattle(battleId)
}
