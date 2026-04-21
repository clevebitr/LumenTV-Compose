package com.corner.catvodcore.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.corner.catvodcore.bean.Site
import com.corner.catvodcore.bean.Vod
import com.corner.catvodcore.bean.Vod.Companion.setVodFlags
import com.corner.catvodcore.bean.Collect
import com.corner.catvodcore.bean.Result
import com.corner.catvodcore.bean.Url
import com.corner.catvodcore.bean.add
import com.corner.catvodcore.bean.v
import com.corner.catvodcore.config.ApiConfig
import com.corner.util.net.Http
import com.corner.util.json.Jsons
import com.corner.util.net.Utils
import com.corner.catvodcore.viewmodel.GlobalAppState.hideProgress
import com.corner.catvodcore.viewmodel.GlobalAppState.showProgress
import com.corner.util.copyAdd
import com.corner.util.scope.createCoroutineScope
import com.github.catvod.crawler.Spider
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import okhttp3.Call
import okhttp3.Headers.Companion.toHeaders
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import com.corner.ui.nav.data.DialogState
import com.corner.ui.nav.data.DialogState.changeDialogState
import com.corner.ui.nav.data.ViewModelState
import com.corner.ui.scene.SnackBar
import com.corner.util.core.Constants
import com.corner.util.m3u8.M3U8AdFilterInterceptor
import com.corner.util.m3u8.M3U8Cache
import com.corner.util.net.createDefaultOkHttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Request
import java.net.URI

private val log = LoggerFactory.getLogger("SiteViewModel")

object SiteViewModel {
    private val _state = MutableStateFlow(ViewModelState())
    val state: MutableStateFlow<ViewModelState> = _state
    val result: MutableState<Result> by lazy { mutableStateOf(Result()) }
    val detail: MutableState<Result> by lazy { mutableStateOf(Result()) }
    val player: MutableState<Result> by lazy { mutableStateOf(Result()) }
    val search: MutableState<CopyOnWriteArrayList<Collect>> =
        mutableStateOf(CopyOnWriteArrayList(listOf(Collect.all())))
    val quickSearch: MutableState<CopyOnWriteArrayList<Collect>> =
        mutableStateOf(CopyOnWriteArrayList(listOf(Collect.all())))

    /**
     * 使用SupervisorJob确保单个任务失败不影响其他任务
     * 注意: 由于是全局单例,此scope不会自动取消,需手动管理
     */
    private val supervisorJob = SupervisorJob()
    val viewModelScope = createCoroutineScope(Dispatchers.IO)

    /**
     * 取消所有正在进行的任务
     * 应在应用退出或需要重置时调用
     */
    fun cancelAll() {
        supervisorJob.cancelChildren()
    }

    fun getSearchResultActive(): Collect {
        return search.value.first { it.activated.value }
    }

    fun homeContent(): Result {
        val site: Site = GlobalAppState.home.value
        result.value = Result()
        try {
            when (site.type) {
                3 -> {
                    val spider = ApiConfig.getSpider(site)
                    val homeContent = spider.homeContent(true)
                    ApiConfig.setRecent(site)
                    val rst: Result = Jsons.decodeFromString<Result>(homeContent)
                    if (rst.list.isNotEmpty()) result.value = rst
                    val homeVideoContent = spider.homeVideoContent()
                    rst.list.addAll(Jsons.decodeFromString<Result>(homeVideoContent).list)
                    result.value = rst.also { this.result.value = it }
                }

                4 -> {
                    val params: MutableMap<String, String> = mutableMapOf()
                    params["filter"] = "true"
                    val homeContent = call(site, params, false)
                    result.value = Jsons.decodeFromString<Result>(homeContent).also { this.result.value = it }
                }

                else -> {
                    val homeContent: String =
                        Http.newCall(site.api, site.header.toHeaders()).execute().use { response ->
                            if (!response.isSuccessful) throw IOException("Unexpected code $response")
                            val body = response.body
                            body.string()
                        }
                    fetchPic(site, Jsons.decodeFromString<Result>(homeContent)).also { result.value = it }
                }
            }
        } catch (e: IllegalStateException) {
            if (e.message?.contains("Playwright", ignoreCase = true) == true) {
                throw e
            }
            log.error("home Content site:{}", site.name, e)
            return Result(false)
        } catch (e: Exception) {
            log.error("home Content site:{}", site.name, e)
            return Result(false)
        }
        result.value.list.forEach { it.site = site }
        return result.value
    }

