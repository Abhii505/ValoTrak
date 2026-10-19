package com.mit.firstgo.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mit.firstgo.data.AccountInfo
import com.mit.firstgo.data.AgentInfo
import com.mit.firstgo.data.HistoryEntry
import com.mit.firstgo.data.KeyCheck
import com.mit.firstgo.data.LeaderboardEntry
import com.mit.firstgo.data.MatchSummary
import com.mit.firstgo.data.MmrInfo
import com.mit.firstgo.data.PlayerMatchStats
import com.mit.firstgo.data.RiotPhase
import com.mit.firstgo.data.RiotShopItem
import com.mit.firstgo.data.RiotState
import com.mit.firstgo.data.RiotStore
import com.mit.firstgo.data.SkinLevel
import com.mit.firstgo.data.StoreOffer
import com.mit.firstgo.data.ValApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

private val ValorantRed = Color(0xFFFF4655)
private val ValorantDark = Color(0xFF0F1923)
private val ValorantDarker = Color(0xFF0A1220)
private val CardBg = Color(0xFF152232)
private val CardBgAlt = Color(0xFF1B2B3D)
private val TextPrimary = Color(0xFFF9F9F9)
private val TextSecondary = Color(0xFF8B9BB4)
private val WinGreen = Color(0xFF2ED573)
private val AccentBlue = Color(0xFF4CC3FF)
private val AccentYellow = Color(0xFFF6C243)
private val DividerColor = Color(0xFF24344A)

// ---------- Visual-FX toolkit (contained glow, gradient borders, ambient light) ----------

/** Tight, contained bloom behind a composable; radius capped to the object's half-size. */
private fun Modifier.glow(color: Color, alpha: Float = 0.35f): Modifier = this.drawBehind {
    if (size.width > 0f && size.height > 0f) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = min(size.width, size.height) * 0.62f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = alpha),
                    color.copy(alpha = alpha * 0.45f),
                    Color.Transparent,
                ),
                center = c, radius = r,
            ),
            radius = r, center = c,
        )
    }
}

/** Glow that hugs the silhouette (soft box-shadow). */
private fun Modifier.edgeGlow(
    color: Color,
    cornerRadius: Dp = 14.dp,
    spread: Dp = 3.dp,
    alpha: Float = 0.4f,
): Modifier = this.drawBehind {
    if (size.width > 0f && size.height > 0f) {
        val outline = RoundedCornerShape(cornerRadius).createOutline(size, layoutDirection, this)
        val w = spread.toPx()
        drawOutline(outline, color = color.copy(alpha = alpha * 0.20f), style = Stroke(width = w * 3.0f, cap = StrokeCap.Round))
        drawOutline(outline, color = color.copy(alpha = alpha * 0.38f), style = Stroke(width = w * 1.6f, cap = StrokeCap.Round))
        drawOutline(outline, color = color.copy(alpha = alpha), style = Stroke(width = w * 0.7f, cap = StrokeCap.Round))
    }
}

/** Text with a soft, low-opacity halo. */
private fun glowText(
    color: Color,
    fontSize: TextUnit,
    weight: FontWeight = FontWeight.Bold,
    blurPx: Float = 8f,
    strength: Float = 0.55f,
): TextStyle = TextStyle(
    color = color,
    fontSize = fontSize,
    fontWeight = weight,
    shadow = Shadow(color = color.copy(alpha = strength), offset = Offset(0f, 0f), blurRadius = blurPx),
)

/** Card with gradient background, edge light border and a halo hugging its outline. */
@Composable
private fun GlowCard(
    modifier: Modifier = Modifier,
    glowColor: Color = Color.White,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = Color.Transparent, shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .edgeGlow(glowColor, 14.dp, 3.dp, 0.28f)
                .background(Brush.verticalGradient(listOf(CardBgAlt, CardBg)))
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(Color(0x30FFFFFF), Color(0x0AFFFFFF), Color(0xFFFF4655).copy(alpha = 0.14f))),
                    RoundedCornerShape(14.dp),
                ),
            content = content,
        )
    }
}

@Composable
private fun GradientLine(modifier: Modifier = Modifier, color: Color = DividerColor) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, color, Color.Transparent)))
    )
}

@Composable
private fun AmbientGlows() {
    Canvas(Modifier.fillMaxSize()) {
        fun wash(cx: Float, cy: Float, r: Float, color: Color) {
            drawCircle(
                brush = Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(cx, cy), radius = r),
                radius = r, center = Offset(cx, cy),
            )
        }
        wash(size.width * 0.10f, size.height * 0.03f, size.width * 0.80f, ValorantRed.copy(alpha = 0.06f))
        wash(size.width * 0.95f, size.height * 0.55f, size.width * 0.90f, AccentBlue.copy(alpha = 0.045f))
        wash(size.width * 0.30f, size.height * 0.95f, size.width * 0.75f, AccentYellow.copy(alpha = 0.03f))
    }
}

// ---------- Entry ----------

@Composable
fun ValTrackerScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("valorant_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    if (prefs.getBoolean("assets_preloaded", false)) {
        AssetPreloader.done = true
    }

    if (!AssetPreloader.done) {
        LaunchedEffect(Unit) {
            AssetPreloader.preload(context)
            prefs.edit().putBoolean("assets_preloaded", true).apply()
        }
        PreloadScreen(AssetPreloader.progress)
        return
    }

    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var riotId by remember { mutableStateOf(prefs.getString("riot_id", "") ?: "") }
    var region by remember { mutableStateOf(prefs.getString("region", "eu") ?: "eu") }
    var showKey by remember { mutableStateOf(false) }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var account by remember { mutableStateOf<AccountInfo?>(null) }
    var mmr by remember { mutableStateOf<MmrInfo?>(null) }
    var history by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    var matches by remember { mutableStateOf<List<MatchSummary>>(emptyList()) }
    var hasMore by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var loadMoreError by remember { mutableStateOf<String?>(null) }

    var screen by remember { mutableStateOf("input") } // input | profile | match
    var selectedMatch by remember { mutableStateOf<MatchSummary?>(null) }

    fun parseRiotId(s: String): Pair<String, String>? {
        val idx = s.lastIndexOf('#')
        if (idx <= 0 || idx == s.length - 1) return null
        return s.substring(0, idx).trim() to s.substring(idx + 1).trim()
    }

    val bannerUrl = account?.cardId?.let { "https://media.valorant-api.com/playercards/$it/wideart.png" }
    val avatarUrl = account?.cardId?.let { "https://media.valorant-api.com/playercards/$it/smallart.png" }

    val shareStats = remember(matches) { aggregateStats(matches) }

    val onShareStats: () -> Unit = onShare@{
        val parsed = parseRiotId(riotId) ?: return@onShare
        val (name, tag) = parsed
        scope.launch {
            val f = ShareCardBuilder.build(
                context = context,
                playerName = name,
                playerTag = tag,
                tier = mmr?.tier,
                rr = mmr?.rr,
                elo = mmr?.elo,
                peak = mmr?.highestRank,
                peakSeason = mmr?.peakSeason,
                wins = shareStats.wins,
                games = shareStats.games,
                winPctStr = fmt1(shareStats.winPct) + "%",
                kdStr = fmt2(shareStats.kd),
                hsPctStr = fmt1(shareStats.hsPct) + "%",
                acsStr = fmt1(shareStats.acs),
                rankIconUrl = rankIconUrl(mmr),
                bannerUrl = bannerUrl,
            )
            if (f != null) ShareCardBuilder.shareImage(context, f)
        }
    }

    if (screen == "input") {
        InputScreen(
            apiKey = apiKey, onApiKey = { apiKey = it },
            riotId = riotId, onRiotId = { riotId = it },
            region = region, onRegion = { region = it },
            showKey = showKey, onToggleKey = { showKey = !showKey },
            loading = loading,
            error = error,
            onFetch = {
                val parsed = parseRiotId(riotId)
                if (parsed == null) { error = "Enter a valid RIOT ID like Name#TAG"; return@InputScreen }
                val (name, tag) = parsed
                loading = true
                error = null
                prefs.edit()
                    .putString("api_key", apiKey.trim())
                    .putString("riot_id", riotId.trim())
                    .putString("region", region.trim())
                    .apply()
                scope.launch {
                    val accDef = async { ValApi.getAccount(name, tag, apiKey) }
                    val mmrDef = async { ValApi.getMmr(region.trim(), "pc", name, tag, apiKey) }
                    val histDef = async { ValApi.getMmrHistory(region.trim(), "pc", name, tag, apiKey) }
                    val matchDef = async { ValApi.getMatches(region.trim(), "pc", name, tag, 20, apiKey) }
                    val accRes = accDef.await()
                    val mmrRes = mmrDef.await()
                    val histRes = histDef.await()
                    val matchRes = matchDef.await()
                    account = accRes.getOrNull()
                    mmr = mmrRes.getOrNull()
                    history = histRes.getOrNull() ?: emptyList()
                    matches = matchRes.getOrNull() ?: emptyList()
                    hasMore = matchRes.getOrNull()?.size?.let { it >= 20 } ?: true
                    loadingMore = false
                    loadMoreError = null
                    val failures = listOf(accRes, mmrRes).filter { it.isFailure }
                    if (account == null && mmr == null) {
                        error = failures.firstOrNull()?.exceptionOrNull()?.message ?: "No data returned"
                    } else if (failures.isNotEmpty()) {
                        error = failures.first().exceptionOrNull()?.message
                    }
                    loading = false
                    if (account != null || mmr != null) screen = "profile"
                }
            }
        )
    } else if (screen == "profile") {
        ProfileScreen(
            account = account,
            mmr = mmr,
            history = history,
            matches = matches,
            bannerUrl = bannerUrl,
            avatarUrl = avatarUrl,
            region = region,
            apiKey = apiKey,
            riotIdText = riotId,
            onBack = { screen = "input" },
            onMatchClick = { m ->
                selectedMatch = m
                screen = "match"
            },
            onRefresh = {
                val parsed = parseRiotId(riotId)
                if (parsed == null) return@ProfileScreen
                val (name, tag) = parsed
                loading = true
                scope.launch {
                    val accDef = async { ValApi.getAccount(name, tag, apiKey) }
                    val mmrDef = async { ValApi.getMmr(region.trim(), "pc", name, tag, apiKey) }
                    val histDef = async { ValApi.getMmrHistory(region.trim(), "pc", name, tag, apiKey) }
                    val matchDef = async { ValApi.getMatches(region.trim(), "pc", name, tag, 20, apiKey) }
                    val accRes = accDef.await()
                    val mmrRes = mmrDef.await()
                    val histRes = histDef.await()
                    val matchRes = matchDef.await()
                    account = accRes.getOrNull() ?: account
                    mmr = mmrRes.getOrNull() ?: mmr
                    history = histRes.getOrNull() ?: history
                    matchRes.getOrNull()?.let { fresh ->
                        if (fresh.size >= 20) hasMore = true
                        matches = listUnion(matches, fresh)
                    }
                    loading = false
                }
            },
            loading = loading,
            loadingMore = loadingMore,
            hasMore = hasMore,
            loadMoreError = loadMoreError,
            onLoadMore = {
                val parsed = parseRiotId(riotId)
                if (parsed == null || loadingMore || !hasMore) return@ProfileScreen
                val (name, tag) = parsed
                loadingMore = true
                loadMoreError = null
                scope.launch {
                    val res = ValApi.getMatches(region.trim(), "pc", name, tag, 20, apiKey, start = matches.size)
                    val fresh = res.getOrNull().orEmpty()
                    if (res.isSuccess) {
                        val added = listUnion(matches, fresh)
                        matches = added
                        if (fresh.size < 20) hasMore = false
                    } else {
                        loadMoreError = res.exceptionOrNull()?.message ?: "Could not load more matches"
                    }
                    loadingMore = false
                }
            },
            onShare = onShareStats,
        )
    } else {
        val m = selectedMatch
        if (m != null) {
            MatchDetailScreen(
                match = m,
                onBack = { screen = "profile" },
            )
        } else {
            screen = "profile"
        }
    }
}

