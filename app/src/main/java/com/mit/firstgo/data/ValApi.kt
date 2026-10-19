package com.mit.firstgo.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of the lightweight API-key validity probe. */
enum class KeyCheck { VALID, INVALID, NETWORK }

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
        start: Int = 0,
    ): Result<List<MatchSummary>> {
        // fetch account to get puuid for "me" detection
        val accRes = getAccount(name, tag, apiKey)
        val puuid = accRes.getOrNull()?.puuid

        var url = "$BASE/valorant/v4/matches/${enc(region)}/${enc(platform)}/${enc(name)}/${enc(tag)}?size=$size"
        if (start > 0) url += "&start=$start"
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

    // ---------- Extended endpoints ----------

    /** Key probe result: only 401/403 mean the key is rejected. Anything else (404, 5xx, net errors) is not a key problem. */
    suspend fun checkKey(apiKey: String?): KeyCheck = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrBlank()) return@withContext KeyCheck.NETWORK
        try {
            var code = probe("$BASE/valorant/v1/game-version", apiKey)
            if (code in 200..299) return@withContext KeyCheck.VALID
            if (code in 401..403) return@withContext KeyCheck.INVALID
            // fall back to a second route before deciding
            code = probe("$BASE/valorant/v3/leaderboard/pc/eu?size=1", apiKey)
            when {
                code in 200..299 -> KeyCheck.VALID
                code in 401..403 -> KeyCheck.INVALID
                code == 404 || code == 400 -> KeyCheck.VALID // key authenticated, route just has no data
                else -> KeyCheck.NETWORK
            }
        } catch (_: Exception) {
            KeyCheck.NETWORK
        }
    }

    private fun probe(url: String, apiKey: String): Int {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.setRequestProperty("Authorization", apiKey.trim())
        conn.setRequestProperty("User-Agent", "ValoTrak/1.0")
        val code = conn.responseCode
        conn.disconnect()
        return code
    }

    /** Leaderboard v3: top players for a region/platform. */
    suspend fun getLeaderboard(
        region: String,
        platform: String = "pc",
        page: Int = 1,
        size: Int = 100,
        name: String? = null,
        tag: String? = null,
        apiKey: String?,
    ): Result<List<LeaderboardEntry>> {
        var url = "$BASE/valorant/v3/leaderboard/${enc(region)}/${enc(platform)}?size=$size&page=$page"
        if (!name.isNullOrBlank() && !tag.isNullOrBlank()) {
            url += "&name=${enc(name)}&tag=${enc(tag)}"
        }
        val res = get(url, apiKey)
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        return try {
            val root = JSONObject(res.getOrThrow())
            val data = root.optJSONObject("data") ?: JSONObject()
            val players = data.optJSONArray("players") ?: JSONArray()
            val list = ArrayList<LeaderboardEntry>(players.length())
            for (i in 0 until players.length()) {
                val o = players.getJSONObject(i)
                val entryRank = o.optNullableInt("leaderboard_rank")
                val tierId = o.optNullableInt("tier")
                list.add(
                    LeaderboardEntry(
                        rank = entryRank ?: (i + 1),
                        name = o.optString("name"),
                        tag = o.optString("tag"),
                        tierId = tierId,
                        rr = o.optNullableInt("rr"),
                        wins = o.optNullableInt("wins"),
                        card = o.optString("card").optStringOrNull(),
                        puuid = o.optString("puuid").optStringOrNull(),
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Cache of skin level uuid -> name/icon, fetched once from valorant-api. */
    private val skinLevelCache = mutableMapOf<String, SkinLevel>()

    suspend fun loadSkinLevels(): Map<String, SkinLevel> {
        if (skinLevelCache.isNotEmpty()) return skinLevelCache
        try {
            val res = get("https://valorant-api.com/v1/weapons/skinlevels?language=en-US", null)
            if (res.isSuccess) {
                val root = JSONObject(res.getOrThrow())
                val data = root.optJSONArray("data") ?: JSONArray()
                for (i in 0 until data.length()) {
                    val o = data.getJSONObject(i)
                    val uuid = o.optString("uuid")
                    val name = o.optString("name").optStringOrNull() ?: continue
                    if (uuid.isNotBlank()) {
                        skinLevelCache[uuid] = SkinLevel(uuid, name, o.optString("displayIcon").optStringOrNull())
                    }
                }
            }
        } catch (_: Exception) { }
        return skinLevelCache
    }

    /** Current game/client version (e.g. "release-10.08-shipping-..."), needed for Riot store headers. */
    private var gameVersionCache: String? = null

    suspend fun getGameVersion(apiKey: String?): String? {
        gameVersionCache?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val res = get("$BASE/valorant/v1/game-version", apiKey)
                if (res.isSuccess) {
                    val data = JSONObject(res.getOrThrow()).optJSONObject("data") ?: JSONObject()
                    // Prefer an explicit riotClientVersion, else any "release-..." string.
                    var v = data.optString("riotClientVersion").takeIf { it.startsWith("release-") }
                    if (v == null) {
                        v = data.optString("version").takeIf { it.startsWith("release-") }
                    }
                    if (v == null) {
                        val keys = data.keys()
                        while (keys.hasNext()) {
                            val cand = data.optString(keys.next().toString())
                            if (cand.startsWith("release-")) { v = cand; break }
                        }
                    }
                    gameVersionCache = v
                }
                gameVersionCache
            } catch (_: Exception) {
                null
            }
        }
    }

    /** Global store offers (weapon skins currently being offered). Wallet is not available in public API v4. */
    suspend fun getStoreOffers(apiKey: String?): Result<List<StoreOffer>> {
        val res = get("$BASE/valorant/v1/store-offers", apiKey)
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        return try {
            val root = JSONObject(res.getOrThrow())
            val data = root.optJSONObject("data") ?: JSONObject()
            val offers = data.optJSONArray("Offers") ?: JSONArray()
            val skinType = "e5cdd41a-4398-4e02-8f81-c9206df6d9f9"
            val list = ArrayList<StoreOffer>(offers.length())
            for (i in 0 until offers.length()) {
                val o = offers.getJSONObject(i)
                if (!o.optBoolean("IsDirectPurchase", false)) continue
                val rewards = o.optJSONArray("Rewards") ?: JSONArray()
                if (rewards.length() == 0) continue
                val r = rewards.getJSONObject(0)
                val type = r.optString("ItemTypeID")
                val item = r.optString("ItemID")
                if (type != skinType || item.isNullOrBlank()) continue
                val costObj = o.optJSONObject("Cost")
                var cost: Int? = null
                if (costObj != null) {
                    // VP currency uuid first, fallback to any entry
                    val vpKey = "85ad13f7-3d1b-5128-9eb2-7cd8ee0b5741"
                    cost = costObj.optNullableInt(vpKey)
                    if (cost == null) {
                        val keys = costObj.keys()
                        while (keys.hasNext()) { cost = costObj.optNullableInt(keys.next().toString()); if (cost != null) break }
                    }
                }
                list.add(StoreOffer(item, cost))
            }
            Result.success(list.distinctBy { it.itemId })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private val agentsCache = mutableMapOf<String, AgentInfo>()

    /** Playable agents from valorant-api.com (no key needed). */
    suspend fun getAgents(): Result<List<AgentInfo>> {
        if (agentsCache.isNotEmpty()) return Result.success(agentsCache.values.toList())
        val res = get("https://valorant-api.com/v1/agents?isPlayableCharacter=true&language=en-US", null)
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        try {
            val root = JSONObject(res.getOrThrow())
            val data = root.optJSONArray("data") ?: JSONArray()
            for (i in 0 until data.length()) {
                val o = data.getJSONObject(i)
                val name = o.optString("displayName").optStringOrNull() ?: continue
                val role = o.optJSONObject("role")
                val abilities = ArrayList<AgentAbility>()
                val abArr = o.optJSONArray("abilities") ?: JSONArray()
                for (j in 0 until abArr.length()) {
                    val a = abArr.getJSONObject(j)
                    abilities.add(
                        AgentAbility(
                            name = a.optString("displayName").optStringOrNull() ?: continue,
                            icon = a.optString("displayIcon").optStringOrNull(),
                        )
                    )
                }
                agentsCache[name] = AgentInfo(
                    name = name,
                    description = o.optString("description").optStringOrNull(),
                    icon = o.optString("displayIcon").optStringOrNull(),
                    fullPortrait = o.optString("fullPortrait").optStringOrNull(),
                    roleName = role?.optString("displayName")?.optStringOrNull(),
                    roleIcon = role?.optString("displayIcon")?.optStringOrNull(),
                    abilities = abilities,
                )
            }
            return Result.success(agentsCache.values.sortedBy { it.name }.toList())
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}