    fun detailContent(key: String, id: String): Result? {
        DialogState.resetBrowserChoice()

        changeDialogState(false)
        _state.update { it.copy(isSpecialVideoLink = false) }

        val site: Site = ApiConfig.api.sites.find { it.key == key } ?: return null
        var rst = Result()
        try {
            if (site.type == 3) {
                val spider: Spider = ApiConfig.getSpider(site)
                val detailContent = spider.detailContent(listOf(id))
                ApiConfig.setRecent(site)
                rst = Jsons.decodeFromString<Result>(detailContent)
                if (rst.list.isNotEmpty()) rst.list[0].setVodFlags()
                detail.value = rst
            } else if (site.key.isEmpty() && site.name.isEmpty() && key == "push_agent") {
                val vod = Vod()
                vod.vodId = id
                vod.vodName = id
                vod.vodPic = "https://pic.rmb.bdstatic.com/bjh/1d0b02d0f57f0a42201f92caba5107ed.jpeg"
                val rs = Result()
                rs.list = mutableListOf(vod)
                detail.value = rs
            } else {
                val params: MutableMap<String, String> =
                    mutableMapOf()
                params["ac"] = if (site.type == 0) "videolist" else "detail"
                params["ids"] = id
                val detailContent = call(site, params, true)
                log.debug("detail: $detailContent")
                rst = Jsons.decodeFromString<Result>(detailContent)
                if (rst.list.isNotEmpty()) rst.list[0].setVodFlags()
                detail.value = rst
            }
        } catch (e: Exception) {
            log.debug("${site.name} 后端错误（已忽略）: {}", e.message)
            return null
        }
        rst.list.forEach { it.site = site }

        return rst
    }

    /**
     * 获取视频播放信息并处理播放链接
     * @param key 站点唯一标识
     * @param flag 播放标识（区分不同播放源）
     * @param id 视频唯一标识
     * @return Pair对象，第一个元素为Result对象，第二个元素表示是否为特殊链接
     */
    fun playerContent(key: String, flag: String, id: String): Result? {
        val site = ApiConfig.getSite(key) ?: return null

        return try {
            // 重置特殊链接标志位
            _state.update { it.copy(isSpecialVideoLink = false) }
            changeDialogState(false)

            val rawResult = when (site.type) {
                3 -> handleType3Site(site, flag, id)        // 爬虫类型站点
                4 -> handleType4Site(site, flag, id)        // 参数请求类型站点
                else -> handleOtherTypeSite(site, flag, id) // 其他类型站点
            }

            rawResult.let { result ->
                result.header = site.header
                if (StringUtils.isNotBlank(flag)) result.flag = flag
                if (site.type == 3) result.key = key // 仅类型3需要key
                processVideoLink(result)
                return result
            }

        } catch (e: Exception) {
            log.error("Site [${site.name}] (key: $key) playerContent error. Flag: $flag, ID: $id", e)
            null
        }
    }

    /**
     * 处理「类型3（爬虫类型）」站点的差异化逻辑
     */
    private fun handleType3Site(site: Site, flag: String, id: String): Result {
        val spider = ApiConfig.getSpider(site)
        val playerContentStr = spider.playerContent(flag, id, ApiConfig.api.flags.toList())
        ApiConfig.setRecent(site) // 类型3特有：记录最近访问站点
        return Jsons.decodeFromString<Result>(playerContentStr) // 解析为Result
    }

