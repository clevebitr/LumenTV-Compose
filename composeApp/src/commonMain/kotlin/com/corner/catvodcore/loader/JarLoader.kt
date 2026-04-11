package com.corner.catvodcore.loader

import com.corner.catvodcore.Constant
import com.corner.catvodcore.config.ApiConfig
import com.corner.ui.scene.SnackBar
import com.corner.util.net.Http
import com.corner.util.io.Paths
import com.corner.util.net.Urls
import com.corner.util.net.Utils
import com.corner.util.thisLogger
import com.github.catvod.crawler.Spider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.lang3.StringUtils
import java.io.File
import java.lang.reflect.Method
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap

object JarLoader {
    private val log = thisLogger()

    /**
     * jar包加载器
     * */
    private val loaders: ConcurrentHashMap<String, URLClassLoader> by lazy { ConcurrentHashMap() }

    /**
     * 缓存
     */
    private val methods: ConcurrentHashMap<String, Method> by lazy { ConcurrentHashMap() }

    /**
     * 爬虫缓存
     */
    private val spiders: ConcurrentHashMap<String, Spider> by lazy { ConcurrentHashMap() }

    /**
     * 最近使用的jar包
     */
    var recent: String? = null

    /**
     * 最大重试次数
     */
    private const val MAX_RETRY_COUNT = 30

    /**
     * 清空加载的jar包
     */
    fun clear() {
        log.info("clear jar loader")
        spiders.values.forEach { spider ->
            CoroutineScope(Dispatchers.IO).launch {
                spider.destroy()
                spiders.clear()
            }
        }
        loaders.clear()
        methods.clear()
        recent = null
    }

    /**
     * 加载jar包
     * @param key
     * @param spider  jar路径
     * */
    fun loadJar(key: String, spider: String) {
        var currentRetryCount = 0
        var currentSpider = spider
        var currentProcessedUrl: String? = null  // 初始化为null

        while (true) {
            try {
                if (StringUtils.isBlank(currentSpider)) return

                val texts = currentSpider.split(Constant.MD5_SPLIT)
                val md5 = if (texts.size <= 1) "" else texts[1].trim()
                val jar = texts[0]
                log.debug("<loadJar>md5 is {}", md5)
                log.debug("<loadJar>texts is {}", texts)
                when {
                    md5.isNotEmpty() && Utils.equals(parseJarUrl(jar), md5) -> {
                        log.info("md5校验成功，以md5方式加载...")
                        load(key, Paths.jar(parseJarUrl(jar)))
                        return
                    }

                    jar.startsWith("file") -> {
                        log.info("jar文件已存在，以文件方式加载...")
                        load(key, Paths.local(jar))
                        return
                    }

                    jar.startsWith("http") -> {
                        log.info("jar文件不存在，以http方式加载...")
                        load(key, download(jar))
                        return
                    }

                    else -> {
                        currentProcessedUrl = parseJarUrl(jar)
                        if (currentProcessedUrl == jar) {
                            if (currentRetryCount < MAX_RETRY_COUNT) {
                                log.warn("路径解析失败，尝试第${currentRetryCount + 1}次重试: $jar")
                                currentRetryCount++
                                Thread.sleep(1000)
                                continue
                            }
                            throw IllegalStateException("无法解析的路径格式: $jar (已尝试$MAX_RETRY_COUNT 次)")
                        } else {
                            currentRetryCount = 0
                            currentSpider = currentProcessedUrl
                            continue
                        }
                    }
                }
            } catch (e: Exception) {
                log.error(
                    """
                        加载失败！
                        原始路径: $currentSpider
                        解析后路径: ${currentProcessedUrl ?: "N/A"}
                        重试次数: $currentRetryCount/$MAX_RETRY_COUNT
                        错误类型: ${e.javaClass.simpleName}
                    """.trimIndent(), e
                )

                if (currentRetryCount < MAX_RETRY_COUNT) {
                    currentRetryCount++
                    Thread.sleep(1000)
                } else {
                    throw e
                }
            }
        }
    }

    /**
     * 解析jar路径
     *
     * 如果在配置文件种使用的相对路径， 下载的时候使用的全路径 如果判断的md5是否一致的时候使用相对路径 就会造成重复下载
     */
    private fun parseJarUrl(jar: String): String {
        if (jar.startsWith("file") || jar.startsWith("http")) return jar
        return Urls.convert(ApiConfig.api.url!!, jar)
    }

    /**
     * 加载jar包
     * @param key
     * @param jar  jar文件
     */
    private fun load(key: String, jar: File) {
        log.debug("load jar {},jaKey {}", jar, key)
        loaders[key] = URLClassLoader(arrayOf(jar.toURI().toURL()), this.javaClass.classLoader)
        putProxy(key)
        invokeInit(key)
    }

