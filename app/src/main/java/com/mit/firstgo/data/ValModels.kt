package com.mit.firstgo.data

data class AccountInfo(
    val name: String,
    val tag: String,
    val level: Int?,
    val cardId: String?,
    val titleId: String?,
    val puuid: String?,
    val region: String?,
)

data class TierInfo(
    val id: Int?,
    val name: String?,
)

data class SeasonalRank(
    val seasonShort: String?,
    val seasonName: String?,
    val wins: Int,
    val games: Int,
    val endTier: String?,
    val actWins: List<String>,
)

data class MmrInfo(
    val tier: String?,
    val tierId: Int?,
    val rr: Int?,
    val elo: Int?,
    val lastChange: Int?,
    val rankImage: String?,
    val highestRank: String?,
    val peakSeason: String?,
    val seasonal: List<SeasonalRank>,
)

data class HistoryEntry(
    val date: String?,
    val map: String?,
    val mapId: String?,
    val tier: String?,
    val rr: Int?,
    val lastChange: Int?,
    val elo: Int?,
    val refundedRr: Int?,
    val derankProtected: Boolean?,
)

data class PlayerMatchStats(
    val puuid: String?,
    val name: String?,
    val tag: String?,
    val teamId: String?,
    val agent: String?,
    val agentIcon: String?,
    val score: Int?,
    val kills: Int?,
    val deaths: Int?,
    val assists: Int?,
    val headshots: Int?,
    val bodyshots: Int?,
    val legshots: Int?,
    val damage: Int?,
)

data class TeamScore(
    val teamId: String?,
    val won: Boolean?,
    val roundsWon: Int?,
    val roundsLost: Int?,
)

data class MatchSummary(
    val matchId: String,
    val map: String?,
    val mapId: String?,
    val mode: String?,
    val gameStart: Long?,
    val gameStartPatched: String?,
    val gameLengthSec: Int?,
    val roundsPlayed: Int?,
    val region: String?,
    val players: List<PlayerMatchStats>,
    val teams: List<TeamScore>,
    val playerPuuid: String?,
) {
    val me: PlayerMatchStats? get() = players.firstOrNull { it.puuid == playerPuuid }
    val myTeam: TeamScore? get() = teams.firstOrNull { it.teamId == me?.teamId }
    val result: String get() {
        val w = myTeam?.won
        return when (w) { true -> "WIN"; false -> "LOSS"; else -> "DRAW" }
    }
}

data class LeaderboardEntry(
    val rank: Int,
    val name: String,
    val tag: String,
    val tierId: Int?,
    val rr: Int?,
    val wins: Int?,
    val card: String?,
    val puuid: String?,
)

data class SkinLevel(
    val uuid: String,
    val name: String,
    val icon: String?,
)

data class StoreOffer(
    val itemId: String,
    val cost: Int?,
)

data class AgentAbility(
    val name: String,
    val icon: String?,
)

data class AgentInfo(
    val name: String,
    val description: String?,
    val icon: String?,
    val fullPortrait: String?,
    val roleName: String?,
    val roleIcon: String?,
    val abilities: List<AgentAbility>,
)