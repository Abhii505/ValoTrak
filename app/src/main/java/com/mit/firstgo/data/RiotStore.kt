package com.mit.firstgo.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Phases of the Riot login state machine. */
enum class RiotPhase { NEED_CREDS, MFA, READY, FAILED }

data class RiotState(
    val phase: RiotPhase,
    val email: String? = null,
    val message: String? = null,
)

data class RiotShopItem(
    val uuid: String,
    val skin: SkinLevel?,   // null only if name map is missing that skin
    val cost: Int?,
)

data class RiotShop(
    val items: List<RiotShopItem>,
    val refreshInSec: Int?,
)

/**
 * Talks to Riot's real authorization + in-game storefront endpoints:
 *   1. PUT auth.riotgames.com/api/v1/authorize (client_id play-val)
 *   2. (optionally) login form -> multifactor
 *   3. Entitlements token from entitlements-api.prod.riotgames.com
 *   4. GET pd.<region>.a.pvp.net/store/v3/storefront/
 *
 * Tokens & cookies: Riot sets its own cookies on the app-wide CookieManager —
 * they are process-local, never written to server or to permanent storage.
 */
object RiotStore {

    private const val AUTH = "https://auth.riotgames.com/api/v1/authorize"

    private const val AUTH_BODY = """{"client_id":"play-val","redirect_uri":"https://playvalorant.com/opt_in","response_type":"token:id_token","scope":"account openid"}"""

    private const val PLATFORM =
        "ewogICJwbGF0Zm9ybVR5cGUiOiAiUEMiLAogICJwbGF0Zm9ybU9TIjogIldpbmRvd3MiLAogICJwbGF0Zm9ybU9TVmVyc2lvbiI6ICIxMC4wLjE5MDQyLjEuMjU2LjY0Yml0IiwKICAicGxhdGZvcm1DaGlwc2V0IjogIlVua25ub3ciCn0="

