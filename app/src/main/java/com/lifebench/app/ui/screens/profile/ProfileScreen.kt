package com.lifebench.app.ui.screens.profile

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lifebench.app.data.Repo
import com.lifebench.app.navigation.Routes
import com.lifebench.app.ui.components.*
import com.lifebench.app.ui.theme.Dimen
import com.lifebench.app.util.BackupUtil
import kotlinx.coroutines.launch

/**
 * 个人中心：数据概览、快捷设置入口、数据备份导入导出、关于。
 */
@Composable
fun ProfileScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val todos by Repo.todo.observeActive().collectAsStateWithLifecycle(emptyList())
    val accounts by Repo.account.observeAll().collectAsStateWithLifecycle(emptyList())
    val notes by Repo.note.observeAll().collectAsStateWithLifecycle(emptyList())
    val focuses by Repo.focus.observeAll().collectAsStateWithLifecycle(emptyList())
    val steps by Repo.step.observeWeek().collectAsStateWithLifecycle(emptyList())
    val themeMode by Repo.settings.themeMode.collectAsStateWithLifecycle("SYSTEM")
    val fontScale by Repo.settings.fontScale.collectAsStateWithLifecycle(1.0f)

    val totalSteps = steps.sumOf { it.steps }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("个人中心")
        Spacer(Modifier.height(Dimen.s12))
        // 数据概览
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("数据概览", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimen.s8))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("待办", "${todos.size}")
                Stat("记账", "${accounts.size}")
                Stat("笔记", "${notes.size}")
                Stat("专注", "${focuses.size}次")
                Stat("步数", "$totalSteps")
            }
        }
        Spacer(Modifier.height(Dimen.s12))
        // 快捷设置
        AppCard(Modifier.padding(horizontal = Dimen.s16).padding(bottom = Dimen.s12), onClick = { nav.navigate(Routes.SETTINGS) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Settings, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Dimen.s12))
                Text("全局设置（主题/字体/通知/预算）", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(Dimen.s12))
        // 数据备份
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("数据备份与恢复", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimen.s8))
            PrimaryButton("导出全部备份", onClick = {
                scope.launch {
                    val p = BackupUtil.exportAll(context)
                    Toast.makeText(context, "已导出：$p", Toast.LENGTH_LONG).show()
                }
            }, icon = Icons.Filled.FileDownload)
            Spacer(Modifier.height(Dimen.s8))
            PrimaryButton("导入备份恢复", onClick = {
                scope.launch {
                    val files = context.filesDir.listFiles()
                    val latest = files?.filter { it.name.endsWith(".json") }
                        ?.sortedByDescending { it.lastModified() }?.firstOrNull()
                    if (latest != null) {
                        val r = BackupUtil.importAll(context, latest)
                        Toast.makeText(context, if (r) "恢复成功" else "恢复失败", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "未找到备份文件", Toast.LENGTH_SHORT).show()
                    }
                }
            }, icon = Icons.Filled.FileUpload)
        }
        Spacer(Modifier.height(Dimen.s12))
        // 关于
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("关于", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimen.s8))
            Text("LifeBench 个人全能生活工作台 v1.0", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Dimen.s4))
            Text("离线优先 · 无广告 · 数据本地加密存储", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("含番茄钟、天气、睡眠、记账、饮食、健身、舒尔特、速读等模块，全部数据仅存于本机。",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Dimen.s24))
    }
}

/** 概览小指标。 */
@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
