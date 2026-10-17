package com.mit.firstgo.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mit.firstgo.data.AccountInfo
import com.mit.firstgo.data.HistoryEntry
import com.mit.firstgo.data.MatchSummary
import com.mit.firstgo.data.MmrInfo
import com.mit.firstgo.data.ValApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private val ValorantRed = Color(0xFFFF4655)
private val ValorantDark = Color(0xFF0F1923)
private val ValorantDarker = Color(0xFF050A10)
private val CardBg = Color(0xFF1B2735)
private val TextPrimary = Color(0xFFF9F9F9)
private val TextSecondary = Color(0xFF8B9BB4)
private val WinGreen = Color(0xFF2ED573)

@Composable
fun ValTrackerScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("valorant_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

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

    var screen by remember { mutableStateOf("input") } // input | profile | match
    var selectedMatch by remember { mutableStateOf<MatchSummary?>(null) }
    var matchLoading by remember { mutableStateOf(false) }

    fun parseRiotId(s: String): Pair<String, String>? {
        val idx = s.lastIndexOf('#')
        if (idx <= 0 || idx == s.length - 1) return null
        return s.substring(0, idx).trim() to s.substring(idx + 1).trim()
    }

    val bannerUrl = account?.cardId?.let { "https://media.valorant-api.com/playercards/$it/wideart.png" }
    val avatarUrl = account?.cardId?.let { "https://media.valorant-api.com/playercards/$it/smallart.png" }

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
                    val matchDef = async { ValApi.getMatches(region.trim(), "pc", name, tag, 8, apiKey) }
                    val accRes = accDef.await()
                    val mmrRes = mmrDef.await()
                    val histRes = histDef.await()
                    val matchRes = matchDef.await()
                    account = accRes.getOrNull()
                    mmr = mmrRes.getOrNull()
                    history = histRes.getOrNull() ?: emptyList()
                    matches = matchRes.getOrNull() ?: emptyList()
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
                    val matchDef = async { ValApi.getMatches(region.trim(), "pc", name, tag, 8, apiKey) }
                    val accRes = accDef.await()
                    val mmrRes = mmrDef.await()
                    val histRes = histDef.await()
                    val matchRes = matchDef.await()
                    account = accRes.getOrNull() ?: account
                    mmr = mmrRes.getOrNull() ?: mmr
                    history = histRes.getOrNull() ?: history
                    matches = matchRes.getOrNull() ?: matches
                    loading = false
                }
            },
            loading = loading,
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
            Text("ValoTrak", color = ValorantRed, fontSize = 34.sp, fontWeight = FontWeight.Black)
            Text("Valorant Progress Tracker", color = TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))

            Surface(color = CardBg.copy(alpha = 0.9f), shape = RoundedCornerShape(24.dp), tonalElevation = 4.dp) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Setup", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    OutlinedTextField(
                        value = apiKey, onValueChange = onApiKey,
                        label = { Text("HenrikDev API Key", color = TextSecondary) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary),
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            Text(if (showKey) "Hide" else "Show", color = ValorantRed,
                                modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(8.dp)).padding(4.dp)
                                    .clickableSafe { onToggleKey() })
                        }
                    )
                    OutlinedTextField(
                        value = riotId, onValueChange = onRiotId,
                        label = { Text("RIOT ID (Name#TAG)", color = TextSecondary) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = region, onValueChange = onRegion,
                            label = { Text("Region", color = TextSecondary) },
                            textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary),
                            singleLine = true, modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = onFetch, enabled = !loading,
                            modifier = Modifier.weight(1.4f).height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ValorantRed, contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                            else Text("Fetch", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (error != null) {
                Surface(color = ValorantRed.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)) {
                    Text("⚠  $error", color = ValorantRed, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                }
            }

            Text("Get a free HenrikDev key via their Discord #get-a-key", color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ProfileScreen(
    account: AccountInfo?,
    mmr: MmrInfo?,
    history: List<HistoryEntry>,
    matches: List<MatchSummary>,
    bannerUrl: String?,
    avatarUrl: String?,
    region: String,
    onBack: () -> Unit,
    onMatchClick: (MatchSummary) -> Unit,
    onRefresh: () -> Unit,
    loading: Boolean,
) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ValorantDarker, ValorantDark, Color(0xFF111D27))))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Header banner
            Box(Modifier.fillMaxWidth().height(220.dp).background(ValorantDark)) {
                if (bannerUrl != null) {
                    RemoteImage(url = bannerUrl, contentDescription = "banner", modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 1f to ValorantDarker.copy(alpha = 0.95f))))
                // back button
                Surface(
                    color = Color.Black.copy(alpha = 0.4f), shape = CircleShape,
                    modifier = Modifier.padding(12.dp).size(40.dp).clickableSafe { onBack() }
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("←", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                }
                Row(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (avatarUrl != null) {
                        RemoteImage(url = avatarUrl, contentDescription = "avatar",
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, ValorantRed, RoundedCornerShape(12.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        Spacer(Modifier.width(12.dp))
                    }
                    Column {
                        Text(account?.let { "${it.name}#${it.tag}" } ?: "Profile", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(account?.level?.let { "Account Level $it" } ?: "", color = TextSecondary, fontSize = 13.sp)
                    }
                }
                // refresh
                Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                    Surface(color = Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickableSafe { onRefresh() }) {
                        Text(if (loading) "…" else "Refresh", color = TextPrimary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 12.sp)
                    }
                }
            }

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Rank card
                if (mmr != null || account != null) {
                    Surface(color = CardBg, shape = RoundedCornerShape(20.dp), tonalElevation = 4.dp) {
                        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)).background(ValorantDark), contentAlignment = Alignment.Center) {
                                val img = mmr?.rankImage ?: mmr?.tierId?.let { "https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/$it/largeicon.png" }
                                if (img != null) RemoteImage(url = img, contentDescription = "rank", modifier = Modifier.size(84.dp))
                                else Text("?", color = TextSecondary, fontSize = 32.sp)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(mmr?.tier ?: "Unknown", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                if (mmr?.rr != null) Text("Rank Rating: ${mmr!!.rr} RR", color = TextSecondary, fontSize = 13.sp)
                                if (mmr?.elo != null) Text("ELO: ${mmr!!.elo}", color = TextSecondary, fontSize = 13.sp)
                                if (mmr?.lastChange != null) {
                                    val lc = mmr!!.lastChange!!
                                    val sign = if (lc >= 0) "+" else ""
                                    val col = if (lc >= 0) WinGreen else ValorantRed
                                    Text("Last match: $sign$lc RR", color = col, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                mmr?.highestRank?.let { Text("Peak: $it${mmr.peakSeason?.let { s -> " ($s)" } ?: ""}", color = TextSecondary, fontSize = 12.sp) }
                            }
                        }
                    }
                }

                // Time played (from fetched matches)
                val totalSec = matches.mapNotNull { it.gameLengthSec }.sum()
                if (matches.isNotEmpty()) {
                    Surface(color = CardBg, shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Recent play time", color = TextSecondary, fontSize = 12.sp)
                                Text(formatDuration(totalSec), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("${matches.size} matches", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                // Act ranks (dropdown)
                val seasonal = mmr?.seasonal ?: emptyList()
                if (seasonal.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    Surface(color = CardBg, shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().clickableSafe { expanded = !expanded }.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Act Ranks (${seasonal.size})", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (expanded) "▲" else "▼", color = TextSecondary, fontSize = 14.sp)
                            }
                            if (expanded) {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    seasonal.take(12).forEach { s ->
                                        Surface(color = ValorantDark, shape = RoundedCornerShape(10.dp)) {
                                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(s.seasonName ?: s.seasonShort ?: "Act", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                Text("${s.wins}W / ${s.games}G  •  ${s.endTier ?: "—"}", color = TextSecondary, fontSize = 12.sp)
                                                if (s.actWins.isNotEmpty()) {
                                                    Text("Peak: ${s.actWins.last()}", color = ValorantRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Recent matches with mode filter dropdown
                if (matches.isNotEmpty()) {
                    var modeFilter by remember { mutableStateOf("All") }
                    var dropdownOpen by remember { mutableStateOf(false) }
                    val modes = remember(matches) { listOf("All") + matches.mapNotNull { it.mode }.distinct() }
                    val filtered = if (modeFilter == "All") matches else matches.filter { it.mode == modeFilter }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent Matches", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Box {
                            Surface(color = CardBg, shape = RoundedCornerShape(10.dp), modifier = Modifier.clickableSafe { dropdownOpen = !dropdownOpen }) {
                                Text(modeFilter, color = TextPrimary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp)
                            }
                            if (dropdownOpen) {
                                Surface(color = CardBg, shape = RoundedCornerShape(10.dp), tonalElevation = 8.dp, modifier = Modifier.padding(top = 36.dp)) {
                                    Column {
                                        modes.forEach { md ->
                                            Text(md, color = TextPrimary, modifier = Modifier
                                                .clickableSafe { modeFilter = md; dropdownOpen = false }
                                                .padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    filtered.forEach { m ->
                        MatchRow(m, onClick = { onMatchClick(m) })
                    }
                }

                // RR history
                if (history.isNotEmpty()) {
                    Text("RR History", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    history.take(10).forEach { h ->
                        Surface(color = CardBg, shape = RoundedCornerShape(12.dp)) {
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
                                    Text("$sign$lc", color = col, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun MatchRow(m: MatchSummary, onClick: () -> Unit) {
    val me = m.me
    val result = m.result
    val resColor = when (result) { "WIN" -> WinGreen; "LOSS" -> ValorantRed; else -> TextSecondary }
    Surface(color = CardBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickableSafe { onClick() }) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // map splash
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
                Text(result, color = resColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                val (rw, rl) = m.myTeam?.let { (it.roundsWon ?: 0) to (it.roundsLost ?: 0) } ?: (0 to 0)
                Text("$rw-$rl", color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MatchDetailScreen(match: MatchSummary, onBack: () -> Unit) {
    val me = match.me
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ValorantDarker, ValorantDark, Color(0xFF111D27))))) {
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
                    Text(match.map ?: "Unknown Map", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("${match.mode ?: ""}  •  ${match.gameStartPatched?.take(20) ?: ""}", color = TextSecondary, fontSize = 13.sp)
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Result row
                Surface(color = CardBg, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(match.result, color = when (match.result) { "WIN" -> WinGreen; "LOSS" -> ValorantRed; else -> TextSecondary },
                            fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text("${match.myTeam?.roundsWon ?: 0} - ${match.myTeam?.roundsLost ?: 0}", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(match.gameLengthSec?.let { formatDuration(it) } ?: "", color = TextSecondary, fontSize = 13.sp)
                    }
                }

                // Player stats
                Text("Your Stats", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
                            RemoteImage(url = me.agentIcon, contentDescription = me.agent, modifier = Modifier.size(96.dp).align(Alignment.CenterHorizontally))
                        }
                    }
                }

                // All players
                Text("All Players", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                match.players.forEach { p ->
                    Surface(color = CardBg, shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (p.agentIcon != null) {
                                RemoteImage(url = p.agentIcon, contentDescription = p.agent, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(ValorantDark), contentAlignment = Alignment.Center) {
                                    Text(p.agent?.take(3)?.uppercase() ?: "?", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text("${p.name ?: "?"}#${p.tag ?: ""}", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(p.agent ?: "—", color = TextSecondary, fontSize = 11.sp)
                                Text("HS ${p.headshots ?: 0}  BS ${p.bodyshots ?: 0}  LS ${p.legshots ?: 0}  DMG ${p.damage ?: 0}", color = TextSecondary, fontSize = 10.sp)
                            }
                            Text("${p.kills ?: 0}/${p.deaths ?: 0}/${p.assists ?: 0}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
            }
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

private fun formatDuration(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun Modifier.clickableSafe(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)