    /**
     * 处理「类型4（参数请求类型）」站点的差异化逻辑
     */
    private fun handleType4Site(site: Site, flag: String, id: String): Result {
        // 类型4特有：构建请求参数
        val params = mutableMapOf(
            "play" to id,
            "flag" to flag
        )
        val playerContentStr = call(site, params, true) // 调用参数请求接口
        return Jsons.decodeFromString<Result>(playerContentStr) // 解析为Result
    }

    /**
     * 处理「其他类型」站点的差异化逻辑
     */
    private fun handleOtherTypeSite(site: Site, flag: String, id: String): Result {
        // 其他类型特有：初始化URL并处理JSON类型链接
        var url = Url().add(id)
        val urlType = Url(id).parameters["type"]
        if (urlType == "json" && StringUtils.isNotBlank(id)) {
            // 请求并解析JSON类型的真实链接
            val responseBody = Http.newCall(id, site.header.toHeaders()).execute().use { response ->
                if (!response.isSuccessful) {
                    log.error("获取JSON链接失败，状态码: ${response.code}")
                    return@use ""
                }
                response.body.string()
            }
            if (StringUtils.isNotBlank(responseBody)) {
                url = Jsons.decodeFromString<Result>(responseBody).url
            }
        }

        // 构建基础Result对象
        return Result().apply {
            this.url = url
            this.playUrl = site.playUrl
            // 其他类型特有：设置解析标识（0=无需解析，1=需要解析）
            this.parse = if (StringUtils.isBlank(site.playUrl)) 0 else 1
            if (StringUtils.isNotBlank(flag)) {
                this.flag = flag
            }
        }
    }

    /**
     * 统一处理视频链接：包含「特殊链接检测」和「标准M3U8处理」
     */
    private fun processVideoLink(result: Result) {
        val urlStr = result.url.v()
        if (urlStr.isBlank()) return // URL为空直接跳过

        // 0. 检测并处理磁力链接
        if (com.corner.util.net.Utils.isDownloadLink(urlStr)) {
            log.debug("发现磁力链接: $urlStr")
            // 根据用户选择更新状态
            if (!DialogState.userChoseOpenInBrowser) {
                DialogState.showPngDialog(urlStr)
                changeDialogState(true)
            } else {
                changeDialogState(false)
            }
            _state.update { it.copy(isSpecialVideoLink = true) }
            return // 磁力链接无需后续处理
        }

        // 0.5. 处理需要解析的加密 URL（parse == 1）
        if (result.parse == 1 && result.key != null) {
            log.debug("检测到需要解析的 URL (parse=1): $urlStr")
            // 将加密 URL 转换为本地代理 URL，由 Spider 的 proxyLocal 方法解密
            val proxyUrl = "http://127.0.0.1:${com.corner.server.KtorD.getPort()}/proxy?do=${result.key}&url=${java.net.URLEncoder.encode(urlStr, "UTF-8")}"
            result.url = Url().add(proxyUrl)
            result.parse = 0 // 已转换为代理 URL，无需再次解析
            log.debug("转换为代理 URL: $proxyUrl")
            return // 代理 URL 无需后续处理
        }

        // 1. 检测并处理「包含.m3u8但不以.m3u8结尾」的特殊链接
        // 注意：需要先去除查询参数再判断，避免将带参数的正常 m3u8 URL 误判为特殊链接
        val urlWithoutQuery = urlStr.split("?").firstOrNull() ?: urlStr
        val isSpecialLink = !urlStr.contains("proxy")
                && urlStr.contains(".m3u8", ignoreCase = true)
                && !urlWithoutQuery.trim().endsWith(".m3u8", ignoreCase = true)

        if (isSpecialLink) {
            log.debug("发现特殊链接(包含.m3u8但不以.m3u8结尾): $urlStr")
            // 根据用户选择更新状态
            if (!DialogState.userChoseOpenInBrowser) {
                DialogState.showPngDialog(urlStr)
                changeDialogState(true)
            } else {
                changeDialogState(false)
            }
            _state.update { it.copy(isSpecialVideoLink = true) }
            return // 特殊链接无需后续M3U8处理
        } else {
            log.debug("未发现特殊链接:{}", urlStr)
        }

        // 2. 处理「标准M3U8链接」（以.m3u8结尾、不含proxy）
        val isStandardM3u8 = urlWithoutQuery.endsWith(".m3u8", ignoreCase = true) && !urlStr.contains("proxy")
        if (isStandardM3u8) {
            result.url = processM3U8(result.url)
            log.debug("Processed standard M3U8 link: $urlStr")
        }
    }


