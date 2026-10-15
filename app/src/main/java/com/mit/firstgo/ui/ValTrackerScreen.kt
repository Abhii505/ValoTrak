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
import com.mit.firstgo.data.MmrInfo
import com.mit.firstgo.data.ValApi
import kotlinx.coroutines.launch

private val ValorantRed = Color(0xFFFF4655)
private val ValorantDark = Color(0xFF0F1923)
private val ValorantDarker = Color(0xFF050A10)
private val CardBg = Color(0xFF1B2735)
private val TextPrimary = Color(0xFFF9F9F9)
private val TextSecondary = Color(0xFF8B9BB4)

@Composable
fun ValTrackerScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("valorant_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var riotId by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("eu") }
    var showKey by remember { mutableStateOf(false) }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var account by remember { mutableStateOf<AccountInfo?>(null) }
    var mmr by remember { mutableStateOf<MmrInfo?>(null) }
    var history by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }

    fun parseRiotId(s: String): Pair<String, String>? {
        val idx = s.lastIndexOf('#')
        if (idx <= 0 || idx == s.length - 1) return null
        return s.substring(0, idx).trim() to s.substring(idx + 1).trim()
    }

    val bannerUrl = account?.cardId?.let { "https://media.valorant-api.com/playercards/$it/wideart.png" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ValorantDarker, ValorantDark, Color(0xFF111D27))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---------- Header banner ----------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(ValorantDark),
            ) {
                if (bannerUrl != null) {
                    RemoteImage(
                        url = bannerUrl,
                        contentDescription = "Player card banner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                }
                // dark scrim so text is readable
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to ValorantDarker.copy(alpha = 0.95f)
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp),
                ) {
                    Text(
                        text = account?.let { "${it.name}#${it.tag}" } ?: "Valorant Progress Tracker",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = account?.level?.let { "Account Level $it" }
                            ?: "Track your rank, ELO and recent games",
                        color = TextSecondary,
                        fontSize = 13.sp,
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // ---------- Input card ----------
                Surface(
                    color = CardBg.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Setup",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("HenrikDev API Key", color = TextSecondary) },
                            textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary),
                            singleLine = true,
                            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                Text(
                                    text = if (showKey) "Hide" else "Show",
                                    color = ValorantRed,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .padding(4.dp)
                                        .clickableSafe { showKey = !showKey },
                                )
                            },
                        )
                        OutlinedTextField(
                            value = riotId,
                            onValueChange = { riotId = it },
                            label = { Text("RIOT ID (Name#TAG)", color = TextSecondary) },
                            textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = region,
                                onValueChange = { region = it },
                                label = { Text("Region", color = TextSecondary) },
                                textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = {
                                    val parsed = parseRiotId(riotId)
                                    if (parsed == null) {
                                        error = "Enter a valid RIOT ID like Name#TAG"
                                        return@Button
                                    }
                                    val (name, tag) = parsed
                                    loading = true
                                    error = null
                                    prefs.edit().putString("api_key", apiKey.trim()).apply()
                                    scope.launch {
                                        val accRes = ValApi.getAccount(name, tag, apiKey)
                                        val mmrRes = ValApi.getMmr(region.trim(), name, tag, apiKey)
                                        val histRes = ValApi.getMmrHistory(region.trim(), "pc", name, tag, apiKey)
                                        account = accRes.getOrNull()
                                        mmr = mmrRes.getOrNull()
                                        history = histRes.getOrNull() ?: emptyList()
                                        val failures = listOf(accRes, mmrRes, histRes).filter { it.isFailure }
                                        if (account == null && mmr == null && history.isEmpty()) {
                                            error = failures.firstOrNull()?.exceptionOrNull()?.message
                                                ?: "No data returned"
                                        } else if (failures.isNotEmpty()) {
                                            error = failures.first().exceptionOrNull()?.message
                                        }
                                        loading = false
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(56.dp),
                                enabled = !loading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ValorantRed,
                                    contentColor = Color.White,
                                ),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White,
                                    )
                                } else {
                                    Text("Fetch", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (error != null) {
                    Surface(
                        color = ValorantRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = "⚠  $error",
                            color = ValorantRed,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 13.sp,
                        )
                    }
                }

                // ---------- Rank card ----------
                if (mmr != null || account != null) {
                    Surface(
                        color = CardBg,
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 4.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // rank image
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(ValorantDark),
                                contentAlignment = Alignment.Center,
                            ) {
                                val img = mmr?.rankImage
                                if (img != null) {
                                    RemoteImage(
                                        url = img,
                                        contentDescription = "Rank icon",
                                        modifier = Modifier.size(84.dp),
                                    )
                                } else {
                                    Text("?", color = TextSecondary, fontSize = 32.sp)
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = mmr?.tier ?: "Unknown",
                                    color = TextPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (mmr?.rr != null) {
                                    Text(
                                        "Rank Rating: ${mmr!!.rr} RR",
                                        color = TextSecondary,
                                        fontSize = 13.sp,
                                    )
                                }
                                if (mmr?.elo != null) {
                                    Text(
                                        "ELO: ${mmr!!.elo}",
                                        color = TextSecondary,
                                        fontSize = 13.sp,
                                    )
                                }
                                if (mmr?.lastChange != null) {
                                    val lc = mmr!!.lastChange!!
                                    val sign = if (lc >= 0) "+" else ""
                                    val col = if (lc >= 0) Color(0xFF2ED573) else ValorantRed
                                    Text(
                                        "Last match: $sign$lc RR",
                                        color = col,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                mmr?.highestRank?.let {
                                    Text(
                                        "Peak: $it",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                // ---------- History ----------
                if (history.isNotEmpty()) {
                    Text(
                        "Recent Competitive Games",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    history.forEach { h ->
                        Surface(
                            color = CardBg,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (h.mapId != null) {
                                    RemoteImage(
                                        url = "https://media.valorant-api.com/maps/${h.mapId}/splash.png",
                                        contentDescription = h.map,
                                        modifier = Modifier
                                            .size(width = 72.dp, height = 44.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 72.dp, height = 44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(ValorantDark),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            h.map?.take(3)?.uppercase() ?: "?",
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        h.map ?: "Unknown Map",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        h.date?.take(10) ?: "",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                    )
                                    if (h.tier != null) {
                                        Text(
                                            h.tier,
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    if (h.lastChange != null) {
                                        val lc = h.lastChange!!
                                        val sign = if (lc >= 0) "+" else ""
                                        val col = if (lc >= 0) Color(0xFF2ED573) else ValorantRed
                                        Surface(
                                            color = col.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(8.dp),
                                        ) {
                                            Text(
                                                "$sign$lc",
                                                color = col,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                    if (h.elo != null) {
                                        Text(
                                            "${h.elo} ELO",
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
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

// Small helper to avoid clickable import collision (Modifier.clickable exists).
private fun Modifier.clickableSafe(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)