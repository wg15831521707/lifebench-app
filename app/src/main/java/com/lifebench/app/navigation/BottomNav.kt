package com.lifebench.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifebench.app.ui.theme.Dimen

/** 底部四大主导航栏目（工作台 / 专注 / 工具 / 我的）。 */
data class TabItem(val route: String, val label: String, val icon: ImageVector)

val BottomTabs = listOf(
    TabItem(Routes.HOME, "工作台", Icons.Filled.Home),
    TabItem(Routes.FOCUS_HUB, "专注", Icons.Filled.Spa),
    TabItem(Routes.TOOLS, "工具", Icons.Filled.Widgets),
    TabItem(Routes.PROFILE, "我的", Icons.Filled.Person),
)

/**
 * 自定义底部导航栏（对应 lifebench-redesign.html 的 .nav）：
 * - 半透明表面 + 顶部发丝描边，呈现磨砂质感（rgba(255,255,255,.86) 的意图）；
 * - 选中项显示「顶部品牌色小药丸」指示器（30×3dp），图标轻微上移 1dp，文字转品牌色；
 * - 未选中为弱化灰；固定 72dp 高，并避让系统手势条。
 * 无障碍：每项用 selectable + Role.Tab，支持键盘/读屏聚焦。
 */
@Composable
fun BottomNavigationBar(
    currentRoute: String?,
    onTabSelected: (TabItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            // 顶部发丝描边（对应模板 border-top:1px solid hair）
            HorizontalDivider(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            Row(
                Modifier.fillMaxSize().padding(horizontal = Dimen.s8, vertical = Dimen.s6),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomTabs.forEach { tab ->
                    BottomNavItem(tab, currentRoute == tab.route, onTabSelected)
                }
            }
        }
    }
}

@Composable
private fun RowScope.BottomNavItem(
    tab: TabItem,
    selected: Boolean,
    onTabSelected: (TabItem) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = { onTabSelected(tab) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.offset(y = if (selected) (-1).dp else 0.dp)
        ) {
            Icon(
                tab.icon,
                contentDescription = tab.label,
                tint = if (selected) primary else muted,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(3.dp))
            Text(
                tab.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) primary else muted
            )
        }
        if (selected) {
            // 顶部品牌色小药丸指示器（30×3dp，对应模板 .nav button.on::after）
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-1).dp)
                    .size(width = 30.dp, height = 3.dp)
                    .background(primary, RoundedCornerShape(3.dp))
            )
        }
    }
}
