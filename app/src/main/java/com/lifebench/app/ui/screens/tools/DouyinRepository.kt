package com.lifebench.app.ui.screens.tools

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

/**
 * 抖音热榜数据仓库：本地缓存 + 自有 Worker 代理拉取，保持「离线优先」。
 *
 * - 展示数据优先级：本地缓存文件 > assets 内嵌快照（首次种子）。
 * - 刷新：下拉/按钮触发，拉取自有阿里云函数计算 FC 代理（仅此一处联网）；
 *   成功覆盖缓存并更新时间戳；失败由调用方降级显示缓存。
 * - 封面：线上接口不返回封面，按 rank 复用本地 assets/douyin/covers/{rank:02d}.jpg，
 *   缺失时卡片自动回退品牌渐变占位。
 */

/** 与 App UI 对齐的热榜条目模型。 */
data class DouyinHotItem(
    val rank: Int,
    val title: String,
    val heat: Long,
    val label: String?,
    val videoCount: Int,
    val cover: String?,
    val link: String,
)

/**
 * 热榜代理地址（阿里云函数计算 FC，自带 *.fcapp.run 国内可直连域名，无需备案）。
 * 部署后请将下方占位符替换为你的 FC HTTP 触发器公网地址
 * （形如 https://xxxx.cn-hangzhou.fcapp.run/，详见 fc-douyin-hot/ 目录）。
 */
private const val HOTLIST_API = "https://douyin-ot-proxy-rzglldiree.cn-hangzhou.fcapp.run/"

private const val CACHE_FILE = "douyin_hotlist_cache.json"
private const val SEED_ASSET = "douyin/hotlist.json"
private const val NET_TIMEOUT = 10_000

object DouyinRepository {

    private val gson = Gson()

    /**
     * 读取初始展示数据：优先缓存文件，无则种子 assets 并写入缓存。
     * 返回 (列表, 更新时间戳)。时间戳为 null 表示来自 assets 种子（非真实联网时间）。
     */
    suspend fun loadInitial(context: Context): Pair<List<DouyinHotItem>, Long?> =
        withContext(Dispatchers.IO) {
            readCache(context)?.let { return@withContext it }
            val seed = readAssets(context)
            if (seed != null) {
                writeCache(context, seed.first)
                seed
            } else {
                emptyList<DouyinHotItem>() to null
            }
        }

    /**
     * 刷新：拉取 Worker 代理，成功覆盖缓存并返回 (列表, 当前时间戳)。
     * 失败抛异常，由调用方降级到已显示的缓存数据。
     */
    suspend fun refresh(context: Context): Pair<List<DouyinHotItem>, Long> =
        withContext(Dispatchers.IO) {
            val list = fetchRemote()
            writeCache(context, list)
            list to System.currentTimeMillis()
        }

    private fun readCache(context: Context): Pair<List<DouyinHotItem>, Long>? {
        return runCatching {
            val f = java.io.File(context.filesDir, CACHE_FILE)
            if (!f.exists()) return null
            val wrapper = gson.fromJson(f.readText(Charset.forName("UTF-8")), CacheWrapper::class.java)
            (wrapper?.items ?: emptyList()) to (wrapper?.ts ?: 0L)
        }.getOrNull()
    }

    private fun writeCache(context: Context, items: List<DouyinHotItem>) {
        runCatching {
            val json = gson.toJson(CacheWrapper(items, System.currentTimeMillis()))
            java.io.File(context.filesDir, CACHE_FILE).writeText(json, Charset.forName("UTF-8"))
        }
    }

    private fun readAssets(context: Context): Pair<List<DouyinHotItem>, Long?>? {
        return runCatching {
            context.assets.open(SEED_ASSET).use { stream ->
                val json = stream.readBytes().toString(Charset.forName("UTF-8"))
                gson.fromJson(json, Array<DouyinHotItem>::class.java).toList() to null
            }
        }.getOrNull()
    }

    private fun fetchRemote(): List<DouyinHotItem> {
        if (HOTLIST_API.contains("REPLACE")) {
            throw IllegalStateException("Worker 地址未配置")
        }
        val conn = (URL(HOTLIST_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = NET_TIMEOUT
            readTimeout = NET_TIMEOUT
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Worker HTTP ${conn.responseCode}")
            }
            val body = conn.inputStream.bufferedReader(Charset.forName("UTF-8")).use { it.readText() }
            val arr = gson.fromJson(body, Array<RemoteItem>::class.java)
                ?: throw IllegalStateException("空响应")
            return arr.mapIndexed { i, it ->
                val rank = i + 1
                DouyinHotItem(
                    rank = rank,
                    title = it.title ?: "无标题",
                    heat = it.heat ?: 0,
                    label = it.label,
                    videoCount = it.videoCount ?: 0,
                    // 线上无封面：按 rank 复用本地 covers/{rank:02d}.jpg，缺失则卡片占位
                    cover = "covers/${String.format("%02d", rank)}.jpg",
                    link = it.link
                        ?: "https://www.douyin.com/search/${java.net.URLEncoder.encode(it.title ?: "", "UTF-8")}"
                )
            }
        } finally {
            conn.disconnect()
        }
    }

    private data class CacheWrapper(val items: List<DouyinHotItem>, val ts: Long)
    private data class RemoteItem(
        @SerializedName("rank") val rank: Int?,
        @SerializedName("title") val title: String?,
        @SerializedName("heat") val heat: Long?,
        @SerializedName("label") val label: String?,
        @SerializedName("videoCount") val videoCount: Int?,
        @SerializedName("link") val link: String?,
    )
}