    /**
     * 处理M3U8文件，修正错误的扩展名、处理加密密钥、过滤广告链接并返回本地代理URL
     *
     * @param url 包含 M3U8 文件地址的 Url 对象
     * @return 处理后的本地代理 Url 对象，若处理失败则返回原始 Url 对象
     */
    private fun processM3U8(url: Url, postMsg: Boolean = true): Url {
        // 如果不是 .m3u8 文件，直接返回原始 Url 对象
        if (!url.v().endsWith(".m3u8", ignoreCase = true)) {
            return url
        }
        if (postMsg) {
            SnackBar.postMsg("正在处理播放文件，请稍候...", type = SnackBar.MessageType.INFO)
        }
        try {
            showProgress()
            // 定义请求 M3U8 文件时需要携带的请求头

            val interceptor = M3U8AdFilterInterceptor.Interceptor()

            // 创建OkHttpClient并添加拦截器，使用统一的代理配置
            val client = createDefaultOkHttpClient()
                .newBuilder()
                .addInterceptor(interceptor)
                .build()

            // 使用拦截器处理请求
            val request = Request.Builder()
                .url(url.v())
                .headers(Constants.header.toHeaders())
                .build()

            val content = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    log.error("下载 M3U8 文件失败，状态码: ${response.code}")
                    return url
                }
                response.body.string()
            }

            //处理加密密钥（仅在存在密钥时处理）
            val processedKeyContent = if (content.contains("#EXT-X-KEY:")) {
                processEncryptionKeys(content, url.v())
            } else {
                content
            }
            //特殊链接检测（只匹配.png、.image和无后缀名链接）
            val pattern = Regex(
                // 匹配 http/https 协议
                "https?://" +
                        // 匹配域名部分（允许点号）
                        "[^\\s\"'/?#]+" +
                        // 匹配可选的路径部分（不包含点号）
                        "(?:/[^\\s\"'.?#]*)*" +
                        // 匹配三种情况：
                        // 1. 以.png结尾
                        // 2. 以.image结尾
                        // 3. 以.jpg结尾
                        // 4. 无后缀名（路径中无点号）
                        "(?:" +
                        "\\.(?:png|image|jpg)(?=[\\s\"'>]|$)" +  // 情况1和2
                        "|" +
                        "(?<!\\.)(?=[\\s\"'>]|$)" +         // 情况3：无后缀名（前面无点号）
                        ")",
                RegexOption.IGNORE_CASE
            )

            if (pattern.containsMatchIn(processedKeyContent)) {
                log.debug("<process>检测到特殊链接，弹出弹窗")
                if (!DialogState.userChoseOpenInBrowser) {
                    DialogState.showPngDialog(url.v())
                    changeDialogState(true)
                } else {
                    changeDialogState(false)
                }
                _state.update { it.copy(isSpecialVideoLink = true) }
                return url
            }

            // 3. 处理嵌套M3U8
            val processedNestedContent = Regex("(?m)^(?!#).*\\.m3u8$").replace(processedKeyContent) { match ->
                val nestedUrl = match.value.let {
                    if (it.startsWith("http")) it else "${url.v().substringBeforeLast("/")}/$it"
                }
                processM3U8(Url().add(nestedUrl), false).v() // 递归处理
            }

