package com.lifebench.app.ui.screens.profile

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lifebench.app.data.Repo
import com.lifebench.app.navigation.Routes
import com.lifebench.app.R
import com.lifebench.app.ui.components.*
import com.lifebench.app.ui.theme.Dimen
import com.lifebench.app.util.TimeUtil

/**
 * 个人中心：数据概览、快捷设置入口、关于（详情见「设置页 - 关于」；备份功能已收敛到设置页）。
 */
@Composable
fun ProfileScreen(nav: NavController) {
    val todos by Repo.todo.observeActive().collectAsStateWithLifecycle(emptyList())
    val accounts by Repo.account.observeAll().collectAsStateWithLifecycle(emptyList())
    val notes by Repo.note.observeAll().collectAsStateWithLifecycle(emptyList())
    val focuses by Repo.focus.observeAll().collectAsStateWithLifecycle(emptyList())
    val habits by Repo.habit.observeActiveHabits().collectAsStateWithLifecycle(emptyList())
    val checkIns by Repo.habit.observeAllCheckIns().collectAsStateWithLifecycle(emptyList())
    val themeMode by Repo.settings.themeMode.collectAsStateWithLifecycle("SYSTEM")
    val fontScale by Repo.settings.fontScale.collectAsStateWithLifecycle(1.0f)

    // 累计坚持天数（取各习惯最长连续），用于个人头部副标题
    val byHabit = checkIns.groupBy { it.habitId }.mapValues { m -> m.value.map { it.date }.toSet() }
    val longestStreak = habits.maxOfOrNull { streakOf(byHabit[it.id] ?: emptySet()) } ?: 0

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("个人中心")
        Spacer(Modifier.height(Dimen.s12))
        // 个人头部：渐变头像 + 昵称 + 累计坚持天数（设计系统 ProfileHeader）
        ProfileHeader(name = "王浩", subtitle = "自律给我自由 · 已坚持 ${longestStreak} 天", avatarText = "浩")
        Spacer(Modifier.height(Dimen.s12))
        // 数据概览
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("数据概览", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimen.s8))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatBadge("待办", "${todos.size}")
                StatBadge("记账", "${accounts.size}")
                StatBadge("笔记", "${notes.size}")
                StatBadge("专注", "${focuses.size}次")
            }
        }
        Spacer(Modifier.height(Dimen.s12))
        // 快捷设置：仅保留跳转「全局设置」
        AppCard(Modifier.padding(horizontal = Dimen.s16).padding(bottom = Dimen.s12)) {
            Row(
                Modifier.fillMaxWidth().clickable { nav.navigate(Routes.SETTINGS) }.padding(vertical = Dimen.s4),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Settings, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Dimen.s12))
                Text("全局设置", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(Dimen.s12))
        // 关于：仅保留一句宣传语，点击进入「设置页 - 关于」查看详情
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Row(
                Modifier.fillMaxWidth().clickable { nav.navigate(Routes.SETTINGS) }.padding(vertical = Dimen.s4),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_launcher_art),
                    contentDescription = "小满应用图标",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape)
                )
                Spacer(Modifier.width(Dimen.s12))
                Column(Modifier.weight(1f)) {
                    Text("小满", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "离线优先 · 无广告 · 数据本地加密存储",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(Dimen.s24))
    }
}

/** 计算连续打卡天数：从今天（或昨天，若今天未打卡）往前连续计数。 */
private fun streakOf(dates: Set<Long>): Int {
    if (dates.isEmpty()) return 0
    var cursor = TimeUtil.dayKey()
    if (cursor !in dates) {
        cursor = TimeUtil.dayKey(cursor - 86_400_000L)
        if (cursor !in dates) return 0
    }
    var s = 0
    while (cursor in dates) {
        s++
        cursor = TimeUtil.dayKey(cursor - 86_400_000L)
    }
    return s
}
