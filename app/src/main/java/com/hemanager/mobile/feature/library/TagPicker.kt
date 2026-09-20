package com.hemanager.mobile.feature.library

// 标签选择底部 sheet。
// 用户长按媒体卡片或在快捷动作中触发，展示已加标签 + 加新标签的输入框。

import com.hemanager.mobile.ApiClient
import com.hemanager.mobile.MediaItem
import com.hemanager.mobile.TagItem
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.platform.LocalContext
import com.hemanager.mobile.ui.util.toastError
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TagPickerSheetV2(
    serverUrl: String,
    token: String,
    item: MediaItem,
    onDismiss: () -> Unit,
    onUnauthorized: (Throwable) -> Boolean,
    onTagAdded: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var allTags by remember { mutableStateOf<List<TagItem>>(emptyList()) }
    var loadingTags by remember { mutableStateOf(true) }
    var newTagName by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        val result = withContext(Dispatchers.IO) {
            runCatching { ApiClient(serverUrl, token).getTags() }
        }
        loadingTags = false
        result.onSuccess { allTags = it }
        result.onFailure {
            if (onUnauthorized(it)) return@onFailure
            context.toastError("标签加载失败", readableError(it))
        }
    }

    fun submitTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || submitting) return
        submitting = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ApiClient(serverUrl, token).addTag(item.id, trimmed) }
            }
            submitting = false
            result.onSuccess { fresh ->
                onTagAdded(fresh)
                newTagName = ""
                if (allTags.none { it.name.equals(trimmed, ignoreCase = true) }) {
                    // Re-fetch tag list so the new tag becomes available for other items.
                    scope.launch {
                        val refresh = withContext(Dispatchers.IO) {
                            runCatching { ApiClient(serverUrl, token).getTags() }
                        }
                        refresh.onSuccess { allTags = it }
                    }
                }
            }
            result.onFailure {
                if (onUnauthorized(it)) return@onFailure
                context.toastError("添加失败", readableError(it))
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF12172A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                "为「${item.title}」添加标签",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(14.dp))

            if (item.tags.isNotEmpty()) {
                Text(
                    "当前标签",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(6.dp))
                FlowRowCompat(
                    items = item.tags,
                    spacing = 8.dp
                ) { tag ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFF7AB8FF).copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, Color(0xFF7AB8FF).copy(alpha = 0.42f))
                    ) {
                        Text(
                            tag.name,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = Color(0xFFB8D4FF),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Text(
                "新建标签",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入标签名…", color = Color.White.copy(alpha = 0.45f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            submitTag(newTagName)
                            focusManager.clearFocus()
                        },
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF7AB8FF).copy(alpha = 0.6f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                        cursorColor = Color(0xFF7AB8FF)
                    )
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = { submitTag(newTagName) },
                    enabled = newTagName.trim().isNotEmpty() && !submitting,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF7AB8FF),
                        contentColor = Color(0xFF0B1020)
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(18.dp))

            Text(
                "已有标签",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(8.dp))
            if (loadingTags) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF7AB8FF))
                    Spacer(Modifier.width(10.dp))
                    Text("加载中…", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                }
            } else if (allTags.isEmpty()) {
                Text("暂无标签，新建一个吧", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
            } else {
                val attachedNames = remember(item.tags) { item.tags.map { it.name }.toSet() }
                FlowRowCompat(items = allTags, spacing = 8.dp) { tag ->
                    val attached = tag.name in attachedNames
                    Surface(
                        modifier = Modifier.clickable(enabled = !attached && !submitting) {
                            submitTag(tag.name)
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = if (attached) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = if (attached) 0.10f else 0.20f))
                    ) {
                        Text(
                            tag.name,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = if (attached) Color.White.copy(alpha = 0.45f) else Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * Minimal flow row — wraps children to multiple lines as space runs out.
 * Compose has FlowRow in androidx.compose.foundation.layout but it's still
 * marked experimental; this avoids the opt-in and pulls its weight.
 */
@Composable
internal fun <T> FlowRowCompat(
    items: List<T>,
    spacing: androidx.compose.ui.unit.Dp,
    content: @Composable (T) -> Unit
) {
    Layout(
        content = { items.forEach { content(it) } },
        modifier = Modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val spacingPx = spacing.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val rows = mutableListOf<MutableList<androidx.compose.ui.layout.Placeable>>()
        var current = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var rowWidth = 0
        placeables.forEach { p ->
            val needed = if (current.isEmpty()) p.width else rowWidth + spacingPx + p.width
            if (needed > constraints.maxWidth && current.isNotEmpty()) {
                rows.add(current)
                current = mutableListOf(p)
                rowWidth = p.width
            } else {
                current.add(p)
                rowWidth = needed
            }
        }
        if (current.isNotEmpty()) rows.add(current)
        val totalHeight = rows.sumOf { row -> row.maxOfOrNull { it.height } ?: 0 } +
            spacingPx * (rows.size - 1).coerceAtLeast(0)
        layout(constraints.maxWidth, totalHeight) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                val rowHeight = row.maxOfOrNull { it.height } ?: 0
                row.forEach { p ->
                    p.placeRelative(x, y)
                    x += p.width + spacingPx
                }
                y += rowHeight + spacingPx
            }
        }
    }
}
