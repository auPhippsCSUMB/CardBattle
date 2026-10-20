package com.example.cardbattle.api.dataObjects

data class PageResponse<T>(
    val content: List<T>,
    val page: PageMeta
)

data class PageMeta(
    val size: Int,
    val number: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class StatsResponse(
    val wins: Int,
    val losses: Int,
    val totalBattles: Int,
    val winRate: Double
)

enum class CardType {
    WARRIOR,
    BRAWLER,
    RANGER,
    MAGE,
    ROGUE
}