            // 4. 将相对路径的 .ts 片段转换为完整 URL（不代理，让 HLS.js 直接访问）
            val processedTsContent = convertRelativeTsToAbsolute(processedNestedContent, url.v())

            // 缓存内容并返回代理URL
            val cacheId = M3U8Cache.put(processedTsContent)
            log.debug("缓存M3U8文件成功，: http://127.0.0.1:9978/proxy/cached_m3u8?id=$cacheId")
            return Url().add("http://127.0.0.1:9978/proxy/cached_m3u8?id=$cacheId")
        } catch (e: Exception) {
            log.error("处理 M3U8 文件失败", e)
            return url
        } finally {
            hideProgress()
        }
    }

    /**
     * 处理M3U8中的加密密钥
     */
    private fun processEncryptionKeys(content: String, baseUrl: String): String {
        log.debug("开始处理密钥")
        val keyRegex = """#EXT-X-KEY:METHOD=([^,]+),URI="([^"]+)"(,IV=([^"]+))?""".toRegex()

        return keyRegex.replace(content) { match ->
            val (method, uri, _, iv) = match.destructured
            try {
                val keyUrl = when {
                    uri.startsWith("http") -> uri
                    uri.startsWith("/") -> {
                        val baseUri = URI(baseUrl)
                        "${baseUri.scheme}://${baseUri.host}$uri"
                    }

                    else -> {
                        // 处理相对路径
                        val basePath = baseUrl.substringBeforeLast("/")
                        "$basePath/$uri".replace(Regex("(?<!:)//"), "/")
                    }
                }
                val cacheId = downloadAndStoreKey(keyUrl)

                "#EXT-X-KEY:METHOD=$method,URI=\"$cacheId\"" +
                        (if (iv.isNotEmpty()) ",IV=$iv" else "")
            } catch (e: Exception) {
                log.error("密钥处理失败，保留原始标签", e)
                match.value
            }
        }
    }

    /**
     * 下载并存储加密密钥
     */
    private fun downloadAndStoreKey(keyUrl: String): String {
        // 使用带代理配置的HTTP客户端
        val client = createDefaultOkHttpClient()
        val request = Request.Builder().url(keyUrl).build()

        val keyData = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("密钥下载失败")
            response.body.bytes()
        }

        // 将密钥数据存入M3U8缓存
        val cacheId = M3U8Cache.put(String(keyData))

        // 返回通过本地服务器访问的缓存URL
        return "http://127.0.0.1:9978/proxy/cached_m3u8?id=$cacheId"
    }

    /**
     * 将 m3u8 文件中的相对路径 .ts 片段转换为完整 URL
     * 注意：不代理 .ts 文件，直接返回原始完整 URL，让 HLS.js 直接访问
     * @param content m3u8 文件内容
     * @param baseUrl m3u8 文件的基准 URL
     * @return 处理后的 m3u8 内容
     */
    private fun convertRelativeTsToAbsolute(content: String, baseUrl: String): String {
        // 创建基准 URI，用于解析相对路径
        val baseUri = try {
            java.net.URI(baseUrl)
        } catch (e: Exception) {
            log.error("无效的基准 URL: $baseUrl", e)
            return content // 如果基准 URL 无效，返回原始内容
        }
        
        // 匹配所有非注释行且包含 .ts 的行
        return content.lines().joinToString("\n") { line ->
            when {
                // 跳过注释行和空行
                line.startsWith("#") || line.isBlank() -> line
                // 已经是完整 URL，不需要处理
                line.startsWith("http") -> line
                // 处理相对路径的 .ts 文件，转换为完整 URL
                line.contains(".ts", ignoreCase = true) -> {
                    try {
                        // 使用 URI.resolve() 正确解析相对路径
                        val resolvedUri = baseUri.resolve(line)
                        val fullUrl = resolvedUri.toString()
                        log.debug("转换相对路径 .ts: $line -> $fullUrl")
                        fullUrl // 返回完整的原始 URL，不代理
                    } catch (e: Exception) {
                        log.error("URL 解析失败: line=$line, baseUrl=$baseUrl", e)
                        line // 解析失败时保留原始行
                    }
                }
                else -> line
            }
        }
    }


    /**
     * 根据站点和关键词进行搜索操作，支持快速搜索模式
     *
     * @param site 搜索使用的站点信息
     * @param keyword 搜索的关键词
     * @param quick 是否为快速搜索模式
     */
    fun searchContent(site: Site, keyword: String, quick: Boolean) {
        try {
            // 检查站点类型是否为 3
            if (site.type == 3) {
                val spider: Spider = ApiConfig.getSpider(site)
                val searchContent = spider.searchContent(keyword, quick)
                log.debug("search: " + site.name + "," + searchContent)
                val result = Jsons.decodeFromString<Result>(searchContent)
                post(site, result, quick)
            } else {
                // 非类型 3 的站点，构建搜索请求参数
                val params = mutableMapOf<String, String>()
                params["wd"] = keyword
                params["quick"] = quick.toString()
                val searchContent = call(site, params, true)
                log.debug(site.name + "," + searchContent)
                val result = Jsons.decodeFromString<Result>(searchContent)
                post(site, fetchPic(site, result), quick)
            }
        } catch (e: Exception) {
            log.error("${site.name} search error", e)
        }
    }


    /**
     * 根据指定站点、关键词和页码进行搜索操作，并将搜索结果存储在 `result` 状态中。
     *
     * @param site 搜索使用的站点信息
     * @param keyword 搜索的关键词
     * @param page 搜索的页码
     */
    @Suppress("unused")
    fun searchContent(site: Site, keyword: String, page: String) {
        try {
            if (site.type == 3) {
                val spider: Spider = ApiConfig.getSpider(site)
                val searchContent = spider.searchContent(keyword, false, page)
                log.debug(site.name + "," + searchContent)
                val rst = Jsons.decodeFromString<Result>(searchContent)
                for (vod in rst.list) vod.site = site
                result.value = rst
            } else {
                val params = mutableMapOf<String, String>()
                params["wd"] = keyword
                params["pg"] = page
                val searchContent = call(site, params, true)
                log.debug(site.name + "," + searchContent)
                val rst: Result = fetchPic(site, Jsons.decodeFromString<Result>(searchContent))
                for (vod in rst.list) vod.site = site
                result.value = rst
            }
        } catch (e: Exception) {
            log.error("${site.name} searchContent error", e)
        }
    }


    /**
     * 根据站点 key、分类 ID、页码、过滤标志和扩展参数获取分类内容
     *
     * @param key 站点的唯一标识
     * @param tid 分类的 ID
     * @param page 请求的页码
     * @param filter 是否启用过滤
     * @param extend 扩展参数，包含额外的请求信息
     * @return 包含分类内容的 Result 对象，若出错则返回表示失败的 Result 对象
     */
    fun categoryContent(
        key: String,
        tid: String,
        page: String,
        filter: Boolean,
        extend: HashMap<String, String>
    ): Result {
        log.info("categoryContent key:{} tid:{} page:{} filter:{} extend:{}", key, tid, page, filter, extend)
        val site: Site = ApiConfig.getSite(key) ?: return Result(false)
        try {
            if (site.type == 3) {
                val spider: Spider = ApiConfig.getSpider(site)
                val categoryContent = spider.categoryContent(tid, page, filter, extend)
                ApiConfig.setRecent(site)
                result.value = Jsons.decodeFromString<Result>(categoryContent)
                if (isEmptyResult(result.value)) {
                    log.warn("type3 cate is Empty: {}", categoryContent)
                }
                // 获取封面图片
                fetchPic(site, result.value)
            } else {
                val params = mutableMapOf<String, String>()
                if (site.type == 1 && extend.isNotEmpty()) params["f"] = Jsons.encodeToString(extend)
                else if (site.type == 4) params["ext"] = Utils.base64(Jsons.encodeToString(extend))
                params["ac"] = if (site.type == 0) "videolist" else "detail"
                params["t"] = tid
                params["pg"] = page
                val categoryContent = call(site, params, true)
                result.value = Jsons.decodeFromString<Result>(categoryContent)
                if (isEmptyResult(result.value)) {
                    log.warn("type${site.type} cate is Empty: {}", categoryContent)
                }
                // 获取封面图片
                fetchPic(site, result.value)
            }
        } catch (e: Exception) {
            log.error("${site.name} category error", e)
            result.value = Result(false)
        }
        result.value.list.forEach { it.site = site }
        return result.value
    }


    private fun post(site: Site, result: Result, quick: Boolean) {
        if (result.list.isEmpty()) {
            return
        }
        for (vod in result.list) vod.site = site
        if (quick) {
            search.value = quickSearch.value.copyAdd(Collect.create(result.list))
            if (quickSearch.value.isEmpty()) {
                search.value = quickSearch.value.copyAdd(Collect.all())
            }
            quickSearch.value[0].list.addAll(result.list)
        } else {
            search.value = search.value.copyAdd(Collect.create(result.list))
            if (search.value.isEmpty()) {
                search.value = search.value.copyAdd(Collect.all())
            }
            search.value[0].list.addAll(result.list)
        }
    }

    fun clearSearch() {
        search.value.clear()
        search.value.add(Collect.all())
    }

    fun clearQuickSearch() {
        quickSearch.value.clear()
        quickSearch.value.add(Collect.all())
    }
}


