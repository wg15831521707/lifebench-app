package com.lifebench.app.ui.screens.tools

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
import java.net.URLDecoder
import java.net.URLEncoder
import com.google.gson.Gson
import com.lifebench.app.ui.components.AppTopBar
import com.lifebench.app.ui.theme.Dimen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

/**
 * 抖音热榜视频墙（占位内容，后续可替换为用户自选视频）。
 * - 离线快照来自 assets/douyin/hotlist.json + 本地压缩封面，无需联网；
 * - 点击任意卡片 -> 直接拉起抖音官方 App（com.ss.android.ugc.aweme）打开对应搜索，
 *   优先使用抖音私有 scheme（snssdk1128）确保一定打开 App；仅在 App 未安装时才回退网页版。
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
 * 跳转抖音：只要设备装了抖音官方 App，就一定会拉起 App（绝不会落到网页）。
 * 机型适配重点（华为 Nova 6 / HarmonyOS 4.2 / 抖音 39.9.0 等 Android 11+ 机型）：
 * 1) 必须在 AndroidManifest 用 <queries> 声明 com.ss.android.ugc.aweme，
 *    否则系统认为抖音「不可见/未安装」，resolveActivity 一律返回 null；
 * 2) 依次尝试：App Link(https+包名) -> 私有 scheme(snssdk1128) -> 直接拉起 App 首页；
 * 3) 仅当抖音确实未安装时，才回退到浏览器打开网页版。
 */
fun openDouyin(context: Context, url: String) {
    val pm = context.packageManager
    val pkg = "com.ss.android.ugc.aweme"
    val installed = runCatching { pm.getPackageInfo(pkg, 0) != null }.getOrElse { false }

    if (installed) {
        // 依次尝试所有候选意图，第一个能 resolve 的就启动（优先内容深链，最后兜底 App 首页）
        for (intent in buildDouyinIntents(url, pkg)) {
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                return
            }
        }
        // 极少数情况下所有 scheme 都 resolve 不到，但 App 确实在 -> 直接拉起首页
        pm.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
            return
        }
    }
    // 仅当抖音未安装时才退到浏览器
    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
}

/**
 * 构造一组「打开抖音」候选意图，按优先级排序：
 * 1) App Link：https + 包名（若抖音为该域名注册了已校验 App Link，可直接在 App 内打开搜索/视频）
 * 2) 私有 scheme：snssdk1128://search?keyword=关键词（搜索场景）
 * 3) 私有 scheme：snssdk1128:// + 原路径（视频/用户等）
 * 4) 私有 scheme：裸 snssdk1128://（一定拉起 App 首页）
 */
private fun buildDouyinIntents(url: String, pkg: String): List<Intent> {
    val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return emptyList()
    val host = uri.host.orEmpty()
    if (!host.contains("douyin.com")) return emptyList()
    val flag = Intent.FLAG_ACTIVITY_NEW_TASK
    val list = mutableListOf<Intent>()

    // 1) App Link（带包名，避免被系统浏览器抢走）
    list += Intent(Intent.ACTION_VIEW, uri).apply { setPackage(pkg); addFlags(flag) }

    val keyword = if (uri.path?.startsWith("/search") == true) {
        uri.lastPathSegment?.let { java.net.URLDecoder.decode(it, "UTF-8") }
    } else null

    // 2) 搜索专用 scheme
    if (keyword != null) {
        val enc = java.net.URLEncoder.encode(keyword, "UTF-8")
        list += Intent(Intent.ACTION_VIEW, android.net.Uri.parse("snssdk1128://search?keyword=$enc")).apply {
            setPackage(pkg); addFlags(flag)
        }
    }
    // 3) 通用 scheme（视频/用户链接把 https 换成 snssdk1128）
    list += Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url.replaceFirst(Regex("^https?://"), "snssdk1128://"))).apply {
        setPackage(pkg); addFlags(flag)
    }
    // 4) 裸 scheme：兜底一定拉起 App
    list += Intent(Intent.ACTION_VIEW, android.net.Uri.parse("snssdk1128://")).apply {
        setPackage(pkg); addFlags(flag)
    }
    return list
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
