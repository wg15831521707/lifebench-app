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

/** 跳转抖音：优先用私有 scheme 直接拉起官方 App，未安装才回退浏览器。 */
fun openDouyin(context: Context, url: String) {
    // 1) 抖音私有 scheme 一定命中 App（已安装时），体验最佳、不会落到网页
    buildDouyinSchemeIntent(url)?.let { schemeIntent ->
        if (schemeIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(schemeIntent)
            return
        }
    }
    // 2) 兜底：指定抖音包名尝试 https 深链
    val appIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
        setPackage("com.ss.android.ugc.aweme")
    }
    try {
        context.startActivity(appIntent)
    } catch (_: ActivityNotFoundException) {
        // 仅在抖音 App 未安装时，才退回网页版
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    }
}

/**
 * 将抖音网页链接转换为 App 私有 scheme 意图：
 * - www.douyin.com/search/关键词 -> snssdk1128://search?keyword=关键词
 * - 其它抖音链接（视频/用户等）直接把 https 换成 snssdk1128://
 * 私有 scheme 在抖音已安装时必然命中 App，避免被系统浏览器拦截。
 */
private fun buildDouyinSchemeIntent(url: String): Intent? = runCatching {
    val uri = android.net.Uri.parse(url)
    val host = uri.host.orEmpty()
    if (!host.contains("douyin.com")) return@runCatching null
    val deep = if (uri.path?.startsWith("/search") == true) {
        val keyword = uri.lastPathSegment ?: return@runCatching null
        val decoded = java.net.URLDecoder.decode(keyword, "UTF-8")
        "snssdk1128://search?keyword=${java.net.URLEncoder.encode(decoded, "UTF-8")}"
    } else {
        url.replaceFirst(Regex("^https?://"), "snssdk1128://")
    }
    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(deep)).apply {
        setPackage("com.ss.android.ugc.aweme")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}.getOrNull()

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