private fun listUnion(old: List<MatchSummary>, fresh: List<MatchSummary>): List<MatchSummary> {
    val seen = old.map { it.matchId }.toMutableSet()
    return old + fresh.filter { seen.add(it.matchId) }
}

// ---------- Input screen (with key validation + region picker) ----------

private enum class KeyStatus { UNKNOWN, CHECKING, VALID, INVALID, UNVERIFIED }

@Composable
private fun InputScreen(
    apiKey: String, onApiKey: (String) -> Unit,
    riotId: String, onRiotId: (String) -> Unit,
    region: String, onRegion: (String) -> Unit,
    showKey: Boolean, onToggleKey: () -> Unit,
    loading: Boolean,
    error: String?,
    onFetch: () -> Unit,
) {
    var keyStatus by remember { mutableStateOf(KeyStatus.UNKNOWN) }

    LaunchedEffect(apiKey) {
        if (apiKey.isBlank()) {
            keyStatus = KeyStatus.UNKNOWN
        } else {
            keyStatus = KeyStatus.CHECKING
            delay(800) // debounce
            keyStatus = when (ValApi.checkKey(apiKey)) {
                KeyCheck.VALID -> KeyStatus.VALID
                KeyCheck.INVALID -> KeyStatus.INVALID
                KeyCheck.NETWORK -> KeyStatus.UNVERIFIED
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(ValorantDarker, ValorantDark, Color(0xFF111D27)))
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.mit.firstgo.R.drawable.logo),
                contentDescription = "ValoTrak logo",
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Valo", color = ValorantRed, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text("Trak", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Black)
            }
            Text("Valorant Progress Tracker", color = TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))

            Surface(color = CardBg.copy(alpha = 0.9f), shape = RoundedCornerShape(24.dp), tonalElevation = 4.dp) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Setup", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                    OutlinedTextField(
                        value = apiKey, onValueChange = onApiKey,
                        label = { Text("HenrikDev API Key", color = TextSecondary) },
                        textStyle = TextStyle(color = TextPrimary),
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            Text(if (showKey) "Hide" else "Show", color = ValorantRed,
                                modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(8.dp)).padding(4.dp)
                                    .clickableSafe { onToggleKey() })
                        }
                    )

                    // Key validation status
                    when (keyStatus) {
                        KeyStatus.CHECKING -> Text("Checking key…", color = TextSecondary, fontSize = 11.sp)
                        KeyStatus.VALID -> Text("✓ API key looks valid", color = WinGreen, fontSize = 11.sp)
                        KeyStatus.INVALID -> Text("✗ Key rejected (401/403) — get a free key: docs.henrikdev.xyz/general/auth", color = ValorantRed, fontSize = 11.sp)
                        KeyStatus.UNVERIFIED -> Text("⚠ Couldn't verify key right now (network/API hiccup) — Fetch will still work if the key is valid", color = AccentYellow, fontSize = 11.sp)
                        KeyStatus.UNKNOWN -> {}
                    }

                    OutlinedTextField(
                        value = riotId, onValueChange = onRiotId,
                        label = { Text("RIOT ID (Name#TAG)", color = TextSecondary) },
                        textStyle = TextStyle(color = TextPrimary),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )

                    // Region chips
                    Text("Region", color = TextSecondary, fontSize = 12.sp)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("eu", "na", "ap", "kr", "latam", "br").forEach { r ->
                            val selected = region.equals(r, ignoreCase = true)
                            Surface(
                                color = if (selected) ValorantRed else CardBgAlt,
                                shape = RoundedCornerShape(10.dp),
                                modifier = (if (selected) Modifier.edgeGlow(ValorantRed, 10.dp, 2.dp, 0.5f) else Modifier)
                                    .clickableSafe { onRegion(r) }
                            ) {
                                Text(
                                    r.uppercase(),
                                    color = if (selected) Color.White else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onFetch, enabled = !loading,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ValorantRed, contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        else Text("Fetch", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (error != null) {
                Surface(color = ValorantRed.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)) {
                    Text("⚠  $error", color = ValorantRed, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                }
            }
        }
    }
}

// ---------- Aggregated stats over a match set ----------

private data class AggStats(
    val games: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val kills: Int,
    val deaths: Int,
    val assists: Int,
    val headshots: Int,
    val bodyshots: Int,
    val legshots: Int,
    val damage: Int,
    val rounds: Int,
    val score: Int,
    val playSec: Int,
) {
    val winPct: Float get() = if (games > 0) wins * 100f / games else 0f
    val kd: Float get() = if (deaths > 0) kills.toFloat() / deaths else kills.toFloat()
    val kad: Float get() = if (deaths > 0) (kills + assists).toFloat() / deaths else (kills + assists).toFloat()
    val hsPct: Float get() {
        val shots = headshots + bodyshots + legshots
        return if (shots > 0) headshots * 100f / shots else 0f
    }
    val bodyPct: Float get() {
        val shots = headshots + bodyshots + legshots
        return if (shots > 0) bodyshots * 100f / shots else 0f
    }
    val legPct: Float get() {
        val shots = headshots + bodyshots + legshots
        return if (shots > 0) legshots * 100f / shots else 0f
    }
    val adr: Float get() = if (rounds > 0) damage.toFloat() / rounds else 0f
    val acs: Float get() = if (rounds > 0) score.toFloat() / rounds else 0f
    val killsPerRound: Float get() = if (rounds > 0) kills.toFloat() / rounds else 0f
}

private fun aggregateStats(matches: List<MatchSummary>): AggStats {
    val mine = matches.mapNotNull { it.me }
    val damage = mine.sumOf { it.damage ?: 0 }
    val rounds = matches.sumOf { it.roundsPlayed ?: 0 }
    val score = mine.sumOf { it.score ?: 0 }
    return AggStats(
        games = matches.size,
        wins = matches.count { it.result == "WIN" },
        losses = matches.count { it.result == "LOSS" },
        draws = matches.count { it.result == "DRAW" },
        kills = mine.sumOf { it.kills ?: 0 },
        deaths = mine.sumOf { it.deaths ?: 0 },
        assists = mine.sumOf { it.assists ?: 0 },
        headshots = mine.sumOf { it.headshots ?: 0 },
        bodyshots = mine.sumOf { it.bodyshots ?: 0 },
        legshots = mine.sumOf { it.legshots ?: 0 },
        damage = damage,
        rounds = rounds,
        score = score,
        playSec = matches.mapNotNull { it.gameLengthSec }.sum(),
    )
}

private fun matchAcs(m: MatchSummary): Float {
    val r = m.roundsPlayed ?: 0
    val s = m.me?.score ?: 0
    return if (r > 0) s.toFloat() / r else 0f
}

private data class AgentStat(
    val name: String,
    val icon: String?,
    val games: Int,
    val wins: Int,
    val kills: Int,
    val deaths: Int,
    val assists: Int,
    val damage: Int,
    val rounds: Int,
    val score: Int,
    val hs: Int,
    val shots: Int,
) {
    val winPct: Float get() = if (games > 0) wins * 100f / games else 0f
    val kd: Float get() = if (deaths > 0) kills.toFloat() / deaths else kills.toFloat()
    val adr: Float get() = if (rounds > 0) damage.toFloat() / rounds else 0f
    val acs: Float get() = if (rounds > 0) score.toFloat() / rounds else 0f
    val hsPct: Float get() = if (shots > 0) hs * 100f / shots else 0f
}

private fun agentStats(matches: List<MatchSummary>): List<AgentStat> =
    matches.groupBy { it.me?.agent ?: "Unknown" }
        .mapValues { (_, ms) ->
            val me = ms.mapNotNull { it.me }
            AgentStat(
                name = ms.first().me?.agent ?: "Unknown",
                icon = ms.first().me?.agentIcon,
                games = ms.size,
                wins = ms.count { it.result == "WIN" },
                kills = me.sumOf { it.kills ?: 0 },
                deaths = me.sumOf { it.deaths ?: 0 },
                assists = me.sumOf { it.assists ?: 0 },
                damage = me.sumOf { it.damage ?: 0 },
                rounds = ms.sumOf { it.roundsPlayed ?: 0 },
                score = me.sumOf { it.score ?: 0 },
                hs = me.sumOf { it.headshots ?: 0 },
                shots = me.sumOf { (it.headshots ?: 0) + (it.bodyshots ?: 0) + (it.legshots ?: 0) },
            )
        }
        .values.sortedByDescending { it.games }
        .take(6)

private fun rankIconUrl(mmr: MmrInfo?): String? =
    mmr?.rankImage
        ?: mmr?.tierId?.let { "https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/$it/largeicon.png" }

// ---------- Profile (tabbed, Tracker.gg style, pull-to-refresh) ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScreen(
    account: AccountInfo?,
    mmr: MmrInfo?,
    history: List<HistoryEntry>,
    matches: List<MatchSummary>,
    bannerUrl: String?,
    avatarUrl: String?,
    region: String,
    apiKey: String,
    riotIdText: String,
    onBack: () -> Unit,
    onMatchClick: (MatchSummary) -> Unit,
    onRefresh: () -> Unit,
    loading: Boolean,
    loadingMore: Boolean,
    hasMore: Boolean,
    loadMoreError: String?,
    onLoadMore: () -> Unit,
    onShare: () -> Unit,
) {
    var modeFilter by remember { mutableStateOf("All") }
    val modes = remember(matches) {
        val order = listOf("All", "Competitive", "Unrated", "Swiftplay", "Deathmatch", "Team Deathmatch", "Escalation", "Spike Rush")
        (order + matches.mapNotNull { it.mode }.distinct()).distinct()
    }
    val filtered = remember(matches, modeFilter) {
        if (modeFilter == "All") matches else matches.filter { (it.mode ?: "").equals(modeFilter, ignoreCase = true) }
    }
    val stats = remember(filtered) { aggregateStats(filtered) }
    val allStats = remember(matches) { aggregateStats(matches) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Matches", "Stats", "Ranks", "Agents", "Board", "Store")

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ValorantDarker, ValorantDark, Color(0xFF0E1B28))))) {
        AmbientGlows()
        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

                // ------- Header banner -------
                Box(Modifier.fillMaxWidth().height(200.dp).background(ValorantDark)) {
                    if (bannerUrl != null) {
                        RemoteImage(url = bannerUrl, contentDescription = "banner", modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    }
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 1f to ValorantDarker.copy(alpha = 0.95f))))
                    Surface(
                        color = Color.Black.copy(alpha = 0.4f), shape = CircleShape,
                        modifier = Modifier.padding(12.dp).size(40.dp).clickableSafe { onBack() }
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text("←", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    }
                    Row(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (avatarUrl != null) {
                            RemoteImage(url = avatarUrl, contentDescription = "avatar",
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).edgeGlow(AccentBlue, 12.dp, 2.dp, 0.30f)
                                    .border(2.dp, Brush.linearGradient(listOf(ValorantRed, AccentBlue)), RoundedCornerShape(12.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            Spacer(Modifier.width(12.dp))
                        }
                        Column {
                            Text(account?.let { "${it.name}#${it.tag}" } ?: "Profile", style = glowText(TextPrimary, 22.sp, FontWeight.Bold, 6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(account?.level?.let { "Level $it  •  $region" } ?: region, color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }

                // ------- Tab bar (scrollable chips) -------
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .background(ValorantDarker.copy(alpha = 0.65f))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tabs.forEachIndexed { i, t ->
                        val selected = selectedTab == i
                        Surface(
                            color = if (selected) ValorantRed else CardBg,
                            shape = RoundedCornerShape(9.dp),
                            modifier = (if (selected) Modifier.edgeGlow(ValorantRed, 9.dp, 2.dp, 0.5f) else Modifier)
                                .clickableSafe { selectedTab = i }
                        ) {
                            Text(
                                t,
                                color = if (selected) Color.White else TextSecondary,
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (selectedTab == 0) OverviewTab(account, mmr, allStats, history, matches, onShare)
                    if (selectedTab == 1) {
                        MatchesTab(
                            filtered = filtered, modes = modes, modeFilter = modeFilter,
                            onModeFilter = { modeFilter = it }, onMatchClick = onMatchClick,
                            totalCount = matches.size, hasMore = hasMore,
                            loadingMore = loadingMore, loadMoreError = loadMoreError,
                            onLoadMore = onLoadMore,
                        )
                    }
                    if (selectedTab == 2) StatsTab(allStats, matches)
                    if (selectedTab == 3) RanksTab(mmr, history)
                    if (selectedTab == 4) AgentsTab()
                    if (selectedTab == 5) BoardTab(apiKey, region, riotIdText)
                    if (selectedTab == 6) StoreTab(apiKey, region)
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }
}

// ---------- Overview ----------

@Composable
private fun OverviewTab(
    account: AccountInfo?,
    mmr: MmrInfo?,
    stats: AggStats,
    history: List<HistoryEntry>,
    matches: List<MatchSummary>,
    onShare: () -> Unit,
) {
    SectionTitle("CURRENT RATING")
    GlowCard(glowColor = AccentYellow) {
        Column {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val img = rankIconUrl(mmr)
                if (img != null) {
                    Box(Modifier.size(64.dp).glow(AccentYellow, alpha = 0.40f), contentAlignment = Alignment.Center) {
                        RemoteImage(url = img, contentDescription = mmr?.tier ?: "rank", modifier = Modifier.size(64.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(mmr?.tier ?: "Unranked", style = glowText(TextPrimary, 20.sp, FontWeight.Bold, 6f))
                    if (mmr?.rr != null) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${mmr.rr}", style = glowText(AccentYellow, 20.sp, FontWeight.Black, 10f))
                            Text(" RR", style = glowText(AccentYellow, 12.sp, FontWeight.Bold, 6f))
                        }
                    }
                    if (mmr?.lastChange != null) {
                        val lc = mmr.lastChange!!
                        val col = if (lc >= 0) WinGreen else ValorantRed
                        Text("Last match  ${if (lc >= 0) "+" else ""}$lc RR", style = glowText(col, 12.sp, FontWeight.SemiBold, 5f))
                    }
                    Spacer(Modifier.height(6.dp))
                    RankProgressBar(rr = mmr?.rr)
                }
            }
            GradientLine(Modifier.padding(horizontal = 16.dp))
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Peak Rating", style = glowText(TextPrimary, 14.sp, FontWeight.SemiBold, 4f))
                    mmr?.peakSeason?.let { Text(it, color = TextSecondary, fontSize = 11.sp) }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(mmr?.highestRank ?: "—", style = glowText(ValorantRed, 18.sp, FontWeight.Bold, 6f))
                    if (mmr?.elo != null) Text("ELO ${mmr.elo}", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }

    val seasonLabel = mmr?.seasonal?.lastOrNull()?.seasonName
        ?: mmr?.seasonal?.lastOrNull()?.seasonShort ?: ""
    SectionTitle(
        "SEASON OVERVIEW",
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${formatDuration(stats.playSec)} Playtime", color = TextSecondary, fontSize = 12.sp)
                Text("${stats.games} Matches", color = TextSecondary, fontSize = 12.sp)
                Surface(
                    color = CardBgAlt, shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.edgeGlow(AccentBlue, 8.dp, 2.dp, 0.35f).clickableSafe { onShare() }
                ) {
                    Text("Share", style = glowText(AccentBlue, 12.sp, FontWeight.SemiBold, 6f), modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
    )
    GlowCard(glowColor = AccentYellow) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val img = rankIconUrl(mmr)
                if (img != null) {
                    Box(Modifier.size(64.dp).glow(AccentYellow, alpha = 0.40f), contentAlignment = Alignment.Center) {
                        RemoteImage(url = img, contentDescription = "rank", modifier = Modifier.size(64.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(mmr?.tier ?: "Unranked", style = glowText(TextPrimary, 16.sp, FontWeight.SemiBold, 5f))
                    if (mmr?.rr != null) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${mmr.rr}", style = glowText(AccentYellow, 20.sp, FontWeight.Black, 10f))
                            Text(" RR", style = glowText(AccentYellow, 11.sp, FontWeight.SemiBold, 5f))
                        }
                    }
                    account?.level?.let {
                        Text("Level $it", color = TextSecondary, fontSize = 12.sp)
                    }
                }
                if (stats.games > 0) {
                    WinLossDonut(wins = stats.wins, losses = stats.losses)
                }
            }

            Spacer(Modifier.height(12.dp))
            val rrVals = history.mapNotNull { it.rr }.reversed()
            if (rrVals.size >= 2) {
                TrendChart(values = rrVals, modifier = Modifier.fillMaxWidth().height(90.dp))
                Spacer(Modifier.height(12.dp))
            }

            if (stats.games > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HighlightStat("Damage/Round", fmt1(stats.adr), Modifier.weight(1f), AccentYellow)
                    HighlightStat("K/D Ratio", fmt2(stats.kd), Modifier.weight(1f), WinGreen)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HighlightStat("Headshot %", fmt1(stats.hsPct) + "%", Modifier.weight(1f), ValorantRed)
                    HighlightStat("Win %", fmt1(stats.winPct) + "%", Modifier.weight(1f), AccentBlue)
                }
                Spacer(Modifier.height(14.dp))

                listOf(
                    listOf(Triple("Wins", stats.wins.toString(), AccentYellow),
                           Triple("Kills", fmtComma(stats.kills), ValorantRed),
                           Triple("Deaths", fmtComma(stats.deaths), TextPrimary)),
                    listOf(Triple("Assists", fmtComma(stats.assists), AccentBlue),
                           Triple("ACS", fmt1(stats.acs), WinGreen),
                           Triple("KAD Ratio", fmt2(stats.kad), AccentYellow)),
                    listOf(Triple("Kills/Round", fmt1(stats.killsPerRound), TextPrimary),
                           Triple("Damage", fmtComma(stats.damage), ValorantRed),
                           Triple("Rounds", fmtComma(stats.rounds), TextPrimary)),
                ).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { (label, value, accent) ->
                            ValueCell(label, value, accent, Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                matches.groupBy { it.me?.agent ?: "Unknown" }.maxByOrNull { it.value.size }?.let { (agent, ms) ->
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ms.first().me?.agentIcon?.let { icon ->
                            Box(Modifier.size(48.dp).glow(AccentBlue, alpha = 0.30f), contentAlignment = Alignment.Center) {
                                RemoteImage(url = icon, contentDescription = agent, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
                            }
                            Spacer(Modifier.width(12.dp))
                        }
                        Column {
                            Text("Most played agent", color = TextSecondary, fontSize = 11.sp)
                            Text(agent, style = glowText(TextPrimary, 16.sp, FontWeight.Bold, 5f))
                            Text("${ms.size} games  •  ${ms.count { it.result == "WIN" }} wins", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                Text("No match data yet — fetch your profile to see performance here.", color = TextSecondary, fontSize = 13.sp)
            }
        }
    }

    mmr?.highestRank?.let { peak ->
        SectionTitle("PEAK RANK")
        GlowCard(glowColor = ValorantRed) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("Peak", color = TextSecondary, fontSize = 11.sp); Text(peak, style = glowText(ValorantRed, 18.sp, FontWeight.Bold, 8f)) }
                mmr.peakSeason?.let { Column(horizontalAlignment = Alignment.End) { Text("Season", color = TextSecondary, fontSize = 11.sp); Text(it, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) } }
            }
        }
    }
}

@Composable
private fun RankProgressBar(rr: Int?, modifier: Modifier = Modifier) {
    val pct = (rr ?: 0).coerceIn(0, 100) / 100f
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(ValorantDark)) {
            Box(
                Modifier.fillMaxWidth(pct).height(8.dp)
                    .background(Brush.horizontalGradient(listOf(AccentYellow, ValorantRed.copy(alpha = 0.85f))))
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${rr ?: 0} RR in tier", color = TextSecondary, fontSize = 10.sp)
            Text("${(100 - (rr ?: 0)).coerceIn(0, 100)} RR to next tier", color = TextSecondary, fontSize = 10.sp)
        }
    }
}

// ---------- Matches (with load-more) ----------

@Composable
private fun MatchesTab(
    filtered: List<MatchSummary>,
    modes: List<String>,
    modeFilter: String,
    onModeFilter: (String) -> Unit,
    onMatchClick: (MatchSummary) -> Unit,
    totalCount: Int,
    hasMore: Boolean,
    loadingMore: Boolean,
    loadMoreError: String?,
    onLoadMore: () -> Unit,
) {
    val chipShape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        modes.forEach { md ->
            val selected = modeFilter.equals(md, ignoreCase = true)
            Surface(
                color = if (selected) ValorantRed else CardBg,
                shape = chipShape,
                modifier = (if (selected) Modifier.edgeGlow(ValorantRed, 8.dp, 2.dp, 0.5f) else Modifier)
                    .clickableSafe { onModeFilter(md) }
            ) {
                Text(
                    if (md == "All") "All Modes" else md,
                    color = if (selected) Color.White else TextSecondary,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }

    SectionTitle(
        "MATCH HISTORY",
        trailing = { Text("$totalCount games loaded  •  tap for scoreboard", color = TextSecondary, fontSize = 12.sp) }
    )

    if (filtered.isEmpty()) {
        Surface(color = CardBg, shape = RoundedCornerShape(12.dp)) {
            Text(
                if (modeFilter == "All") "No match history yet — pull down to refresh."
                else "No matches found for \"$modeFilter\" — try a different mode or load more games.",
                color = TextSecondary, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(14.dp)
            )
        }
    } else {
        filtered.forEach { m ->
            MatchRow(m, onClick = { onMatchClick(m) })
        }
    }

    if (loadMoreError != null) {
        Text(loadMoreError, color = ValorantRed, fontSize = 12.sp)
    }

    if (loadingMore) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = ValorantRed)
            Spacer(Modifier.width(10.dp))
            Text("Loading older games…", color = TextSecondary, fontSize = 12.sp)
        }
    } else if (hasMore) {
        Surface(
            color = CardBg, shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().clickableSafe { onLoadMore() }
        ) {
            Text(
                "Load 20 more games",
                style = glowText(TextPrimary, 13.sp, FontWeight.Bold, 4f),
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                textAlign = TextAlign.Center,
            )
        }
    } else if (filtered.isNotEmpty()) {
        Text("End of history for now", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

// ---------- Stats ----------

@Composable
private fun StatsTab(stats: AggStats, matches: List<MatchSummary>) {
    if (stats.games == 0) {
        Surface(color = CardBg, shape = RoundedCornerShape(12.dp)) {
            Text("No match data yet — pull down to refresh.", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(14.dp))
        }
        return
    }

    SectionTitle("RECENT PERFORMANCE", trailing = { Text("Last ${stats.games} matches", color = TextSecondary, fontSize = 12.sp) })
    GlowCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ValueCell("Wins", "${stats.wins}/${stats.games}", AccentYellow, Modifier.weight(1f))
                ValueCell("Win %", fmt1(stats.winPct) + "%", WinGreen, Modifier.weight(1f))
                ValueCell("K/D", fmt2(stats.kd), ValorantRed, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HighlightStat("Headshot %", fmt1(stats.hsPct) + "%", Modifier.weight(1f), ValorantRed)
                HighlightStat("ACS", fmt1(stats.acs), Modifier.weight(1f), AccentBlue)
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HighlightStat("Damage/Round", fmt1(stats.adr), Modifier.weight(1f), AccentYellow)
                HighlightStat("KAD Ratio", fmt2(stats.kad), Modifier.weight(1f), WinGreen)
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth()) {
                ValueCell("Kills", fmtComma(stats.kills), TextPrimary, Modifier.weight(1f))
                ValueCell("Deaths", fmtComma(stats.deaths), TextPrimary, Modifier.weight(1f))
                ValueCell("Assists", fmtComma(stats.assists), TextPrimary, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                ValueCell("Headshots", fmtComma(stats.headshots), ValorantRed, Modifier.weight(1f))
                ValueCell("Damage", fmtComma(stats.damage), AccentYellow, Modifier.weight(1f))
                ValueCell("Rounds", fmtComma(stats.rounds), TextPrimary, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                ValueCell("Kills/Round", fmt1(stats.killsPerRound), TextPrimary, Modifier.weight(1f))
                ValueCell("Body %", fmt1(stats.bodyPct) + "%", AccentBlue, Modifier.weight(1f))
                ValueCell("Leg %", fmt1(stats.legPct) + "%", TextSecondary, Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            val kdTrend = matches.map { (it.me?.kills ?: 0).toFloat() }.reversed()
            if (kdTrend.size >= 2) {
                Text("Kills per match", color = TextSecondary, fontSize = 11.sp)
                BarChart(values = kdTrend, modifier = Modifier.fillMaxWidth().height(110.dp))
            }
            val acsTrend = matches.map { matchAcs(it) }.reversed()
            if (acsTrend.size >= 2) {
                Spacer(Modifier.height(4.dp))
                Text("ACS per match", color = TextSecondary, fontSize = 11.sp)
                BarChart(values = acsTrend, colors = acsTrend.map { AccentBlue }, modifier = Modifier.fillMaxWidth().height(110.dp))
            }
        }
    }

    SectionTitle("TOP AGENTS", trailing = { Text("Based on ${stats.games} matches", color = TextSecondary, fontSize = 12.sp) })
    GlowCard {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                Text("Agent", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1.6f))
                Text("Matches", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(0.7f), textAlign = TextAlign.End)
                Text("Win %", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(0.7f), textAlign = TextAlign.End)
                Text("K/D", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
                Text("ADR", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
                Text("ACS", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
            }
            GradientLine()
            agentStats(matches).forEach { ag ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (ag.icon != null) {
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            RemoteImage(url = ag.icon, contentDescription = ag.name, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)))
                        }
                        Spacer(Modifier.width(10.dp))
                    } else {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).background(ValorantDark), contentAlignment = Alignment.Center) {
                            Text(ag.name.take(2).uppercase(), color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(ag.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${fmt1(ag.hsPct)}% HS", color = TextSecondary, fontSize = 11.sp)
                    }
                    Text("${ag.games}", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f), textAlign = TextAlign.End)
                    Text(fmt1(ag.winPct) + "%", style = glowText(AccentYellow, 13.sp, FontWeight.SemiBold, 5f), modifier = Modifier.weight(0.7f), textAlign = TextAlign.End)
                    Text(fmt2(ag.kd), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
                    Text(fmt1(ag.adr), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
                    Text(fmt1(ag.acs), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
                }
            }
        }
    }

    if (stats.headshots + stats.bodyshots + stats.legshots > 0) {
        SectionTitle("ACCURACY", trailing = { Text("AVG HS ${fmt1(stats.hsPct)}%", color = AccentYellow, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) })
        GlowCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AccuracyRow("Head", stats.headshots, stats.hsPct, ValorantRed)
                AccuracyRow("Body", stats.bodyshots, stats.bodyPct, AccentYellow)
                AccuracyRow("Legs", stats.legshots, stats.legPct, TextSecondary)
                GradientLine()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total shots", color = TextSecondary, fontSize = 12.sp)
                    Text(fmtComma(stats.headshots + stats.bodyshots + stats.legshots), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------- Ranks ----------

@Composable
private fun RanksTab(mmr: MmrInfo?, history: List<HistoryEntry>) {
    val seasonal = mmr?.seasonal ?: emptyList()
    if (seasonal.isNotEmpty()) {
        SectionTitle("ACT RANKS", trailing = { Text("${seasonal.size} acts  •  tap to expand", color = TextSecondary, fontSize = 12.sp) })
        var expanded by remember { mutableStateOf(false) }
        Surface(color = CardBg, shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().clickableSafe { expanded = !expanded }.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            mmr?.highestRank?.let { "Peak ever: $it" } ?: "End-of-act ranks",
                            style = glowText(ValorantRed, 13.sp, FontWeight.Bold, 5f)
                        )
                        mmr?.peakSeason?.let { Text(it, color = TextSecondary, fontSize = 11.sp) }
                    }
                    Text(if (expanded) "▲" else "▼", color = TextSecondary, fontSize = 13.sp)
                }
                if (expanded) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        seasonal.take(12).forEach { s ->
                            Surface(color = ValorantDark, shape = RoundedCornerShape(10.dp)) {
                                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(s.seasonName ?: s.seasonShort ?: "Act", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${s.wins}W / ${s.games}G  •  ${s.endTier ?: "—"}", color = TextSecondary, fontSize = 12.sp)
                                    if (s.actWins.isNotEmpty()) {
                                        Text("Peak: ${s.actWins.last()}  (${s.actWins.size} act wins)", style = glowText(ValorantRed, 11.sp, FontWeight.Bold, 4f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    SectionTitle("RR HISTORY", trailing = { Text("${history.size} games", color = TextSecondary, fontSize = 12.sp) })
    if (history.isEmpty()) {
        Surface(color = CardBg, shape = RoundedCornerShape(12.dp)) {
            Text("No rating history yet — pull down to refresh.", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(14.dp))
        }
    } else {
        val rrValues = history.mapNotNull { it.rr }.reversed()
        if (rrValues.size >= 2) {
            GlowCard(glowColor = WinGreen) {
                Column(Modifier.fillMaxWidth().padding(12.dp, 10.dp)) {
                    TrendChart(values = rrValues, modifier = Modifier.fillMaxWidth().height(120.dp))
                    val changeValues = history.mapNotNull { it.lastChange?.toFloat() }
                    if (changeValues.size >= 2) {
                        Spacer(Modifier.height(8.dp))
                        Text("RR change per match", color = TextSecondary, fontSize = 11.sp)
                        BarChart(values = changeValues, modifier = Modifier.fillMaxWidth().height(120.dp))
                    }
                }
            }
        }
        history.take(20).forEach { h ->
            Surface(color = CardBg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (h.mapId != null) {
                        RemoteImage(url = "https://media.valorant-api.com/maps/${h.mapId}/splash.png", contentDescription = h.map,
                            modifier = Modifier.size(width = 56.dp, height = 34.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(h.map ?: "Unknown", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(h.date?.take(10) ?: "", color = TextSecondary, fontSize = 11.sp)
                    }
                    if (h.lastChange != null) {
                        val lc = h.lastChange!!
                        val sign = if (lc >= 0) "+" else ""
                        val col = if (lc >= 0) WinGreen else ValorantRed
                        Text("$sign$lc", style = glowText(col, 13.sp, FontWeight.Bold, 5f))
                    }
                }
            }
        }
    }
}

// ---------- Agents ----------

@Composable
private fun AgentsTab() {
    var agents by remember { mutableStateOf<List<AgentInfo>?>(null) }
    var loadErr by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (agents == null) {
            val res = ValApi.getAgents()
            agents = res.getOrNull()
            if (agents == null) loadErr = res.exceptionOrNull()?.message ?: "Could not load agents"
        }
    }

    var query by remember { mutableStateOf("") }
    var roleFilter by remember { mutableStateOf("All") }
    var selected by remember { mutableStateOf<AgentInfo?>(null) }

    SectionTitle(
        "AGENTS",
        trailing = { Text("browser  •  tap an agent for detail", color = TextSecondary, fontSize = 12.sp) }
    )

    selected?.let { sel ->
        GlowCard(glowColor = AccentBlue) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        sel.roleIcon?.let {
                            RemoteImage(url = it, contentDescription = sel.roleName, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(sel.name, style = glowText(TextPrimary, 20.sp, FontWeight.Bold, 8f))
                    }
                    Text("Close", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.clickableSafe { selected = null }.padding(4.dp))
                }
                Spacer(Modifier.height(10.dp))
                if (sel.fullPortrait != null) {
                    RemoteImage(url = sel.fullPortrait, contentDescription = sel.name,
                        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                    Spacer(Modifier.height(10.dp))
                }
                Text(sel.description?.take(320)?.trim() ?: "", color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    sel.abilities.forEach { ab ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (ab.icon != null) {
                                RemoteImage(url = ab.icon, contentDescription = ab.name, modifier = Modifier.size(34.dp))
                            }
                            Text(ab.name, color = TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(64.dp), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Search", color = TextSecondary) },
            textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
            singleLine = true, modifier = Modifier.weight(1f),
        )
    }

    val roles = remember(agents) {
        listOf("All") + (agents?.mapNotNull { it.roleName }?.distinct() ?: emptyList())
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        roles.forEach { r ->
            val sel2 = roleFilter == r
            Surface(
                color = if (sel2) ValorantRed else CardBg,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.clickableSafe { roleFilter = r }
            ) {
                Text(r, color = if (sel2) Color.White else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }

    if (loadErr != null) {
        val le = loadErr
        Text(le!!, color = ValorantRed, fontSize = 12.sp)
    }

    val list = agents
        ?.filter { roleFilter == "All" || it.roleName == roleFilter }
        ?.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        .orEmpty()

    list.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { ag ->
                Surface(
                    color = if (selected == ag) CardBgAlt else CardBg,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).clickableSafe { selected = ag }
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (ag.icon != null) {
                            Box(Modifier.size(84.dp).glow(AccentBlue, alpha = 0.28f), contentAlignment = Alignment.Center) {
                                RemoteImage(url = ag.icon, contentDescription = ag.name, modifier = Modifier.size(80.dp))
                            }
                        }
                        Text(ag.name, style = glowText(TextPrimary, 13.sp, FontWeight.SemiBold, 3f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(ag.roleName ?: "—", color = TextSecondary, fontSize = 10.sp)
                    }
                }
            }
            // keep 2-column alignment when odd count
            if (pair.size == 1) Box(Modifier.weight(1f))
        }
    }
    if (agents != null && list.isEmpty() && loadErr == null) {
        Text("No agents match this filter", color = TextSecondary, fontSize = 12.sp)
    }
}
// ---------- Leaderboard ----------

@Composable
private fun BoardTab(apiKey: String, region: String, riotIdText: String) {
    val scope = rememberCoroutineScope()
    var board by remember { mutableStateOf<List<LeaderboardEntry>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var finding by remember { mutableStateOf(false) }
    var myResult by remember { mutableStateOf<LeaderboardEntry?>(null) }
    var findErr by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(region, apiKey) {
        if (board != null) return@LaunchedEffect
        err = null
        val res = ValApi.getLeaderboard(region.trim(), "pc", 1, 100, null, null, apiKey)
        val l = res.getOrNull()
        if (l != null && l.isNotEmpty()) board = l else err = res.exceptionOrNull()?.message ?: "Leaderboard unavailable"
    }

    SectionTitle("REGION LEADERBOARD", trailing = { Text("$region PC  •  top 100", color = TextSecondary, fontSize = 12.sp) })

    // Find-me card
    if (myResult != null) {
        val meEntry = myResult!!
        Surface(color = CardBgAlt, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().edgeGlow(AccentYellow, 12.dp, 2.dp, 0.35f)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("#${meEntry.rank}", style = glowText(AccentYellow, 16.sp, FontWeight.Black, 8f), modifier = Modifier.width(64.dp))
                Column(Modifier.weight(1f)) {
                    Text("${meEntry.name}#${meEntry.tag}", style = glowText(TextPrimary, 14.sp, FontWeight.Bold, 4f))
                }
                if (meEntry.rr != null) Text("${meEntry.rr} RR", style = glowText(AccentYellow, 13.sp, FontWeight.SemiBold, 5f))
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (finding) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ValorantRed)
            Spacer(Modifier.width(8.dp))
            Text("Finding you…", color = TextSecondary, fontSize = 12.sp)
        } else {
            Surface(
                color = CardBg, shape = RoundedCornerShape(8.dp),
                modifier = Modifier.clickableSafe {
                    val parts = riotIdText.split('#')
                    if (parts.size < 2) { findErr = "Set your RIOT ID first (Name#Tag)"; return@clickableSafe }
                    finding = true
                    findErr = null
                    myResult = null
                    scope.launch {
                        val res = ValApi.getLeaderboard(region.trim(), "pc", 1, 1, parts[0].trim(), parts[1].trim(), apiKey)
                        val l = res.getOrNull()
                        myResult = l?.firstOrNull()
                        if (myResult == null) findErr = "Not found on the $region leaderboard"
                        finding = false
                    }
                }
            ) {
                Text("Find my rank", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
            if (findErr != null) {
                val fe = findErr
                Text(fe!!, color = ValorantRed, fontSize = 10.sp)
            }
        }
    }

    if (err != null) {
        Surface(color = CardBg, shape = RoundedCornerShape(12.dp)) {
            Text("Leaderboard unavailable: $err", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(14.dp))
        }
    }

    board?.let { entries ->
        entries.forEach { e ->
            val isMe = myResult?.rank == e.rank && myResult?.name == e.name
            val top = e.rank <= 10
            Surface(
                color = if (isMe) CardBgAlt else CardBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
                    .then(if (isMe) Modifier.border(1.dp, AccentYellow.copy(alpha = 0.5f), RoundedCornerShape(10.dp)) else Modifier)
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "#${e.rank}",
                        style = glowText(
                            when {
                                e.rank == 1 -> AccentYellow
                                e.rank <= 3 -> Color(0xFFBFC9D4)
                                e.rank <= 10 -> AccentBlue
                                else -> TextSecondary
                            },
                            13.sp, FontWeight.Black, if (e.rank <= 3) 8f else 0f,
                        ),
                        modifier = Modifier.width(52.dp)
                    )
                    if (top && e.card != null) {
                        RemoteImage(
                            url = "https://media.valorant-api.com/playercards/${e.card}/smallart.png",
                            contentDescription = e.name,
                            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, DividerColor, RoundedCornerShape(6.dp)),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${e.name}#${e.tag}",
                                color = if (isMe) AccentYellow else TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (isMe) FontWeight.Bold else FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text("${e.rr ?: 0}", style = glowText(AccentYellow, 12.sp, FontWeight.Bold, 4f), modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
                    Text("${e.wins ?: 0}W", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
                }
            }
        }
    }
}

// ---------- Store (global offers) ----------

private data class StoreItemUI(val name: String, val icon: String?, val cost: Int?)

@Composable
private fun StoreTab(apiKey: String, region: String) {
    val scope = rememberCoroutineScope()

    // ---------- your personal daily shop (Riot login) ----------
    var phase by remember { mutableStateOf(ShopPhase.BOOT) }
    var riotUser by remember { mutableStateOf("") }
    var riotPass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var mfaCode by remember { mutableStateOf("") }
    var mfaEmail by remember { mutableStateOf<String?>(null) }
    var riotErr by remember { mutableStateOf<String?>(null) }
    var shopItems by remember { mutableStateOf<List<RiotShopItem>?>(null) }
    var refreshInSec by remember { mutableStateOf<Int?>(null) }
    var shopBusy by remember { mutableStateOf(false) }

    fun openShop() {
        shopBusy = true
        scope.launch {
            val res = RiotStore.loadShop(region, apiKey)
            shopBusy = false
            val shop = res.getOrNull()
            if (shop == null) {
                riotErr = res.exceptionOrNull()?.message ?: "Store unavailable"
                phase = ShopPhase.LOGIN // tokens may be stale → ask for a fresh login
            } else {
                shopItems = shop.items
                refreshInSec = shop.refreshInSec
                riotErr = null
                phase = ShopPhase.SHOP
            }
        }
    }

    fun handleRiot(st: RiotState) {
        when (st.phase) {
            RiotPhase.READY -> openShop()
            RiotPhase.MFA -> { mfaEmail = st.email; phase = ShopPhase.MFA }
            RiotPhase.NEED_CREDS -> phase = ShopPhase.LOGIN
            RiotPhase.FAILED -> { riotErr = st.message ?: "Riot login failed"; phase = ShopPhase.LOGIN }
        }
    }

    fun doLogin() {
        if (riotUser.isBlank() || riotPass.isBlank()) { riotErr = "Enter your Riot username and password"; return }
        riotErr = null
        phase = ShopPhase.LOADING
        scope.launch { handleRiot(RiotStore.login(riotUser.trim(), riotPass)) }
    }

    fun doMfa() {
        if (mfaCode.isBlank()) { riotErr = "Enter the code Riot emailed you"; return }
        riotErr = null
        phase = ShopPhase.LOADING
        scope.launch {
            val st = RiotStore.submitMfa(mfaCode.trim())
            if (st.phase == RiotPhase.MFA) {
                // Riot bounced us back to the code screen → the code was wrong.
                riotErr = "That code didn't work — check your email and try again"
                mfaEmail = st.email ?: mfaEmail
                phase = ShopPhase.MFA
            } else handleRiot(st)
        }
    }

    LaunchedEffect(region) {
        if (phase == ShopPhase.BOOT) {
            phase = ShopPhase.LOADING
            handleRiot(RiotStore.authorize())
        }
    }

    SectionTitle("YOUR DAILY SHOP", trailing = { Text(region.uppercase(), color = TextSecondary, fontSize = 12.sp) })

    when (phase) {
        ShopPhase.BOOT, ShopPhase.LOADING -> {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = ValorantRed)
            }
        }
        ShopPhase.LOGIN -> {
            Surface(color = CardBg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Riot account login", style = glowText(TextPrimary, 15.sp, FontWeight.SemiBold, 4f))
                    Text(
                        "Signed in through Riot's official authorization server (auth.riotgames.com). Credentials are used only for this login — nothing is stored or sent anywhere else.",
                        color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp,
                    )
                    OutlinedTextField(
                        value = riotUser, onValueChange = { riotUser = it },
                        label = { Text("Riot username", color = TextSecondary) },
                        textStyle = TextStyle(color = TextPrimary),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = riotPass, onValueChange = { riotPass = it },
                        label = { Text("Password", color = TextSecondary) },
                        textStyle = TextStyle(color = TextPrimary),
                        singleLine = true,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Text(if (showPass) "Hide" else "Show", color = AccentBlue,
                                modifier = Modifier.padding(end = 8.dp, start = 4.dp).clickableSafe { showPass = !showPass })
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (riotErr != null) {
                        Text(riotErr!!, color = ValorantRed, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { doLogin() },
                        colors = ButtonDefaults.buttonColors(containerColor = ValorantRed, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().edgeGlow(ValorantRed, 14.dp, 1.dp, 0.35f),
                    ) {
                        Text("Log in & load shop", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
        ShopPhase.MFA -> {
            Surface(color = CardBg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Two-factor code", style = glowText(TextPrimary, 15.sp, FontWeight.SemiBold, 4f))
                    Text(
                        mfaEmail?.let { "Riot sent a 6-digit code to $it. Enter it below." }
                            ?: "Riot sent a 6-digit code to your email. Enter it below.",
                        color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp,
                    )
                    OutlinedTextField(
                        value = mfaCode, onValueChange = { mfaCode = it },
                        label = { Text("6-digit code", color = TextSecondary) },
                        textStyle = TextStyle(color = TextPrimary),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    if (riotErr != null) {
                        Text(riotErr!!, color = ValorantRed, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { doMfa() },
                        colors = ButtonDefaults.buttonColors(containerColor = ValorantRed, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Verify", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
        ShopPhase.SHOP -> {
            val loaded = shopItems
            if (loaded == null || loaded.isEmpty()) {
                Surface(color = CardBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Shop couldn't be loaded: ${riotErr ?: "unknown error"}", color = ValorantRed, fontSize = 12.sp)
                        Text("Check that the region below matches your Riot account, then tap Retry.", color = TextSecondary, fontSize = 11.sp)
                        Text("Retry", color = AccentBlue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp).clickableSafe {
                                riotErr = null; phase = ShopPhase.LOADING; RiotStore.reset(); openShop()
                            })
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Refreshes in", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                    refreshInSec?.let { s ->
                        Text("${s / 3600}h ${(s % 3600) / 60}m", style = glowText(AccentYellow, 13.sp, FontWeight.Bold, 5f))
                    }
                    Spacer(Modifier.weight(1f))
                    if (shopBusy) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = ValorantRed)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        "Log out", color = TextSecondary, fontSize = 12.sp,
                        modifier = Modifier.clickableSafe {
                            RiotStore.reset(); shopItems = null; refreshInSec = null
                            riotUser = ""; riotPass = ""; mfaCode = ""; mfaEmail = null
                            riotErr = null; phase = ShopPhase.LOGIN
                        },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    loaded.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { item -> ShopItemCard(item, Modifier.weight(1f)) }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    // ---------- global rotation (API key only, no login needed) ----------
    Spacer(Modifier.height(6.dp))
    SectionTitle("GLOBAL OFFERS", trailing = { Text("API rotation", color = TextSecondary, fontSize = 12.sp) })
    GlobalOffersSection(apiKey)
}

@Composable
private fun ShopItemCard(item: RiotShopItem, modifier: Modifier = Modifier) {
    Surface(color = CardBg, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            if (item.skin?.icon != null) {
                RemoteImage(
                    url = item.skin.icon, contentDescription = item.skin.name,
                    modifier = Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                item.skin?.name ?: item.uuid.take(8),
                color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            if (item.cost != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmtComma(item.cost), style = glowText(AccentYellow, 13.sp, FontWeight.Bold, 5f))
                    Spacer(Modifier.width(3.dp))
                    Text("VP", color = TextSecondary, fontSize = 10.sp)
                }
            } else {
                Text("—", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun GlobalOffersSection(apiKey: String) {
    var items by remember { mutableStateOf<List<StoreItemUI>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(apiKey) {
        if (items != null) return@LaunchedEffect
        val offersRes = ValApi.getStoreOffers(apiKey)
        val offers = offersRes.getOrNull()
        if (offers == null) {
            err = offersRes.exceptionOrNull()?.message ?: "Store offers unavailable"
            return@LaunchedEffect
        }
        val skins: Map<String, SkinLevel> = ValApi.loadSkinLevels()
        items = offers.mapNotNull { o ->
            skins[o.itemId]?.let { StoreItemUI(it.name, it.icon, o.cost) }
        }
    }

    if (err != null) {
        Surface(color = CardBg, shape = RoundedCornerShape(12.dp)) {
            Text("Store unavailable: $err", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(14.dp))
        }
    }

    val loaded = items
    if (loaded == null && err == null) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = ValorantRed)
        }
    } else if (loaded != null && loaded.isEmpty() && err == null) {
        Text("No weapon skin offers right now.", color = TextSecondary, fontSize = 12.sp)
    }

    loaded?.forEach { item ->
        Surface(color = CardBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.icon != null) {
                    RemoteImage(url = item.icon, contentDescription = item.name,
                        modifier = Modifier.size(width = 96.dp, height = 52.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(item.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (item.cost != null) {
                        Text("${fmtComma(item.cost)}", style = glowText(AccentYellow, 13.sp, FontWeight.Bold, 5f))
                        Text("VP", color = TextSecondary, fontSize = 10.sp)
                    } else {
                        Text("—", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private enum class ShopPhase { BOOT, LOADING, LOGIN, MFA, SHOP }

// ---------- Match rows & detail ----------

@Composable
private fun MatchRow(m: MatchSummary, onClick: () -> Unit) {
    val me = m.me
    val result = m.result
    val resColor = when (result) { "WIN" -> WinGreen; "LOSS" -> ValorantRed; else -> TextSecondary }
    Surface(
        color = CardBg, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickableSafe { onClick() }
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (m.mapId != null) {
                RemoteImage(url = "https://media.valorant-api.com/maps/${m.mapId}/splash.png", contentDescription = m.map,
                    modifier = Modifier.size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            } else {
                Box(Modifier.size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(8.dp)).background(ValorantDark), contentAlignment = Alignment.Center) {
                    Text(m.map?.take(3)?.uppercase() ?: "?", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(m.map ?: "Unknown Map", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${m.mode ?: ""}  •  ${m.gameStartPatched?.take(16) ?: ""}", color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${me?.agent ?: "—"}  •  ${me?.kills ?: 0}/${me?.deaths ?: 0}/${me?.assists ?: 0}", color = TextSecondary, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(result, style = glowText(resColor, 13.sp, FontWeight.Bold, 5f))
                val (rw, rl) = m.myTeam?.let { (it.roundsWon ?: 0) to (it.roundsLost ?: 0) } ?: (0 to 0)
                Text("$rw-$rl", color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MatchDetailScreen(match: MatchSummary, onBack: () -> Unit) {
    val me = match.me
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ValorantDarker, ValorantDark, Color(0xFF0E1B28))))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(180.dp).background(ValorantDark)) {
                if (match.mapId != null) {
                    RemoteImage(url = "https://media.valorant-api.com/maps/${match.mapId}/splash.png", contentDescription = match.map,
                        modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 1f to ValorantDarker.copy(alpha = 0.9f))))
                Surface(color = Color.Black.copy(alpha = 0.4f), shape = CircleShape, modifier = Modifier.padding(12.dp).size(40.dp).clickableSafe { onBack() }) {
                    Box(contentAlignment = Alignment.Center) { Text("←", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                }
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Text(match.map ?: "Unknown Map", style = glowText(TextPrimary, 24.sp, FontWeight.Bold, 6f))
                    Text("${match.mode ?: ""}  •  ${match.gameStartPatched?.take(20) ?: ""}", color = TextSecondary, fontSize = 13.sp)
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = CardBg, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(match.result, style = glowText(
                            when (match.result) { "WIN" -> WinGreen; "LOSS" -> ValorantRed; else -> TextSecondary }, 20.sp, FontWeight.Black, 8f))
                        Text("${match.myTeam?.roundsWon ?: 0} - ${match.myTeam?.roundsLost ?: 0}", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(match.gameLengthSec?.let { formatDuration(it) } ?: "", color = TextSecondary, fontSize = 13.sp)
                    }
                }

                Text("Your Stats", style = glowText(TextPrimary, 16.sp, FontWeight.SemiBold, 4f))
                Surface(color = CardBg, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatCell("Agent", me?.agent ?: "—", Modifier.weight(1f))
                            StatCell("Score", me?.score?.toString() ?: "—", Modifier.weight(1f))
                            StatCell("K/D/A", "${me?.kills ?: 0}/${me?.deaths ?: 0}/${me?.assists ?: 0}", Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatCell("Headshots", me?.headshots?.toString() ?: "—", Modifier.weight(1f))
                            StatCell("Bodyshots", me?.bodyshots?.toString() ?: "—", Modifier.weight(1f))
                            StatCell("Legshots", me?.legshots?.toString() ?: "—", Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatCell("Damage", me?.damage?.toString() ?: "—", Modifier.weight(1f))
                            StatCell("Rounds", match.roundsPlayed?.toString() ?: "—", Modifier.weight(1f))
                            StatCell("Region", match.region ?: "—", Modifier.weight(1f))
                        }
                        if (me?.agentIcon != null) {
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.size(96.dp).glow(AccentBlue, alpha = 0.35f), contentAlignment = Alignment.Center) {
                                RemoteImage(url = me.agentIcon, contentDescription = me.agent, modifier = Modifier.size(92.dp))
                            }
                        }
                    }
                }

                // Compare you to the lobby average
                if (me != null) {
                    val lobbies = match.players.filter { it.puuid != me.puuid && (it.roundsCount(match) ?: 0) >= 0 }
                    val avgKills = lobbies.mapNotNull { it.kills }.average().takeIf { !it.isNaN() }
                    val avgScore = lobbies.mapNotNull { it.score }.average().takeIf { !it.isNaN() }
                    if (avgKills != null || avgScore != null) {
                        Surface(color = CardBgAlt, shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                if (avgKills != null) {
                                    Column {
                                        Text("Kills vs lobby", color = TextSecondary, fontSize = 10.sp)
                                        Text(fmtSigned(me.kills?.toFloat(), avgKills.toFloat()), style = glowText(if (me.kills ?: 0 >= avgKills) WinGreen else ValorantRed, 14.sp, FontWeight.Bold, 5f))
                                    }
                                }
                                if (avgScore != null) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Score vs lobby avg", color = TextSecondary, fontSize = 10.sp)
                                        Text(fmtSigned(me.score?.toFloat(), avgScore.toFloat()), style = glowText(if (me.score ?: 0 >= avgScore) WinGreen else ValorantRed, 14.sp, FontWeight.Bold, 5f))
                                    }
                                }
                            }
                        }
                    }
                }

                // All players grouped by team — winning team first, players sorted by combat score
                Text("All Players", style = glowText(TextPrimary, 16.sp, FontWeight.SemiBold, 4f))
                Text("Winning team first  •  players sorted by combat score", color = TextSecondary, fontSize = 10.sp)

                val teamSections = match.teams.sortedBy { if (it.won == true) 0 else 1 }
                val seen = HashSet<String>()
                teamSections.forEach { team ->
                    val members = match.players
                        .filter { it.teamId != null && it.teamId == team.teamId && !seen.contains(playerKey(it)) }
                        .sortedByDescending { it.score ?: 0 }
                    if (members.isNotEmpty()) {
                        val isWinner = team.won == true
                        val resultCol = when (team.won) { true -> WinGreen; false -> ValorantRed; else -> TextSecondary }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .width(4.dp)
                                        .height(16.dp)
                                        .background(Brush.verticalGradient(listOf(resultCol, resultCol.copy(alpha = 0.4f))))
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    (team.teamId ?: "Team").replaceFirstChar { c -> c.uppercase() },
                                    style = glowText(resultCol, 14.sp, FontWeight.Black, 5f),
                                )
                                if (isWinner) {
                                    Spacer(Modifier.width(8.dp))
                                    Text("WINNING TEAM", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (team.won != null) {
                                    Text(if (isWinner) "WIN" else "LOSS", style = glowText(resultCol, 11.sp, FontWeight.Bold, 4f))
                                }
                                Text("${team.roundsWon ?: 0}-${team.roundsLost ?: 0}", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        members.forEach { p ->
                            seen.add(playerKey(p))
                            PlayerRow(p, isMe = p.puuid != null && p.puuid == match.playerPuuid, teamColor = resultCol)
                        }
                    }
                }
                val orphans = match.players.filter { !seen.contains(playerKey(it)) }.sortedByDescending { it.score ?: 0 }
                if (orphans.isNotEmpty()) {
                    orphans.forEach { p ->
                        PlayerRow(p, isMe = p.puuid != null && p.puuid == match.playerPuuid, teamColor = TextSecondary)
                    }
                }

                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

private fun PlayerMatchStats.roundsCount(match: MatchSummary): Int? = match.roundsPlayed
private fun fmtSigned(mine: Float?, avg: Float): String =
    if (mine == null) "—" else {
        val d = mine - avg
        (if (d >= 0) "+" else "") + String.format("%.1f", d) + " vs " + String.format("%.1f", avg)
    }

private fun playerKey(p: PlayerMatchStats): String = p.puuid ?: ("${p.name}|${p.tag}")

@Composable
private fun PlayerRow(p: PlayerMatchStats, isMe: Boolean, teamColor: Color = TextSecondary) {
    Surface(
        color = if (isMe) CardBgAlt else CardBg,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
            .edgeGlow(teamColor, 10.dp, 2.dp, 0.30f)
            .then(if (isMe) Modifier.border(1.dp, ValorantRed.copy(alpha = 0.6f), RoundedCornerShape(10.dp)) else Modifier)
    ) {
        Row(
            Modifier.fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(teamColor.copy(alpha = 0.14f), teamColor.copy(alpha = 0.03f), Color.Transparent),
                        startX = 0f,
                    )
                )
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (p.agentIcon != null) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    RemoteImage(url = p.agentIcon, contentDescription = p.agent, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                }
                Spacer(Modifier.width(8.dp))
            } else {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(ValorantDark), contentAlignment = Alignment.Center) {
                    Text(p.agent?.take(3)?.uppercase() ?: "?", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${p.name ?: "?"}#${p.tag ?: ""}", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (isMe) {
                        Spacer(Modifier.width(6.dp))
                        Text("YOU", style = glowText(ValorantRed, 9.sp, FontWeight.Black, 4f))
                    }
                }
                Text(p.agent ?: "—", color = TextSecondary, fontSize = 11.sp)
                Text("HS ${p.headshots ?: 0}  BS ${p.bodyshots ?: 0}  LS ${p.legshots ?: 0}  DMG ${p.damage ?: 0}", color = TextSecondary, fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${p.kills ?: 0}/${p.deaths ?: 0}/${p.assists ?: 0}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("score ${p.score ?: 0}", color = TextSecondary, fontSize = 9.sp)
            }
        }
    }
}

// ---------- Tracker.gg-style building blocks ----------

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(18.dp)
                    .background(Brush.verticalGradient(listOf(ValorantRed, ValorantRed.copy(alpha = 0.35f))))
            )
            Spacer(Modifier.width(9.dp))
            Text(text, style = glowText(TextPrimary, 15.sp, FontWeight.Black, 5f), letterSpacing = 0.5.sp)
        }
        if (trailing != null) trailing()
    }
}

@Composable
private fun HighlightStat(label: String, value: String, modifier: Modifier = Modifier, accent: Color = ValorantRed) {
    Surface(color = CardBgAlt, shape = RoundedCornerShape(10.dp), modifier = modifier) {
        Row {
            Box(Modifier.width(4.dp).height(52.dp).background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.45f)))))
            Column(Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 6.dp)) {
                Text(label, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, style = glowText(TextPrimary, 17.sp, FontWeight.Bold, 6f))
            }
        }
    }
}

@Composable
private fun ValueCell(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Row(modifier) {
        Box(Modifier.width(3.dp).height(34.dp).background(accent.copy(alpha = 0.85f)))
        Column(Modifier.padding(start = 8.dp)) {
            Text(label, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = glowText(TextPrimary, 15.sp, FontWeight.Bold, 5f))
        }
    }
}

@Composable
private fun WinLossDonut(wins: Int, losses: Int) {
    val total = (wins + losses).coerceAtLeast(1)
    val wAngle = 360f * wins / total
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(72.dp)) {
            val strokeW = 9.dp.toPx()
            val inset = strokeW / 2 + 2f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(
                color = WinGreen.copy(alpha = 0.22f),
                startAngle = -90f, sweepAngle = wAngle, useCenter = false,
                style = Stroke(width = strokeW * 2.0f, cap = StrokeCap.Round),
                topLeft = Offset(inset, inset), size = arcSize,
            )
            drawArc(
                color = TextSecondary.copy(alpha = 0.15f),
                startAngle = -90f + wAngle, sweepAngle = 360f - wAngle, useCenter = false,
                style = Stroke(width = strokeW * 2.0f, cap = StrokeCap.Round),
                topLeft = Offset(inset, inset), size = arcSize,
            )
            drawArc(
                color = ValorantDark,
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style = Stroke(width = strokeW * 2.0f, cap = StrokeCap.Round),
                topLeft = Offset(inset, inset), size = arcSize,
            )
            drawArc(
                color = WinGreen,
                startAngle = -90f, sweepAngle = wAngle, useCenter = false,
                style = Stroke(width = strokeW, cap = StrokeCap.Butt),
                topLeft = Offset(inset, inset), size = arcSize,
            )
            drawArc(
                color = TextSecondary.copy(alpha = 0.9f),
                startAngle = -90f + wAngle, sweepAngle = 360f - wAngle, useCenter = false,
                style = Stroke(width = strokeW, cap = StrokeCap.Butt),
                topLeft = Offset(inset, inset), size = arcSize,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$wins W", style = glowText(WinGreen, 12.sp, FontWeight.Bold, 6f))
            Text("$losses L", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AccuracyRow(label: String, hits: Int, pct: Float, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.width(48.dp))
        Text(fmt1(pct) + "%", style = glowText(color, 15.sp, FontWeight.Bold, 6f), modifier = Modifier.width(64.dp))
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(ValorantDark)) {
            val frac = (pct / 100f).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .height(8.dp)
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.65f), color)))
            )
        }
        Spacer(Modifier.width(10.dp))
        Text("$hits Hits", color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun PreloadScreen(progress: Float) {
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(ValorantDarker, ValorantDark, Color(0xFF111D27)))
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("ValoTrak", style = glowText(ValorantRed, 34.sp, FontWeight.Black, 14f))
            Text("Preparing assets...", color = TextSecondary, fontSize = 14.sp)
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(64.dp),
                color = ValorantRed,
                strokeWidth = 4.dp,
            )
            Text("${(progress * 100).toInt()}%", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Downloading maps, agents & ranks for offline use", color = TextSecondary, fontSize = 11.sp)
        }
    }
}

// ---------- Charts ----------

@Composable
private fun TrendChart(values: List<Int>, modifier: Modifier = Modifier) {
    if (values.size < 2) return
    val minV = values.minOrNull() ?: 0
    val maxV = values.maxOrNull() ?: 0
    val range = (maxV - minV).coerceAtLeast(1)
    Column(modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            val pad = 6f
            val w = size.width - pad * 2
            val h = size.height - pad * 2
            val stepX = w / (values.size - 1)

            listOf(0f, 0.5f, 1f).forEach { f ->
                val y = pad + f * h
                drawLine(
                    color = TextSecondary.copy(alpha = 0.25f),
                    start = Offset(pad, y), end = Offset(pad + w, y), strokeWidth = 1f,
                )
            }

            val path = Path()
            values.forEachIndexed { i, v ->
                val x = pad + i * stepX
                val y = pad + h - ((v - minV).toFloat() / range) * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val area = Path().apply {
                addPath(path)
                lineTo(pad + w, pad + h)
                lineTo(pad, pad + h)
                close()
            }
            drawPath(area, brush = Brush.verticalGradient(listOf(WinGreen.copy(alpha = 0.28f), WinGreen.copy(alpha = 0.02f)), startY = 0f, endY = size.height))
            drawPath(path, color = TextPrimary.copy(alpha = 0.12f), style = Stroke(width = 10f, cap = StrokeCap.Round))
            drawPath(path, color = TextPrimary.copy(alpha = 0.20f), style = Stroke(width = 6f, cap = StrokeCap.Round))
            drawPath(path, color = TextPrimary, style = Stroke(width = 3f, cap = StrokeCap.Round))
            values.forEachIndexed { i, v ->
                val x = pad + i * stepX
                val y = pad + h - ((v - minV).toFloat() / range) * h
                drawCircle(color = TextPrimary.copy(alpha = 0.22f), radius = 7f, center = Offset(x, y))
                drawCircle(color = TextPrimary, radius = 3f, center = Offset(x, y))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("min $minV", color = TextSecondary, fontSize = 10.sp)
            Text("max $maxV", color = TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun BarChart(
    values: List<Float>,
    colors: List<Color> = values.map { if (it >= 0) WinGreen else ValorantRed },
    modifier: Modifier = Modifier,
    maxValue: Float? = null,
    label: String = "",
) {
    val max = maxValue ?: (values.maxOrNull() ?: 1f)
    val min = if (maxValue == null) (values.minOrNull() ?: 0f) else 0f
    val lo = min.coerceAtMost(0f)
    val hi = max.coerceAtLeast(0f)
    val range = (hi - lo).coerceAtLeast(1f)
    Column(modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            val padLeft = 8f
            val padBottom = 8f
            val w = size.width - padLeft
            val h = size.height - padBottom
            val barWidth = w / (values.size.coerceAtLeast(1) * 1.4f)
            val gap = barWidth * 0.4f
            val zero = h - ((0f - lo) / range) * h
            values.forEachIndexed { i, v ->
                val x = padLeft + i * (barWidth + gap)
                val y = h - ((v - lo) / range) * h
                val top = if (v >= 0) y else zero
                val barH = kotlin.math.abs(y - zero)
                val color = colors.getOrElse(i) { if (v >= 0) WinGreen else ValorantRed }
                drawRect(color = color, topLeft = Offset(x, top), size = Size(barWidth, barH.coerceAtLeast(2f)))
            }
            drawLine(
                color = TextSecondary.copy(alpha = 0.4f),
                start = Offset(padLeft, zero), end = Offset(padLeft + w, zero), strokeWidth = 1f,
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextSecondary, fontSize = 11.sp)
    }
}

// ---------- Helpers ----------

private fun formatDuration(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun fmt1(x: Float): String = String.format("%.1f", x)
private fun fmt2(x: Float): String = String.format("%.2f", x)
private fun fmtComma(n: Int): String = String.format("%,d", n)
private fun fmtComma(n: Int?): String = if (n == null) "—" else String.format("%,d", n)

private fun Modifier.clickableSafe(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
