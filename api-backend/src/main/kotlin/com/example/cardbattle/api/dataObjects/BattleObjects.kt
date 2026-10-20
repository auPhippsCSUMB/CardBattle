package com.example.cardbattle.api.dataObjects

data class BattleResponse(
    val id: Long,
    val winningUser: Long,
    val losingUser: Long,
    val winningCard: Long?,
    val losingCard: Long?,
    val occurredAt: String? = null
)

data class BattleCreateRequest(
    val winningUser: Long,
    val losingUser: Long,
    val winningCard: Long,
    val losingCard: Long
)
