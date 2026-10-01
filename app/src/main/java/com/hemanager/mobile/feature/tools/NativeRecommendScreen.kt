@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.hemanager.mobile.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hemanager.mobile.ui.theme.GeistMono
import com.hemanager.mobile.ui.theme.HeColors
import com.hemanager.mobile.ui.theme.NotoSansSC
import com.hemanager.mobile.ui.theme.Oxanium
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun NativeRecommendScreen(
    server: String,
    token: String,
    isAdmin: Boolean,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val state = rememberNativeActions(server, token, onLogout)
    val context = LocalContext.current
    val store = remember(server) { NativeJobStore(context, server) }
    var queryText by rememberSaveable { mutableStateOf("") }
    var preferred by rememberSaveable { mutableStateOf("") }
    var avoided by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableStateOf("12") }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var ai by remember { mutableStateOf<JSONObject?>(null) }
    var model by rememberSaveable { mutableStateOf("") }
    var base by rememberSaveable { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf<JSONObject?>(null) }
    var metadata by remember { mutableStateOf<JSONObject?>(null) }
    var profileJob by remember { mutableStateOf<JSONObject?>(null) }
    var metadataJob by remember { mutableStateOf<JSONObject?>(null) }
    var detail by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }

    suspend fun loadStats() {
        profile = state.api.get("/recommend/manga-profiles/stats")
        metadata = state.api.get("/recommend/manga-metadata/stats")
    }

    suspend fun load() {
        ai = state.api.get("/ai/recommendations/status")
        model = ai!!.text("model")
        base = ai!!.text("base_url")
        loadStats()
    }

    LaunchedEffect(Unit) {
        state.run {
            load()
            if (isAdmin) {
                listOf("profiles", "metadata").forEach { kind ->
                    val id = store.get("recommend:$kind")
                    if (id.isNotBlank()) runCatching {
                        val job = state.api.get("/recommend/manga-$kind/jobs/${encoded(id)}")
                        if (kind == "profiles") profileJob = job else metadataJob = job
                    }
                }
            }
        }
    }

    NativePoll(Unit, activeJob(profileJob) || activeJob(metadataJob)) {
        try {
            val before = activeJob(profileJob) || activeJob(metadataJob)
            if (activeJob(profileJob)) profileJob = state.api.get("/recommend/manga-profiles/jobs/${encoded(profileJob!!.text("job_id"))}")
            if (activeJob(metadataJob)) metadataJob = state.api.get("/recommend/manga-metadata/jobs/${encoded(metadataJob!!.text("job_id"))}")
            if (before && !activeJob(profileJob) && !activeJob(metadataJob)) loadStats()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state.error = e.message ?: "分析任务更新失败"
        }
    }

    if (detail > 0) {
        NativeMediaDetail(server, token, detail, { detail = 0 }, onLogout)
        return
    }

    NativeScene("漫画推荐", state, onBack, { state.run { load() } }, subtitle = "// AI RECOMMENDATION ENGINE") {
        // AI Status Indicator Box
        item {
            val isAiConfigured = ai?.optBoolean("deepseek_configured") == true
            val statusAccent = if (isAiConfigured) HeColors.MangaAccent else HeColors.Yellow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(statusAccent.copy(alpha = 0.10f))
                    .border(1.dp, statusAccent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isAiConfigured) Icons.Default.AutoAwesome else Icons.Default.Psychology,
                            contentDescription = null,
                            tint = statusAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isAiConfigured) "AI 推荐已配置 (DeepSeek)" else "使用本地规则推荐",
                            color = HeColors.OpWhite,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        )
                    }

                    Text(
                        text = if (isAiConfigured) "ONLINE" else "LOCAL",
                        fontFamily = Oxanium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = statusAccent
                    )
                }
            }
        }

        item {
            NativeSection("描述你想看的作品") {
                NativeField("偏好、题材、画风或剧情", queryText, { queryText = it }, enabled = !state.busy, multiline = true)
                NativeField("优先标签（逗号分隔）", preferred, { preferred = it }, enabled = !state.busy)
                NativeField("排除标签（逗号分隔）", avoided, { avoided = it }, enabled = !state.busy)
                NativeField("推荐数量（1–50）", limit, { limit = it }, enabled = !state.busy)

                Button(
                    onClick = {
                        state.run {
                            val n = limit.toIntOrNull()
                            require(n != null && n in 1..50 && queryText.trim().length in 1..4000) { "请填写偏好，并将数量设为 1–50" }
                            fun tags(text: String) = JSONArray(text.split(',', '，', '\n').map { it.trim() }.filter { it.isNotBlank() })
                            result = state.api.send(
                                "/recommend/manga",
                                obj(
                                    "query" to queryText.trim(),
                                    "limit" to n,
                                    "preferred_tags" to tags(preferred),
                                    "avoid_tags" to tags(avoided)
                                )
                            )
                        }
                    },
                    enabled = !state.busy && queryText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HeColors.Yellow,
                        contentColor = HeColors.OnYellow
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (result == null) "生成推荐" else "重新推荐", fontWeight = FontWeight.Bold)
                }
            }
        }

        result?.let { r ->
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(HeColors.Void.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(HeColors.MangaAccent))
                        Text(
                            text = "候选 ${r.optInt("candidate_count")} 个 · ${if (r.optBoolean("ai_enabled")) "AI 推荐" else "本地推荐"}",
                            fontFamily = GeistMono,
                            color = HeColors.OpWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            if (r.text("message").isNotBlank()) {
                item {
                    Text(r.text("message"), color = HeColors.OpWhiteSoft, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }

            items(r.optJSONArray("recommendations").objects()) { recommendation ->
                recommendation.optJSONObject("media")?.let { m ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(HeColors.PanelGradient)
                            .border(1.dp, HeColors.HairlineRim, RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        NativeMediaRow(m, state.api) { detail = m.optInt("id") }

                        // Recommendation Reason Callout
                        if (recommendation.text("reason").isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(HeColors.Void.copy(alpha = 0.45f))
                                    .border(1.dp, HeColors.MangaAccent.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = HeColors.MangaAccent,
                                        modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                    )
                                    Text(
                                        text = recommendation.text("reason"),
                                        color = HeColors.OpWhite,
                                        style = MaterialTheme.typography.bodySmall,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }

                        // Matched Tags chips
                        val tags = recommendation.optJSONArray("matched_tags")
                        if (tags != null && tags.length() > 0) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (i in 0 until tags.length()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(HeColors.Yellow.copy(alpha = 0.12f))
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = tags.optString(i),
                                            color = HeColors.Yellow,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isAdmin) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = { settings = !settings }) {
                        Text(
                            if (settings) "收起推荐设置" else "推荐设置与分析任务",
                            color = HeColors.Yellow,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (settings) {
                item {
                    NativeSection("AI 配置") {
                        NativeField("模型", model, { model = it }, enabled = !state.busy)
                        NativeField("API 地址", base, { base = it }, enabled = !state.busy)
                        NativeField("API Key（留空保留）", apiKey, { apiKey = it }, secret = true, enabled = !state.busy)
                        Text(
                            text = if (ai?.optBoolean("key_saved") == true) "服务器已保存密钥" else if (ai?.optBoolean("env_key_present") == true) "使用服务器环境密钥" else "未配置密钥",
                            color = HeColors.OpWhiteSoft,
                            fontSize = 12.5.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    state.run {
                                        state.api.send(
                                            "/ai/recommendations/config",
                                            obj("model" to model.trim(), "base_url" to base.trim(), "api_key" to apiKey.trim().ifBlank { null }),
                                            "PUT"
                                        )
                                        apiKey = ""
                                        load()
                                        state.message = "AI 配置已保存"
                                    }
                                },
                                enabled = !state.busy,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("保存 AI 配置", fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = {
                                    state.confirm("清除服务器中保存的 API 密钥？环境密钥仍由服务器管理。") {
                                        state.api.send("/ai/recommendations/config", obj("clear_api_key" to true), "PUT")
                                        load()
                                    }
                                },
                                enabled = !state.busy
                            ) {
                                Text("清除已保存密钥", color = HeColors.OpWhiteSoft)
                            }
                        }
                    }
                }

                listOf("metadata" to "标题与标签元数据", "profiles" to "视觉语义画像").forEach { (kind, label) ->
                    val stats = if (kind == "profiles") profile else metadata
                    val job = if (kind == "profiles") profileJob else metadataJob
                    item {
                        NativeSection(label) {
                            Text(
                                "漫画 ${stats?.optInt("total_manga") ?: 0} · 已分析 ${stats?.optInt("profiled") ?: 0} · 待补 ${stats?.optInt("missing") ?: 0} · 过期 ${stats?.optInt("stale") ?: 0}",
                                fontFamily = GeistMono,
                                color = HeColors.OpWhiteSoft,
                                fontSize = 12.5.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(false to "分析未完成作品", true to "重新分析作品").forEach { (force, text) ->
                                    Button(
                                        onClick = {
                                            state.confirm("$text？${if (kind == "profiles") "本次最多 50 个作品，每部抽样 10 页。" else "本次最多 200 个作品。"}") {
                                                val body = obj("limit" to if (kind == "profiles") 50 else 200, "force" to force)
                                                if (kind == "profiles") body.put("sample_count", 10)
                                                val started = state.api.send("/recommend/manga-$kind/analyze", body)
                                                store.set("recommend:$kind", started.text("job_id"))
                                                if (kind == "profiles") profileJob = started else metadataJob = started
                                            }
                                        },
                                        enabled = !state.busy && !activeJob(job),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(text)
                                    }
                                }
                            }
                        }
                    }
                    item { JobStatus(job) }
                }
            }
        }
    }
}