    init {
        // One shared cookie jar (Riot sets tdid/csrf/ss id cookies on /authorize).
        if (CookieHandler.getDefault() == null) {
            val cm = CookieManager()
            cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL)
            CookieHandler.setDefault(cm)
        }
    }

    private var accessToken: String? = null
    private var entitlementToken: String? = null

    fun isReady(): Boolean = accessToken != null && entitlementToken != null
    fun reset() { accessToken = null; entitlementToken = null }

    /** Try the silent path: if Riot cookies from an earlier login are still alive, we get logged in with no password. */
    suspend fun authorize(): RiotState = withContext(Dispatchers.IO) {
        val (code, body) = request(AUTH, "PUT", AUTH_BODY, emptyMap())
            ?: return@withContext RiotState(RiotPhase.FAILED, message = "Network error talking to Riot")
        if (code !in 200..299) {
            return@withContext RiotState(RiotPhase.FAILED, message = "Riot auth HTTP $code")
        }
        finalize(body)
    }

    suspend fun login(username: String, password: String): RiotState = withContext(Dispatchers.IO) {
        val bodyJson = JSONObject()
            .put("type", "auth")
            .put("username", username)
            .put("password", password)
            .put("remember", true)
            .put("language", "en_US")
            .toString()
        val (code, body) = request(AUTH, "PUT", bodyJson, emptyMap())
            ?: return@withContext RiotState(RiotPhase.FAILED, message = "Network error talking to Riot")
        if (code !in 200..299) {
            return@withContext RiotState(RiotPhase.FAILED, message = "Riot auth HTTP $code (check credentials)")
        }
        finalize(body)
    }

    suspend fun submitMfa(code: String): RiotState = withContext(Dispatchers.IO) {
        val bodyJson = JSONObject()
            .put("type", "multifactor")
            .put("code", code)
            .put("rememberDevice", true)
            .toString()
        val (code2, body) = request(AUTH, "PUT", bodyJson, emptyMap())
            ?: return@withContext RiotState(RiotPhase.FAILED, message = "Network error talking to Riot")
        if (code2 !in 200..299) {
            return@withContext RiotState(RiotPhase.FAILED, message = "Riot auth HTTP $code2 (wrong code?)")
        }
        finalize(body)
    }

    /** Interprets the authorize response; completes token + entitlement for type == "response". */
    private suspend fun finalize(body: String): RiotState {
        return try {
            val root = JSONObject(body)
            when (root.optString("type")) {
                "response" -> {
                    val params = root.getJSONObject("response").optJSONObject("parameters")
                    val uri = params?.optString("uri").orEmpty()
                    val m = Regex("access_token=([^&]+)").find(uri)
                        ?: return RiotState(RiotPhase.FAILED, message = "No access token in Riot response")
                    accessToken = java.net.URLDecoder.decode(m.groupValues[1], "UTF-8")
                    val ok = fetchEntitlement()
                    if (ok) RiotState(RiotPhase.READY)
                    else RiotState(RiotPhase.FAILED, message = "Could not fetch entitlements token")
                }
                "multifactor" -> {
                    val mf = root.optJSONObject("multifactor")
                    RiotState(RiotPhase.MFA, email = mf?.optString("email")?.takeIf { it.isNotBlank() })
                }
                "auth" -> RiotState(RiotPhase.NEED_CREDS)
                "error" -> RiotState(RiotPhase.FAILED, message = root.optString("error").ifBlank { "Riot login failed" })
                else -> RiotState(RiotPhase.FAILED, message = body.take(240))
            }
        } catch (t: Throwable) {
            RiotState(RiotPhase.FAILED, message = t.message ?: "Login parse error")
        }
    }

    private suspend fun fetchEntitlement(): Boolean = withContext(Dispatchers.IO) {
        val tk = accessToken ?: return@withContext false
        // Current endpoint: POST with the access token as a Bearer header.
        val first = request(
            "https://entitlements.auth.riotgames.com/api/token/v1",
            "POST", "{}",
            mapOf(
                "Authorization" to "Bearer $tk",
                "Content-Type" to "application/json",
                "Accept" to "application/json",
            ),
        )
        val (code, body) = if (first != null && first.first in 200..299) first else {
            // Legacy fallback: GET with the access token as a query parameter.
            val legacy = request(
                "https://entitlements.auth.riotgames.com/api/token/v1?access_token=$tk",
                "GET", null, emptyMap(),
            ) ?: return@withContext false
            legacy
        }
        if (code in 200..299) {
            entitlementToken = JSONObject(body).optString("entitlements_token").takeIf { it.isNotBlank() }
            entitlementToken != null
        } else false
    }

    /**
     * Loads the personal daily shop. Requires a completed login (READY).
     * Also fetches the price list (/store/v3/offers) and maps uuids to skins
     * via ValApi.loadSkinLevels().
     */
    suspend fun loadShop(regionCode: String, apiKey: String?): Result<RiotShop> = withContext(Dispatchers.IO) {
        val tk = accessToken
        val ent = entitlementToken
        if (tk == null || ent == null) return@withContext Result.failure(Exception("Not logged in to Riot"))

        val version = ValApi.getGameVersion(apiKey)
        val host = "pd.${regionCode}.a.pvp.net"
        val headers = mapOf(
            "X-Riot-ClientVersion" to (version ?: "release-00.00.0"),
            "X-Riot-ClientPlatform" to PLATFORM,
            "X-Riot-Entitlements-JWT" to ent,
            "Authorization" to "Bearer $tk",
        )

        val (shopCode, shopBody) = request("https://$host/store/v3/storefront/", "GET", null, headers)
            ?: return@withContext Result.failure(Exception("Network error loading storefront"))
        if (shopCode !in 200..299) {
            return@withContext Result.failure(Exception("Storefront HTTP $shopCode"))
        }

        val offersResult = request("https://$host/store/v3/offers/", "GET", null, headers)
        val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        val costMap = HashMap<String, Int>()
        if (offersResult != null && offersResult.first in 200..299) {
            val offersBody = offersResult.second
            try {
                val oRoot = JSONObject(offersBody).optJSONObject("data") ?: JSONObject()
                val list = oRoot.optJSONArray("Offers") ?: JSONArray()
                for (i in 0 until list.length()) {
                    val o = list.getJSONObject(i)
                    val id = o.optString("OfferID")
                    val discountPrice = o.optJSONObject("DiscountedPrice")
                    var price: Int? = null
                    if (discountPrice != null) {
                        val keys = discountPrice.keys()
                        while (keys.hasNext()) {
                            val k = keys.next().toString()
                            price = discountPrice.optNullableInt_(k)
                            if (price != null) break
                        }
                    }
                    if (price == null) {
                        val costObj = o.optJSONObject("Cost")
                        if (costObj != null) {
                            val keys = costObj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next().toString()
                                price = costObj.optNullableInt_(k)
                                if (price != null) break
                            }
                        }
                    }
                    if (id.isNotBlank()) costMap[id] = price ?: 0
                }
            } catch (_: Exception) { }
        }

        return@withContext try {
            val root = JSONObject(shopBody)
            val data = root.getJSONObject("data")
            val sis = data.getJSONObject("SingleItemStore")
            val uuidArr = sis.optJSONArray("SingleItemOffers") ?: JSONArray()
            val remaining = sis.optInt("SingleItemOffersRemainingDurationInSeconds", -1)
            val skins = ValApi.loadSkinLevels()
            val items = ArrayList<RiotShopItem>(uuidArr.length())
            for (i in 0 until uuidArr.length()) {
                val u = uuidArr.optString(i)
                if (!uuid.matches(u)) continue
                items.add(RiotShopItem(u, skins[u], costMap[u]?.takeIf { it in 1..Int.MAX_VALUE && it != 0 }))
            }
            Result.success(
                RiotShop(
                    items = items,
                    refreshInSec = if (remaining > 0) remaining else null,
                )
            )
        } catch (t: Throwable) {
            Result.failure(Exception("Storefront parse error: ${t.message}"))
        }
    }

    // ---------- plumbing ----------

    private suspend fun request(
        url: String,
        method: String,
        bodyJson: String?,
        headers: Map<String, String>,
    ): Pair<Int, String>? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "ValoTrak/1.0")
            if (bodyJson != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
            }
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (bodyJson != null) {
                conn.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            code to text
        } catch (t: Throwable) {
            null
        }
    }
}

// Tiny helper so the code above can reuse ValApi's nullable-int parsing style.
private fun JSONObject.optNullableInt_(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null
