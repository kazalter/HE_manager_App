package com.hemanager.mobile.feature.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hemanager.mobile.data.HePrefs
import com.hemanager.mobile.player.data.PlayerPreferences
import com.hemanager.mobile.ui.op.CodeChip
import com.hemanager.mobile.ui.op.CtaSize
import com.hemanager.mobile.ui.op.FilterTab
import com.hemanager.mobile.ui.op.GhostCta
import com.hemanager.mobile.ui.op.IconBtn4
import com.hemanager.mobile.ui.op.Slash
import com.hemanager.mobile.ui.op.YellowCTA
import com.hemanager.mobile.ui.theme.AppBackgroundBrush
import com.hemanager.mobile.ui.theme.CutCornerShape
import com.hemanager.mobile.ui.theme.Geist
import com.hemanager.mobile.ui.theme.GeistMono
import com.hemanager.mobile.ui.theme.HeColors
import com.hemanager.mobile.ui.theme.NotoSansSC
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SettingsScreen(
    serverUrl: String,
    serverHistory: List<String>,
    onBack: () -> Unit,
    onSwitchServer: (String) -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { HePrefs(context) }
    val playerPrefs = remember { PlayerPreferences(context) }
    val scope = rememberCoroutineScope()
    val versionName = remember { appVersionName(context) }
    val packageName = remember { context.packageName }

    var galleryColumns by remember {
        mutableIntStateOf(prefs.galleryColumns.coerceIn(3, 7))
    }
    var coverCacheSizeMb by remember {
        mutableIntStateOf(prefs.coverCacheSizeMb)
    }
    var clearingCache by remember { mutableStateOf(false) }

    val speed by playerPrefs.speed.collectAsState(initial = 1.0f)
    val subtitlesEnabled by playerPrefs.subtitlesEnabled.collectAsState(initial = true)
    val servers = remember(serverUrl, serverHistory) {
        buildList {
            if (serverUrl.isNotBlank()) add(serverUrl)
            serverHistory.forEach { value ->
                val normalized = value.trim()
                if (normalized.isNotBlank() && normalized !in this) add(normalized)
            }
        }
    }

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundBrush())
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBtn4(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Slash(cn = "设置", en = "Settings", fontSize = 11.sp)
                    Text(
                        text = "本机偏好与连接配置",
                        color = HeColors.OpWhiteMuted,
                        fontFamily = Geist,
                        fontSize = 12.sp,
                    )
                }
                CodeChip("v$versionName", color = HeColors.Yellow)
            }

            SettingsGroup(title = "显示", en = "Display", icon = Icons.Default.Storage) {
                SettingCaption(
                    title = "图廊默认列数",
                    value = "$galleryColumns 列",
                    description = "影响图片图廊进入时的初始密度，仍可用双指缩放临时调整。"
                )
                Slider(
                    value = galleryColumns.toFloat(),
                    onValueChange = { value ->
                        val next = value.roundToInt().coerceIn(3, 7)
                        galleryColumns = next
                        prefs.galleryColumns = next
                    },
                    valueRange = 3f..7f,
                    steps = 3,
                    colors = SliderDefaults.colors(
                        thumbColor = HeColors.Yellow,
                        activeTrackColor = HeColors.Yellow,
                        inactiveTrackColor = HeColors.HairlineMid,
                    )
                )
            }

            SettingsGroup(title = "缓存", en = "Cache", icon = Icons.Default.Storage) {
                SettingCaption(
                    title = "封面磁盘缓存上限",
                    value = "$coverCacheSizeMb MB",
                    description = "新上限会在下次界面重建后应用；清理缓存会立即执行。"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(128, 256, 512).forEach { size ->
                        FilterTab(
                            label = "$size",
                            en = "MB",
                            active = coverCacheSizeMb == size,
                            onClick = {
                                coverCacheSizeMb = size
                                prefs.coverCacheSizeMb = size
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                GhostCta(
                    text = if (clearingCache) "清理中" else "清理封面缓存",
                    onClick = {
                        if (clearingCache) return@GhostCta
                        clearingCache = true
                        scope.launch {
                            val success = withContext(Dispatchers.IO) {
                                runCatching { clearCoverCache(context) }.getOrDefault(false)
                            }
                            clearingCache = false
                            Toast.makeText(
                                context,
                                if (success) "封面缓存已清理" else "封面缓存清理失败",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    icon = Icons.Default.Delete,
                    size = CtaSize.Small,
                    fullWidth = true,
                )
            }

            SettingsGroup(title = "连接", en = "Connection", icon = Icons.Default.CheckCircle) {
                SettingCaption(
                    title = "当前服务器",
                    value = serverUrl,
                    description = "切换到其他历史服务器会回到登录页，避免复用不匹配的 token。"
                )
                if (servers.isEmpty()) {
                    Text(
                        text = "暂无服务器历史",
                        color = HeColors.OpWhiteMuted,
                        fontFamily = Geist,
                        fontSize = 13.sp,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        servers.forEach { server ->
                            ServerOptionRow(
                                server = server,
                                active = server == serverUrl,
                                onClick = {
                                    if (server != serverUrl) onSwitchServer(server)
                                }
                            )
                        }
                    }
                }
            }

            SettingsGroup(title = "播放", en = "Playback", icon = Icons.Default.Speed) {
                SettingCaption(
                    title = "默认播放速度",
                    value = formatSpeed(speed),
                    description = "视频播放器会沿用这里保存的速度偏好。"
                )
                Slider(
                    value = speed.coerceIn(0.5f, 2.0f),
                    onValueChange = { value ->
                        val snapped = (value * 4f).roundToInt() / 4f
                        scope.launch {
                            playerPrefs.setSpeed(snapped.coerceIn(0.5f, 2.0f))
                        }
                    },
                    valueRange = 0.5f..2.0f,
                    steps = 5,
                    colors = SliderDefaults.colors(
                        thumbColor = HeColors.Yellow,
                        activeTrackColor = HeColors.Yellow,
                        inactiveTrackColor = HeColors.HairlineMid,
                    )
                )
                ToggleRow(
                    icon = Icons.Default.Subtitles,
                    title = "默认启用字幕",
                    checked = subtitlesEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { playerPrefs.setSubtitlesEnabled(enabled) }
                    }
                )
            }

            SettingsGroup(title = "关于", en = "About", icon = Icons.AutoMirrored.Filled.ExitToApp) {
                SettingCaption(
                    title = "HE Manager Android",
                    value = "v$versionName",
                    description = "包名 $packageName"
                )
                YellowCTA(
                    text = "退出登录",
                    onClick = onLogout,
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    size = CtaSize.Medium,
                    fullWidth = true,
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    en: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = CutCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HeColors.Panel.copy(alpha = 0.94f))
            .border(1.dp, HeColors.HairlineMid, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = HeColors.Yellow,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Slash(cn = title, en = en, fontSize = 10.5.sp)
        }
        content()
    }
}

@Composable
private fun SettingCaption(
    title: String,
    value: String,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = HeColors.OpWhite,
                fontFamily = NotoSansSC,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                color = HeColors.Yellow,
                fontFamily = GeistMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = description,
            color = HeColors.OpWhiteMuted,
            fontFamily = Geist,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun ServerOptionRow(
    server: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val shape = CutCornerShape(9.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) HeColors.YellowSoft else Color.Transparent)
            .border(1.dp, if (active) HeColors.Yellow.copy(alpha = 0.45f) else HeColors.HairlineMid, shape)
            .clickable(enabled = !active) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = server,
            color = if (active) HeColors.OpWhite else HeColors.OpWhiteSoft,
            fontFamily = GeistMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (active) {
            Spacer(Modifier.width(8.dp))
            CodeChip("CURRENT", color = HeColors.Yellow)
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = HeColors.OpWhiteSoft,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            color = HeColors.OpWhite,
            fontFamily = NotoSansSC,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = HeColors.OnYellow,
                checkedTrackColor = HeColors.Yellow,
                uncheckedThumbColor = HeColors.OpWhiteSoft,
                uncheckedTrackColor = HeColors.SurfaceAlt,
                uncheckedBorderColor = HeColors.HairlineHi,
            )
        )
    }
}

private fun clearCoverCache(context: Context): Boolean {
    val dir = context.cacheDir.resolve("coil_cover_cache")
    if (dir.exists() && !dir.deleteRecursively()) return false
    return dir.mkdirs() || dir.exists()
}

private fun formatSpeed(speed: Float): String {
    return String.format(Locale.ROOT, "%.2fx", speed)
}

private fun appVersionName(context: Context): String {
    return runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.1.0"
    }.getOrDefault("0.1.0")
}
