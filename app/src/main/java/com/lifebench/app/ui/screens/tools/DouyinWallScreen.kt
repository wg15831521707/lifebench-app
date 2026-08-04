package com.lifebench.app.ui.screens.tools

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.gson.Gson
import com.lifebench.app.ui.components.AppTopBar
import com.lifebench.app.ui.theme.Dimen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset

/**
 * 抖音热榜视频墙（占位内容，后续可替换为用户自选视频）。
 * - 离线快照来自 assets/douyin/hotlist.json + 本地压缩封面，无需联网；
 * - 点击任意卡片 -> 直接拉起抖音官方 App 打开对应搜索；
 *   抖音已安装时【一定打开 App，绝不落网页】：内容深链按“域名优先 + snssdk1128 增强 + 通用 scheme”多候选尝试，
 *   都不命中则 getLaunchIntentForPackage 拉起 App 首页；仅当抖音完全未安装才回退网页版。
 */

private data class DouyinHotItem(
    val rank: Int,
    val title: String,
    val heat: Long,
    val label: String?,
    val videoCount: Int,
    val cover: String?,
    val link: String,
)

/**
 * 跳转抖音：只要设备装了抖音，就一定会拉起 App，绝不会落到浏览器。
 *
 * 设计原则（既保证“抖音更新后仍能跳”，又保证“当前一定能跳”）：
 * 1) 内容深链多候选：① snssdk1128://search?keyword=...（抖音长期稳定的私有 scheme，仅作增强）
 *    ② 通用 snssdk1128:// ③ https://...（域名，抗更新）。任一能 resolve 且解析到的不是浏览器即拉起 App。
 * 2) 包名来自「已知包 + 动态识别的抖音类包」，抗抖音换包名。
 * 3) 关键修复（v1.5.17）：解析结果若落在【浏览器包】一律跳过；已装抖音但深链未命中时，
 *    用 getLaunchIntentForPackage 强制拉起 App 首页，【绝不在“已装抖音”情况下回退浏览器】。
 * 4) 仅当抖音真的没装才回退网页，并用 Toast 明确告知用户实际发生了什么（便于排查）。
 */

/** 抖音候选包名（偏好提示 + 兜底；并非唯一入口，主路径靠域名 / 系统解析）。 */
private val DOUYIN_PACKAGES = listOf(
    "com.ss.android.ugc.aweme",
    "com.ss.android.ugc.aweme.lite",
)

/** 是否像抖音系包名（用于从“能处理域名的应用”里动态识别，抗换包名）。 */
private fun isDouyinPkg(p: String): Boolean =
    p.contains("douyin", true) || p.contains("aweme", true) || p.contains("bytedance", true)

/** 浏览器包关键词：解析结果命中这些一律视为“会跳网页”，必须跳过。 */
private val BROWSER_KEYWORDS = listOf(
    "browser", "chrome", "huawei.browser", "browserhd", "miui.browser",
    "sogou", "uc", "qqbrowser", "opera", "firefox", "360browser", "hao", "explorer",
)

private fun isBrowserPkg(pkg: String?): Boolean {
    if (pkg == null) return false
    val lp = pkg.lowercase()
    return BROWSER_KEYWORDS.any { lp.contains(it) }
}

