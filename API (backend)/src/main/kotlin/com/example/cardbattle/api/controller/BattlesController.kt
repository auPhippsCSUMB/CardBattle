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
    ): PageResponse<BattleResponse> = TODO("Implement GET /api/v1/battles")

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createBattle(@RequestBody request: BattleCreateRequest): BattleResponse = TODO("Implement POST /api/v1/battles")

    @GetMapping("/{battleId}")
    fun getBattle(@PathVariable battleId: Long): BattleResponse = TODO("Implement GET /api/v1/battles/{battleId}")
}
