package com.mit.firstgo.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ValApi {

    private val seasonNameCache = mutableMapOf<String, String>()

    private suspend fun fetchSeasonNames() {
        if (seasonNameCache.isNotEmpty()) return
        try {
            val res = get("https://valorant-api.com/v1/seasons?language=en-US", null)
            if (res.isSuccess) {
                val root = org.json.JSONObject(res.getOrThrow())
                val data = root.optJSONArray("data") ?: org.json.JSONArray()
                for (i in 0 until data.length()) {
                    val o = data.getJSONObject(i)
                    val uuid = o.optString("uuid")
                    val display = o.optString("displayName").optStringOrNull()
                    if (!uuid.isNullOrBlank() && !display.isNullOrBlank()) {
                        seasonNameCache[uuid] = display
                    }
                }
            }
        } catch (_: Exception) { }
    }

    private const val BASE = "https://api.henrikdev.xyz"

    private fun enc(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private suspend fun get(url: String, apiKey: String?): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 20000
                conn.readTimeout = 20000
                if (!apiKey.isNullOrBlank()) {
                    conn.setRequestProperty("Authorization", apiKey.trim())
                }
                conn.setRequestProperty("User-Agent", "ValoTrak/1.0")
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (code in 200..299) {
                    Result.success(text)
                } else {
                    Result.failure(Exception("HTTP $code: ${text.take(200)}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun String.optStringOrNull(): String? = if (isNullOrBlank()) null else this

    private fun JSONObject.optTierName(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = get(key)
        return when (v) {
            is JSONObject -> v.optString("name").optStringOrNull()
            is String -> v.optStringOrNull()
            else -> v.toString().optStringOrNull()
        }
    }

    suspend fun getAccount(name: String, tag: String, apiKey: String?): Result<AccountInfo> {
        val url = "$BASE/valorant/v2/account/${enc(name)}/${enc(tag)}"
        val res = get(url, apiKey)
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        return try {
            val root = JSONObject(res.getOrThrow())
            val data = root.optJSONObject("data") ?: root
            val levelRaw = data.optNullableInt("account_level")
            val cardId = data.optString("card").optStringOrNull()
            val titleId = data.optString("title").optStringOrNull()
            val puuid = data.optString("puuid").optStringOrNull()
            val region = data.optString("region").optStringOrNull()
            Result.success(
                AccountInfo(
                    name = data.optString("name").ifBlank { name },
                    tag = data.optString("tag").ifBlank { tag },
                    level = levelRaw,
                    cardId = cardId,
                    titleId = titleId,
                    puuid = puuid,
                    region = region,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMmr(
        region: String,
        platform: String = "pc",
        name: String,
        tag: String,
        apiKey: String?,
    ): Result<MmrInfo> {
        val url = "$BASE/valorant/v3/mmr/${enc(region)}/${enc(platform)}/${enc(name)}/${enc(tag)}"
        val res = get(url, apiKey)
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        return try {
            val root = JSONObject(res.getOrThrow())
            val data = root.optJSONObject("data") ?: root

            val current = data.optJSONObject("current")
            val tierObj = current?.optJSONObject("tier")
            val tierName = tierObj?.optString("name")?.optStringOrNull()
                ?: current?.optString("currenttierpatched")?.optStringOrNull()
            val tierId = tierObj?.optNullableInt("id")
            val rr = current?.optNullableInt("rr")
                ?: current?.optNullableInt("ranking_in_tier")
            val lastChange = current?.optNullableInt("last_change")
                ?: current?.optNullableInt("mmr_change_to_last_game")
            val elo = current?.optNullableInt("elo")
            val rankImage = current?.optJSONObject("images")?.optString("large")?.optStringOrNull()
                ?: current?.optJSONObject("images")?.optString("small")?.optStringOrNull()

            val peak = data.optJSONObject("peak")
            val highestRank = peak?.optTierName("tier")
            val peakSeason = peak?.optJSONObject("season")?.optString("short")?.optStringOrNull()

            fetchSeasonNames()
            val seasonalArr = data.optJSONArray("seasonal") ?: JSONArray()
            val seasonal = ArrayList<SeasonalRank>(seasonalArr.length())
            for (i in 0 until seasonalArr.length()) {
                val o = seasonalArr.getJSONObject(i)
                val seasonObj = o.optJSONObject("season")
                val seasonId = seasonObj?.optString("id")?.optStringOrNull()
                val seasonShort = seasonObj?.optString("short")?.optStringOrNull()
                val seasonName = seasonId?.let { seasonNameCache[it] } ?: seasonShort
                val actWinsArr = o.optJSONArray("act_wins") ?: JSONArray()
                val actWins = ArrayList<String>(actWinsArr.length())
                for (j in 0 until actWinsArr.length()) {
                    val t = actWinsArr.opt(j)
                    val nameStr = when (t) {
                        is JSONObject -> t.optString("name")
                        else -> t?.toString()
                    }
                    if (!nameStr.isNullOrBlank()) actWins.add(nameStr)
                }
                seasonal.add(
                    SeasonalRank(
                        seasonShort = seasonShort,
                        seasonName = seasonName,
                        wins = o.optNullableInt("wins") ?: 0,
                        games = o.optNullableInt("games") ?: 0,
                        endTier = o.optTierName("end_tier"),
                        actWins = actWins,
                    )
                )
            }

            Result.success(
                MmrInfo(
                    tier = tierName,
                    tierId = tierId,
                    rr = rr,
                    elo = elo,
                    lastChange = lastChange,
                    rankImage = rankImage,
                    highestRank = highestRank,
                    peakSeason = peakSeason,
                    seasonal = seasonal,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMmrHistory(
        region: String,
        platform: String = "pc",
        name: String,
        tag: String,
        apiKey: String?,
    ): Result<List<HistoryEntry>> {
        val url =
            "$BASE/valorant/v2/mmr-history/${enc(region)}/${enc(platform)}/${enc(name)}/${enc(tag)}"
        val res = get(url, apiKey)
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        return try {
            val root = JSONObject(res.getOrThrow())
            val data = root.optJSONObject("data") ?: root
            val arr: JSONArray = data.optJSONArray("history") ?: JSONArray()
            val list = ArrayList<HistoryEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val tierObj = o.optJSONObject("tier")
                val tierName = when {
                    tierObj != null && tierObj.optString("name").isNotBlank() -> tierObj.optString("name")
                    o.has("tier") && !o.isNull("tier") && o.get("tier") is String -> o.optString("tier")
                    else -> null
                }?.optStringOrNull()
                val mapObj = o.optJSONObject("map")
                val mapName = mapObj?.optString("name")?.optStringOrNull()
                val mapId = mapObj?.optString("id")?.optStringOrNull()
                list.add(
                    HistoryEntry(
                        date = o.optString("date").optStringOrNull(),
                        map = mapName,
                        mapId = mapId,
                        tier = tierName,
                        rr = o.optNullableInt("rr"),
                        lastChange = o.optNullableInt("last_change") ?: o.optNullableInt("mmr_change_to_last_game"),
                        elo = o.optNullableInt("elo"),
                        refundedRr = o.optNullableInt("refunded_rr"),
                        derankProtected = if (o.has("was_derank_protected") && !o.isNull("was_derank_protected")) o.optBoolean("was_derank_protected") else null,
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parsePlayer(o: JSONObject): PlayerMatchStats {
        val stats = o.optJSONObject("stats")
        val agentObj = o.optJSONObject("agent")
        val agentName = agentObj?.optString("name")?.optStringOrNull()
            ?: o.optString("character").optStringOrNull()
            ?: o.optString("agent").optStringOrNull()
        val agentId = agentObj?.optString("id")?.optStringOrNull()
        val damageObj = stats?.optJSONObject("damage")
        val damageDealt = damageObj?.optNullableInt("dealt") ?: stats?.optNullableInt("damage")
        val agentIcon = agentId?.let { "https://media.valorant-api.com/agents/$it/displayicon.png" }
            ?: o.optString("agent_icon").optStringOrNull()
        return PlayerMatchStats(
            puuid = o.optString("puuid").optStringOrNull(),
            name = o.optString("name").optStringOrNull(),
            tag = o.optString("tag").optStringOrNull(),
            teamId = o.optString("team_id").optStringOrNull() ?: o.optString("team").optStringOrNull(),
            agent = agentName,
            agentIcon = agentIcon,
            score = stats?.optNullableInt("score"),
            kills = stats?.optNullableInt("kills"),
            deaths = stats?.optNullableInt("deaths"),
            assists = stats?.optNullableInt("assists"),
            headshots = stats?.optNullableInt("headshots"),
            bodyshots = stats?.optNullableInt("bodyshots"),
            legshots = stats?.optNullableInt("legshots"),
            damage = damageDealt,
        )
    }

    private fun parseMatch(o: JSONObject, playerPuuid: String?): MatchSummary {
        val meta = o.optJSONObject("metadata") ?: JSONObject()
        val mapObj = meta.optJSONObject("map")
        val mapName = mapObj?.optString("name")?.optStringOrNull() ?: meta.optString("map").optStringOrNull()
        val mapId = mapObj?.optString("id")?.optStringOrNull() ?: meta.optString("map_id").optStringOrNull()

        val playersArr = o.optJSONArray("players") ?: JSONArray()
        val players = ArrayList<PlayerMatchStats>(playersArr.length())
        for (i in 0 until playersArr.length()) {
            players.add(parsePlayer(playersArr.getJSONObject(i)))
        }

        val teamsArr = o.optJSONArray("teams") ?: JSONArray()
        val teams = ArrayList<TeamScore>(teamsArr.length())
        for (i in 0 until teamsArr.length()) {
            val t = teamsArr.getJSONObject(i)
            val rounds = t.optJSONObject("rounds")
            teams.add(
                TeamScore(
                    teamId = t.optString("team_id").optStringOrNull() ?: t.optString("team").optStringOrNull(),
                    won = if (t.has("won") && !t.isNull("won")) t.optBoolean("won") else null,
                    roundsWon = rounds?.optNullableInt("won") ?: t.optNullableInt("rounds_won"),
                    roundsLost = rounds?.optNullableInt("lost") ?: t.optNullableInt("rounds_lost"),
                )
            )
        }

        val queueObj = meta.optJSONObject("queue")
        val modeName = queueObj?.optString("name")?.optStringOrNull() ?: meta.optString("mode").optStringOrNull()

        val gameLengthMs = if (meta.has("game_length_in_ms") && !meta.isNull("game_length_in_ms")) meta.optLong("game_length_in_ms") else null
        val gameLengthSec = gameLengthMs?.let { (it / 1000).toInt() } ?: meta.optNullableInt("game_length")

        val roundsPlayed = o.optJSONArray("rounds")?.length() ?: meta.optNullableInt("rounds_played")

        return MatchSummary(
            matchId = meta.optString("match_id").optStringOrNull() ?: meta.optString("matchid").optStringOrNull() ?: "",
            map = mapName,
            mapId = mapId,
            mode = modeName,
            gameStart = null,
            gameStartPatched = meta.optString("started_at").optStringOrNull(),
            gameLengthSec = gameLengthSec,
            roundsPlayed = roundsPlayed,
            region = meta.optString("region").optStringOrNull(),
            players = players,
            teams = teams,
            playerPuuid = playerPuuid,
        )
    }

    suspend fun getMatches(
        region: String,
        platform: String = "pc",
        name: String,
        tag: String,
        size: Int = 10,
        apiKey: String?,
    ): Result<List<MatchSummary>> {
        // fetch account to get puuid for "me" detection
        val accRes = getAccount(name, tag, apiKey)
        val puuid = accRes.getOrNull()?.puuid

        val url = "$BASE/valorant/v4/matches/${enc(region)}/${enc(platform)}/${enc(name)}/${enc(tag)}?size=$size"
        val res = get(url, apiKey)
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        return try {
            val root = JSONObject(res.getOrThrow())
            val data = root.optJSONArray("data") ?: JSONArray()
            val list = ArrayList<MatchSummary>(data.length())
            for (i in 0 until data.length()) {
                list.add(parseMatch(data.getJSONObject(i), puuid))
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMatch(
        region: String,
        matchId: String,
        apiKey: String?,
        playerPuuid: String? = null,
    ): Result<MatchSummary> {
        val url = "$BASE/valorant/v4/match/${enc(region)}/${enc(matchId)}"
        val res = get(url, apiKey)
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        return try {
            val root = JSONObject(res.getOrThrow())
            val data = root.optJSONObject("data") ?: root
            Result.success(parseMatch(data, playerPuuid))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}