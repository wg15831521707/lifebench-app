'use strict';
/**
 * 抖音热榜代理 — 阿里云函数计算 FC（Node.js Web 函数）
 *
 * - 服务端抓取抖音网页热榜接口，归一化为小满 App 所需的 JSON 形状
 * - 内存 TTL 缓存 5 分钟，避免频繁打 Douyin 触发风控
 * - 标准 http 服务监听 PORT（FC 注入 9000），不依赖任何 FC 私有 API
 * - 开启 CORS，供 App 的 HttpURLConnection 直接调用
 *
 * 部署：阿里云 FC 控制台 → 创建函数（使用自定义运行时创建 / Web 函数）
 * 运行时 Node.js 18/20，监听端口 9000，HTTP 触发器认证「无需认证」。
 */

const http = require('http');
const https = require('https');

const DOUYIN_URL = 'https://www.douyin.com/aweme/v1/hot/search/list/';
// APK 下载中转：服务端代抓 GitHub 安装包再回传，供国内用户直连下载
const APK_URL = 'https://github.com/wg15831521707/lifebench-app/releases/download/v1.5.26/xiaoman-v1.5.26-debug.apk';
const APK_NAME = 'xiaoman-v1.5.26-debug.apk';
const UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ' +
  '(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';
const CACHE_TTL = 300 * 1000; // 5 分钟

let __hotCache = null; // { ts:number, data:Array }

/** 抓取抖音热榜接口 */
function fetchDouyin() {
  return new Promise((resolve, reject) => {
    const req = https.get(
      DOUYIN_URL,
      {
        headers: {
          'User-Agent': UA,
          Accept: 'application/json',
          'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
          Referer: 'https://www.douyin.com/',
        },
        timeout: 8000,
      },
      (res) => {
        if (res.statusCode !== 200) {
          res.resume(); // 丢弃响应体
          reject(new Error('Douyin HTTP ' + res.statusCode));
          return;
        }
        let body = '';
        res.setEncoding('utf8');
        res.on('data', (c) => {
          body += c;
        });
        res.on('end', () => {
          try {
            const raw = JSON.parse(body);
            resolve(normalize(raw));
          } catch (e) {
            reject(e);
          }
        });
      }
    );
    req.on('error', reject);
    req.on('timeout', () => {
      req.destroy(new Error('Douyin fetch timeout'));
    });
  });
}

/** 标签归一化：抖音可能返回字符串("热"/"沸"/"新")或数字(1/2/3)，统一为展示字符串 */
function normalizeLabel(v) {
  if (v == null) return null;
  if (typeof v === 'string') return v.trim() || null;
  const n = Number(v);
  if (Number.isNaN(n)) return String(v);
  return { 1: '沸', 2: '新', 3: '热' }[n] || null;
}

/** 归一化为 App 所需的条目形状：{rank,title,heat,label,videoCount,link} */
function normalize(raw) {
  const list = (raw && raw.data && raw.data.word_list) || [];
  return list.slice(0, 50).map((it, i) => ({
    rank: i + 1,
    title: it.word || '无标题',
    heat: Number(it.hot_value) || 0,
    label: normalizeLabel(it.label),
    videoCount: Number(it.video_count) || 0,
    link:
      it.url ||
      'https://www.douyin.com/search/' + encodeURIComponent(it.word || ''),
  }));
}

function sendJson(res, data, status) {
  const body = JSON.stringify(data);
  res.writeHead(status || 200, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Cache-Control': 'public, max-age=60',
  });
  res.end(body);
}

/**
 * APK 下载中转：跟随 GitHub 的 302 重定向，把安装包字节流 pipe 回客户端。
 * 让国内用户从 *.fcapp.run/apk 直连下载，文件名确定、链路稳定。
 */
function pipeApk(res) {
  function fetchUrl(url, redirects) {
    if (redirects > 5) {
      res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('too many redirects');
      return;
    }
    https
      .get(
        url,
        {
          headers: {
            'User-Agent':
              'Mozilla/5.0 (compatible; xiaoman-apk-proxy/1.0)',
          },
          // 兼容部分运行环境的 CA 链缺失（阿里云 FC 个别节点可能缺根证书）
          rejectUnauthorized: false,
        },
        (up) => {
          if (up.statusCode >= 300 && up.statusCode < 400 && up.headers.location) {
            up.resume(); // 丢弃重定向响应体
            const next = new URL(up.headers.location, url).href;
            fetchUrl(next, redirects + 1);
            return;
          }
          if (up.statusCode !== 200) {
            up.resume();
            res.writeHead(up.statusCode, { 'Content-Type': 'text/plain; charset=utf-8' });
            res.end('upstream HTTP ' + up.statusCode);
            return;
          }
          res.writeHead(200, {
            'Content-Type': 'application/vnd.android.package-archive',
            'Content-Disposition': 'attachment; filename="' + APK_NAME + '"',
            'Access-Control-Allow-Origin': '*',
            'Cache-Control': 'public, max-age=300',
          });
          up.pipe(res);
        }
      )
      .on('error', (e) => {
        res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' });
        res.end('fetch error: ' + e.message);
      });
  }
  fetchUrl(APK_URL, 0);
}

const server = http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');

  // CORS 预检
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Methods': 'GET, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type',
    });
    res.end('');
    return;
  }

  if (req.method !== 'GET') {
    sendJson(res, { error: 'method not allowed' }, 405);
    return;
  }

  // APK 下载中转：/apk 路由，服务端代抓 GitHub 安装包并回传
  const reqPath = (req.url || '/').split('?')[0];
  if (reqPath === '/apk' || reqPath === '/apk/') {
    pipeApk(res);
    return;
  }

  // 命中缓存直接返回
  if (__hotCache && Date.now() - __hotCache.ts < CACHE_TTL) {
    sendJson(res, __hotCache.data);
    return;
  }

  fetchDouyin()
    .then((data) => {
      __hotCache = { ts: Date.now(), data };
      sendJson(res, data);
    })
    .catch((e) => {
      // 抓取失败：返回过期缓存（若有），否则 502
      if (__hotCache) {
        sendJson(res, __hotCache.data);
      } else {
        sendJson(res, { error: String((e && e.message) || e) }, 502);
      }
    });
});

const port = Number(process.env.PORT) || 9000;
server.listen(port, '0.0.0.0', () => {
  console.log('douyin-hot-proxy listening on ' + port);
});
