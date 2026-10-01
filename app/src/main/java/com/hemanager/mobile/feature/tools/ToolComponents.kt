package com.hemanager.mobile.feature.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hemanager.mobile.ui.theme.HeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ToolPage(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        containerColor = HeColors.Ink,
        topBar = {
            TopAppBar(title = { Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HeColors.Ink))
        }, content = content
    )
}

@Composable
internal fun ToolCard(title: String, subtitle: String, icon: ImageVector, badge: String? = null, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HeColors.Panel)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(color = HeColors.YellowSoft, shape = RoundedCornerShape(12.dp)) {
                Icon(icon, null, tint = HeColors.Yellow, modifier = Modifier.padding(12.dp).size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = HeColors.OpWhiteSoft)
                if (badge != null) Text(badge, style = MaterialTheme.typography.labelSmall, color = HeColors.Cyan)
            }
            Icon(Icons.Default.ChevronRight, null, tint = HeColors.OpWhiteSoft)
        }
    }
}

@Composable
internal fun ToolError(message: String, onRetry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}