    /**
     * 添加代理方法
     * @param key
     */
    private fun putProxy(key: String) {
        try {
            val clazz = loaders[key]?.loadClass(Constant.catVodProxy)
            val method = clazz!!.getMethod("proxy", Map::class.java)
            methods[key] = method
        } catch (e: Exception) {
            log.error("添加Proxy失败: key={}", key, e)
        }
    }

    /**
     * 调用初始化方法
     * @param key
     */
    private fun invokeInit(key: String) {
        try {
            val clazz = loaders[key]?.loadClass(Constant.catVodInit)
            val method = clazz?.getMethod("init")
            method?.invoke(clazz)
        } catch (e: ClassNotFoundException) {
            log.debug("Init类不存在: {}", Constant.catVodInit)
        } catch (e: Exception) {
            log.error("调用Init失败: key={}", key, e)
        }
    }

    /**
     * 获取爬虫
     * @param key
     * @param api
     * @param ext
     * @param jar
     * @return
     */
    fun getSpider(key: String, api: String, ext: String, jar: String): Spider {
        try {
            val jaKey = Utils.md5(jar)
            val spKey = jaKey + key
            if (spiders.containsKey(spKey)) return spiders[spKey]!!
            if (loaders[jaKey] == null) loadJar(jaKey, jar)
            val loader = loaders[jaKey] ?: throw IllegalStateException("Loader is null for JAR: $jar")
            val classPath = "${Constant.catVodSpider}.${api.replace("csp_", "")}"
            val spider: Spider =
                loader.loadClass(classPath).getDeclaredConstructor()
                    .newInstance() as Spider
            spider.init(ext)
            spiders[spKey] = spider
            return spider
        } catch (e: ClassNotFoundException) {
            log.debug("Spider类不存在: {}, api: {}", e.message, api)
            return Spider()
        } catch (e: java.net.ConnectException) {
            // 网络连接错误，通常是代理问题
            log.error("加载Spider时网络连接失败: key={}, api={}", key, api, e)
            SnackBar.postMsg(
                "爬虫加载失败：无法连接到网络\n\n可能原因：\n1. 代理服务器未启动（当前配置: ${com.corner.bean.SettingStore.getSettingItem(com.corner.bean.SettingType.PROXY)}）\n2. 网络连接异常\n3. 目标服务器不可达\n\n建议：检查代理设置或关闭代理后重试",
                type = SnackBar.MessageType.ERROR
            )
            return Spider()
        } catch (e: IllegalArgumentException) {
            // URL 格式错误（如端口为 -1），通常是代理配置问题
            log.error("加载Spider时URL格式错误: key={}, api={}, 错误: {}", key, api, e.message, e)
            SnackBar.postMsg(
                "爬虫加载失败：URL格式错误\n\n错误信息: ${e.message}\n\n可能原因：\n1. 代理配置不正确\n2. Spider内部生成的URL无效\n\n建议：关闭代理后重试，或联系开发者修复Spider",
                type = SnackBar.MessageType.ERROR
            )
            return Spider()
        } catch (e: Exception) {
            log.error("加载Spider失败: key={}, api={}", key, api, e)
            // 判断是否是网络相关错误
            val isNetworkError = e.cause is java.net.ConnectException || 
                                e.cause is java.net.SocketTimeoutException ||
                                e.message?.contains("Failed to connect", ignoreCase = true) == true
            
            if (isNetworkError) {
               SnackBar.postMsg(
                    "爬虫加载失败：网络请求错误\n\n错误信息: ${e.message}\n\n请检查网络连接和代理设置",
                    type = SnackBar.MessageType.ERROR
                )
            }
            return Spider()
        }
    }

    /**
     * 下载jar包
     * @param jar
     * @return
     */
    private fun download(jar: String): File {
        val jarPath = Paths.jar(jar)
        log.debug("download jar file {} to:{}", jar, jarPath)

        return Http.get(jar).execute().use { response ->
            val body = response.body
            Paths.write(jarPath, body.bytes())
        }
    }

    /**
     * 代理调用
     * @param params
     * @return
     */
    fun proxyInvoke(params: Map<String, String>): Array<Any>? {
        return try {
            val md5 = Utils.md5(recent ?: "")
            val proxy = methods[md5]

            if (proxy == null) {
                log.error("未找到代理方法，md5: $md5")
                return null
            }

            val safeParams = params.toMap()

            val result = proxy.invoke(null, safeParams)
            return if (result != null && result::class.java.isArray) {
                (result as Array<*>).map { it as Any }.toTypedArray()
            } else {
                null
            }

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 设置最近使用的jar包
     */
    fun setRecentJar(jar: String?) {
        recent = jar
    }
}

