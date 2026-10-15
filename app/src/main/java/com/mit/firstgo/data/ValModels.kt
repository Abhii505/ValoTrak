package com.mit.firstgo.data

data class AccountInfo(
    val name: String,
    val tag: String,
    val level: Int?,
    val cardId: String?,
)

data class MmrInfo(
    val tier: String?,
    val rr: Int?,
    val elo: Int?,
    val lastChange: Int?,
    val rankImage: String?,
    val highestRank: String?,
)

data class HistoryEntry(
    val date: String?,
    val map: String?,
    val mapId: String?,
    val tier: String?,
    val rr: Int?,
    val lastChange: Int?,
    val elo: Int?,
)