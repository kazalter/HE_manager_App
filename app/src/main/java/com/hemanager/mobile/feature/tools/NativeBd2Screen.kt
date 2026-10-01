package com.hemanager.mobile.feature.tools

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PlayArrow
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
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

@Composable
internal fun NativeBd2Screen(server: String, token: String, isAdmin: Boolean, onBack: () -> Unit, onLogout: () -> Unit) {
    val state = rememberNativeActions(server, token, onLogout)
    val context = LocalContext.current
    var assets by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var job by remember { mutableStateOf<JSONObject?>(null) }
    var kind by rememberSaveable { mutableStateOf("char") }
    var search by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }

    suspend fun load() {
        val data = state.api.get("/bd2/spine")
        assets = data.optJSONArray("assets").objects()
        if (target.isBlank()) target = data.text("root")
        job = state.api.get("/bd2/spine/download/status")
    }

    LaunchedEffect(Unit) { state.run { load() } }

    NativePoll(Unit, activeJob(job)) {
        try {
            job = state.api.get("/bd2/spine/download/status")
            if (!activeJob(job)) load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state.error = e.message ?: "任务更新失败"
        }
    }

    NativeScene("BD2 动态", state, onBack, { state.run { load() } }, subtitle = "// SPINE 2D ANIMATIONS") {
        item {
            NativeSection("动画资源") {
                NativeChoice(
                    "资源类型",
                    kind,
                    listOf("char" to "角色", "cutscene" to "过场", "illust" to "插画"),
                    !state.busy
                ) { kind = it }

                NativeField("搜索角色或服装", search, { search = it }, enabled = !state.busy)

                // Feature notice banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(HeColors.Void.copy(alpha = 0.5f))
                        .border(1.dp, HeColors.HairlineMid, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Animation,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "本地 Spine 渲染，支持动画、皮肤、暂停、速度、缩放和特效层控制。",
                            color = HeColors.OpWhiteSoft,
                            fontSize = 12.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            state.confirm("清除手机缓存的 BD2 素材？下次打开动画时会重新下载。") {
                                withContext(Dispatchers.IO) {
                                    check(File(context.cacheDir, "bd2").let { !it.exists() || it.deleteRecursively() }) { "缓存清理失败，请重试" }
                                }
                                state.message = "本地素材缓存已清除"
                            }
                        },
                        enabled = !state.busy
                    ) {
                        Icon(Icons.Default.CleaningServices, null, modifier = Modifier.size(15.dp), tint = HeColors.OpWhiteSoft)
                        Spacer(Modifier.width(6.dp))
                        Text("清除本地素材缓存", color = HeColors.OpWhiteSoft, fontSize = 12.5.sp)
                    }
                }
            }
        }

        if (isAdmin) {
            item {
                NativeSection("服务器素材更新") {
                    NativeField("服务器素材目录", target, { target = it }, enabled = !state.busy)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                state.confirm("下载或更新 BD2 资源到服务器目录“$target”？") {
                                    require(target.isNotBlank()) { "请填写服务器目录" }
                                    state.api.send("/bd2/spine/download", obj("target_dir" to target.trim()))
                                    job = state.api.get("/bd2/spine/download/status")
                                }
                            },
                            enabled = !state.busy && !activeJob(job) && target.isNotBlank(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("下载 / 更新素材", fontWeight = FontWeight.Bold)
                        }

                        if (activeJob(job)) {
                            OutlinedButton(
                                onClick = {
                                    state.confirm("取消素材下载？") {
                                        state.api.send("/bd2/spine/download/cancel")
                                        job = state.api.get("/bd2/spine/download/status")
                                    }
                                },
                                enabled = !state.busy,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("取消更新")
                            }
                        }
                    }
                }
            }
        }

        item { JobStatus(job) }

        val filtered = assets.filter { it.text("kind") == kind && it.text("title").contains(search, true) }

        if (filtered.isEmpty() && !state.busy) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(HeColors.PanelGradient)
                        .border(1.dp, HeColors.HairlineRim, RoundedCornerShape(14.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("此分类暂无资源", color = HeColors.OpWhiteSoft, fontFamily = NotoSansSC)
                }
            }
        }

        items(filtered, key = { it.text("id") }) { asset ->
            val shape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(HeColors.PanelGradient)
                    .border(1.dp, HeColors.HairlineRim, shape)
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = asset.text("title"),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = HeColors.OpWhite,
                                fontFamily = NotoSansSC
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFFF5252).copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = asset.text("asset_id"),
                                        fontFamily = GeistMono,
                                        fontSize = 11.sp,
                                        color = Color(0xFFFF5252)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                state.run {
                                    val cacheKey = MessageDigest.getInstance("SHA-256")
                                        .digest((server + asset.text("id")).toByteArray())
                                        .joinToString("") { "%02x".format(it) }
                                    val dir = File(context.cacheDir, "bd2/$cacheKey").apply { mkdirs() }
                                    val filenames = listOf(asset.text("skeleton"), asset.text("atlas")) +
                                            (asset.optJSONArray("textures")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList())
                                    withContext(Dispatchers.IO) {
                                        filenames.forEach { require(it.isNotBlank() && it == File(it).name) { "素材文件名无效" } }
                                    }
                                    for (name in filenames) {
                                        val file = File(dir, name)
                                        if (!file.exists() || file.length() == 0L) {
                                            val bytes = state.api.bytes("/bd2/spine/${encoded(asset.text("kind"))}/${encoded(asset.text("asset_id"))}/${encoded(name)}")
                                            withContext(Dispatchers.IO) {
                                                val temp = File(dir, "$name.part")
                                                if (name.endsWith(".webp", true)) {
                                                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("纹理解码失败")
                                                    try {
                                                        temp.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                                                    } finally {
                                                        bitmap.recycle()
                                                    }
                                                } else {
                                                    temp.writeBytes(bytes)
                                                }
                                                check(temp.renameTo(file)) { "素材保存失败" }
                                            }
                                        }
                                    }
                                    context.startActivity(
                                        Intent(context, NativeSpineActivity::class.java)
                                            .putExtra("directory", dir.absolutePath)
                                            .putExtra("skeleton", asset.text("skeleton"))
                                            .putExtra("atlas", asset.text("atlas"))
                                            .putExtra("title", asset.text("title"))
                                    )
                                }
                            },
                            enabled = !state.busy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HeColors.Yellow,
                                contentColor = HeColors.OnYellow
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("打开原生动画", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
