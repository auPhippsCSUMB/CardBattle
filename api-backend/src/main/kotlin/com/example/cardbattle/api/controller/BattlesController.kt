package com.example.cardbattle.api.controller

import com.example.cardbattle.api.dataObjects.BattleCreateRequest
import com.example.cardbattle.api.dataObjects.BattleResponse
import com.example.cardbattle.api.dataObjects.PageResponse
import com.example.cardbattle.api.battle.BattleService
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
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
class BattlesController(private val battles: BattleService) {

    @GetMapping
    fun listBattles(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) userId: Long?
    ): PageResponse<BattleResponse> = battles.listBattles(page, size, userId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createBattle(@RequestBody request: BattleCreateRequest, authentication: JwtAuthenticationToken): BattleResponse =
        battles.createBattle(request, authentication.token.subject)

    @GetMapping("/{battleId}")
    fun getBattle(@PathVariable battleId: Long): BattleResponse = battles.getBattle(battleId)
}