fun openDouyin(context: Context, url: String) {
    val pm = context.packageManager
    val uri = runCatching { android.net.Uri.parse(url) }.getOrNull()
    if (uri == null || !uri.host.orEmpty().contains("douyin.com")) {
        safeStart(context, Intent(Intent.ACTION_VIEW, uri), "未识别到抖音链接，已打开网页")
        return
    }

    // 候选包：已知抖音包 + 从域名处理者中动态识别（抗换包名）
    val domainPkgs = pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, uri), 0)
        .map { it.activityInfo.packageName }.distinct()
        .filter { isDouyinPkg(it) }
    val pkgs = (DOUYIN_PACKAGES + domainPkgs).distinct()

    // 内容深链候选（按优先级：私有 scheme 优先，域名兜底）
    val candidates = buildList<Intent> {
        val keyword = if (uri.path?.startsWith("/search") == true)
            uri.lastPathSegment?.let { java.net.URLDecoder.decode(it, "UTF-8") } else null
        if (keyword != null)
            add(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("snssdk1128://search?keyword=${java.net.URLEncoder.encode(keyword, "UTF-8")}")))
        add(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url.replaceFirst(Regex("^https?://"), "snssdk1128://"))))
        add(Intent(Intent.ACTION_VIEW, uri)) // 域名，仅当解析到抖音自身时才用
    }

    // 1) 内容深链：任一候选 + 任一抖音包能 resolve，且解析到的【不是浏览器】-> 直接拉起 App 并打开内容
    for (pkg in pkgs) {
        for (cand in candidates) {
            val i = Intent(cand).apply { setPackage(pkg); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            val resolved = runCatching { i.resolveActivity(pm) }.getOrNull()
            if (resolved != null && !isBrowserPkg(resolved.packageName)) {
                runCatching { context.startActivity(i) }.onSuccess {
                    Toast.makeText(context, "已为你打开抖音", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }
    }

    // 2) 已装抖音但深链未命中 -> 直接拉起 App 首页（绝不落浏览器）
    for (pkg in pkgs) {
        val home = runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull()
        if (home != null) {
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(home) }.onSuccess {
                Toast.makeText(context, "已为你打开抖音", Toast.LENGTH_SHORT).show()
                return
            }
        }
    }

    // 3) 终兜底：设备确实没装抖音 -> 浏览器打开网页版
    safeStart(context, Intent(Intent.ACTION_VIEW, uri), "未检测到抖音 App，已打开网页版")
}

/** 安全启动并打印 Toast，避免静默失败。 */
private fun safeStart(context: Context, intent: Intent, toast: String) {
    runCatching { context.startActivity(intent) }.onSuccess {
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "无法打开，请手动打开抖音", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun DouyinWallScreen(nav: NavController) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<DouyinHotItem>?>(null) }

    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("douyin/hotlist.json").use { stream ->
                    val json = stream.readBytes().toString(Charset.forName("UTF-8"))
                    Gson().fromJson(json, Array<DouyinHotItem>::class.java).toList()
                }
            }.getOrNull()
        }
    }

    Scaffold(
        topBar = { AppTopBar("抖音热榜", showBack = true, onBack = { nav.popBackStack() }) }
    ) { pad ->
        if (items == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (items!!.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("暂无热榜数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = Dimen.s16),
            verticalArrangement = Arrangement.spacedBy(Dimen.s12),
            horizontalArrangement = Arrangement.spacedBy(Dimen.s12),
            contentPadding = PaddingValues(vertical = Dimen.s12)
        ) {
            items(items = items!!, key = { it.rank }) { item ->
                DouyinCard(item) { openDouyin(context, item.link) }
            }
        }
    }
}

@Composable
private fun DouyinCard(item: DouyinHotItem, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(Dimen.cardRadius),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box {
            AssetImage(
                path = if (item.cover != null) "douyin/${item.cover}" else null,
                contentDescription = item.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.82f),
                contentScale = ContentScale.Crop
            )
            // 底部渐变遮罩，保证叠加文字可读
            Box(
                Modifier.fillMaxWidth().height(64.dp).align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                        )
                    )
            )
            // 中央播放按钮，提示「点击播放」
            Icon(
                Icons.Filled.PlayCircle, null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(44.dp).align(Alignment.Center)
            )
            RankBadge(item.rank, Modifier.align(Alignment.TopStart).padding(8.dp))
            item.label?.let {
                LabelTag(it, Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
        }
        Column(Modifier.padding(10.dp)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatHeat(item.heat),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
                if (item.videoCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "· ${item.videoCount}个视频",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 从 assets 异步加载位图（无第三方图片库）；加载失败回退品牌渐变占位。 */
@Composable
private fun AssetImage(
    path: String?,
    contentDescription: String,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    if (path == null) {
        Placeholder(modifier, contentDescription)
        return
    }
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bmp = context.assets.open(path).use { BitmapFactory.decodeStream(it) }
                bmp?.asImageBitmap()
            }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Placeholder(modifier, contentDescription)
    }
}

@Composable
private fun Placeholder(modifier: Modifier, contentDescription: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(
                    scheme.primary.copy(alpha = 0.85f),
                    scheme.primaryContainer
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.PlayCircle, null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
private fun RankBadge(rank: Int, modifier: Modifier) {
    val (bg, fg) = when (rank) {
        1 -> Color(0xFFFFD24A) to Color.Black
        2 -> Color(0xFFC9CDD6) to Color.Black
        3 -> Color(0xFFE0A06A) to Color.Black
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f) to MaterialTheme.colorScheme.onSurface
    }
    Surface(color = bg, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Text(
            "$rank",
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = fg,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun LabelTag(label: String, modifier: Modifier) {
    Surface(
        color = Color(0xFFFE2C55).copy(alpha = 0.92f),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/** 热度值格式化：>=1万 显示为「xx.x万」。 */
private fun formatHeat(n: Long): String {
    if (n < 10000) return n.toString()
    val w = n / 10000.0
    return (if (w >= 100) w.toInt().toString() else String.format("%.1f", w)) + "万"
}
