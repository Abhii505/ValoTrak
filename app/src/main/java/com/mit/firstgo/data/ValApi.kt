package com.mit.firstgo.data

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ValApi {

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
                conn.setRequestProperty("User-Agent", "FirstGO/1.0")
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

    suspend fun getAccount(name: String, tag: String, apiKey: String?): Result<AccountInfo> {
        val url = "$BASE/valorant/v2/account/${enc(name)}/${enc(tag)}"
        val res = get(url, apiKey)
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        return try {
            val root = JSONObject(res.getOrThrow())
            val data = root.optJSONObject("data") ?: root
            val levelRaw = data.optNullableInt("account_level")
            val cardId = data.optString("card").optStringOrNull()
            Result.success(
                AccountInfo(
                    name = data.optString("name").ifBlank { name },
                    tag = data.optString("tag").ifBlank { tag },
                    level = levelRaw,
                    cardId = cardId,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMmr(
        region: String,
        name: String,
        tag: String,
        apiKey: String?,
    ): Result<MmrInfo> {
        val url = "$BASE/valorant/v2/mmr/${enc(region)}/${enc(name)}/${enc(tag)}"
        val res = get(url, apiKey)
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        return try {
            val root = JSONObject(res.getOrThrow())
            val data = root.optJSONObject("data") ?: root

            // HenrikDev v2 MMR payload stores the current values under "current_data".
            val current = data.optJSONObject("current_data")
                ?: data.optJSONObject("current")
            val tierObj = current?.optJSONObject("tier")
                ?: data.optJSONObject("tier")

            val tierName = when {
                current != null && current.has("currenttierpatched") && !current.isNull("currenttierpatched") ->
                    current.optString("currenttierpatched")
                tierObj != null && tierObj.optString("name").isNotBlank() ->
                    tierObj.optString("name")
                data.has("currenttierpatched") && !data.isNull("currenttierpatched") ->
                    data.optString("currenttierpatched")
                else -> null
            }?.optStringOrNull()

            val rr = current?.optNullableInt("ranking_in_tier")
                ?: current?.optNullableInt("rr")
                ?: data.optNullableInt("ranking_in_tier")

            val lastChange = current?.optNullableInt("mmr_change_to_last_game")
                ?: current?.optNullableInt("last_change")
                ?: data.optNullableInt("mmr_change_to_last_game")

            val elo = current?.optNullableInt("elo") ?: data.optNullableInt("elo")
            val rankImage = current?.optJSONObject("images")?.optString("large")?.optStringOrNull()
                ?: current?.optJSONObject("images")?.optString("small")?.optStringOrNull()

            val highestRank = data.optJSONObject("highest_rank")?.optString("patched_tier")?.optStringOrNull()

            Result.success(
                MmrInfo(
                    tier = tierName,
                    rr = rr,
                    elo = elo,
                    lastChange = lastChange,
                    rankImage = rankImage,
                    highestRank = highestRank,
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
                    tierObj != null && tierObj.optString("name").isNotBlank() ->
                        tierObj.optString("name")
                    o.has("tier") && !o.isNull("tier") && o.get("tier") is String ->
                        o.optString("tier")
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
                        lastChange = o.optNullableInt("last_change"),
                        elo = o.optNullableInt("elo"),
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}