private fun call(site: Site, params: MutableMap<String, String>, limit: Boolean): String {
    val call: Call = if (fetchExt(site, params, limit).length <= 1000) {
        Http.newCall(site.api, site.header.toHeaders(), params)
    } else {
        Http.newCall(site.api, site.header.toHeaders(), Http.toBody(params))
    }

    return call.execute().use { response ->
        if (!response.isSuccessful) {
            throw IOException("Unexpected code $response")
        }

        val body = response.body

        body.string()
    }
}

private fun fetchExt(site: Site, params: MutableMap<String, String>, limit: Boolean): String {
    var extend: String = site.ext
    if (extend.startsWith("http")) extend = fetchExt(site)
    if (limit && extend.length > 1000) extend = extend.take(1000)
    if (extend.isNotEmpty()) params["extend"] = extend
    return extend
}

private fun fetchExt(site: Site): String {
    return Http.newCall(site.ext, site.header.toHeaders()).execute().use { res ->
        if (res.code != 200) return@use ""
        res.body.string().also { site.ext = it }
    }
}

private fun fetchPic(site: Site, result: Result): Result {
    if (result.list.isEmpty() || StringUtils.isNotBlank((result.list[0].vodPic))) return result
    val ids = ArrayList<String>()
    for (item in result.list) ids.add(item.vodId)
    val params: MutableMap<String, String> = mutableMapOf()
    params["ac"] = if (site.type == 0) "videolist" else "detail"
    params["ids"] = StringUtils.join(ids, ",")
    val response: String =
        Http.newCall(site.api, site.header.toHeaders(), params).execute().use { resp ->
            if (!resp.isSuccessful) {
                log.error("获取视频图片失败，状态码: ${resp.code}")
                return@use ""
            }
            resp.body.string()
        }
    result.list.clear()
    result.list.addAll(Jsons.decodeFromString<Result>(response).list)
    return result
}

private fun isEmptyResult(result: Result): Boolean {
    return result.list.isEmpty() && 
           result.types.isEmpty() && 
           result.filters.isEmpty() &&
           result.url.values.isEmpty()
}
