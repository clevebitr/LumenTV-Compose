package com.corner.catvodcore.loader

import com.corner.catvodcore.Constant
import com.corner.catvodcore.config.ApiConfig
import com.corner.catvodcore.util.Http
import com.corner.catvodcore.util.Paths
import com.corner.catvodcore.util.Urls
import com.corner.catvodcore.util.Utils
import com.corner.util.thisLogger
import com.github.catvod.crawler.Spider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.lang3.StringUtils
import java.io.File
import java.lang.reflect.Method
import java.net.URLClassLoader
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

object JarLoader {
    private val log = thisLogger()
    private val loaders: ConcurrentHashMap<String, URLClassLoader> by lazy { ConcurrentHashMap() }

    //proxy method
    private val methods: ConcurrentHashMap<String, Method> by lazy { ConcurrentHashMap() }
    private val spiders: ConcurrentHashMap<String, Spider> by lazy { ConcurrentHashMap() }

    var recent: String? = null

    fun clear() {
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

    private const val MAX_RETRY_COUNT = 30

    /**
     * 改为迭代方式，不要使用递归，会出现问题
     * */

    fun loadJar(key: String, spider: String) {
        var currentRetryCount = 0
        var currentSpider = spider
        var currentProcessedUrl: String? = null  // 初始化为null

        while (true) {
            try {
                if (StringUtils.isBlank(currentSpider)) return

                val texts = currentSpider.split(Constant.md5Split)
                val md5 = if (texts.size <= 1) "" else texts[1].trim()
                val jar = texts[0]

                when {
                    md5.isNotEmpty() && Utils.equals(parseJarUrl(jar), md5) -> {
                        load(key, Paths.jar(parseJarUrl(jar)))
                        return
                    }

                    jar.startsWith("file") -> {
                        load(key, Paths.local(jar))
                        return
                    }

                    jar.startsWith("http") -> {
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
     * 如果在配置文件种使用的相对路径， 下载的时候使用的全路径 如果的判断md5是否一致的时候使用相对路径 就会造成重复下载
     */
    private fun parseJarUrl(jar: String): String {
        if (jar.startsWith("file") || jar.startsWith("http")) return jar
        return Urls.convert(ApiConfig.api.url!!, jar)
    }

    private fun load(key: String, jar: File) {
        log.debug("load jar {}", jar)
        loaders[key] = URLClassLoader(arrayOf(jar.toURI().toURL()), this.javaClass.classLoader)
        putProxy(key)
        invokeInit(key)
    }

    private fun putProxy(key: String) {
        try {
            val clazz = loaders[key]?.loadClass(Constant.catVodProxy)
            val method = clazz!!.getMethod("proxy", Map::class.java)
            methods[key] = method
        } catch (e: Exception) {
//            e.printStackTrace()
        }
    }

    private fun invokeInit(key: String) {
        try {
            val clazz = loaders[key]?.loadClass(Constant.catVodInit)
            val method = clazz?.getMethod("init")
            method?.invoke(clazz)
        } catch (e: Exception) {
//            e.printStackTrace()
        }
    }

    fun getSpider(key: String, api: String, ext: String, jar: String): Spider {
        try {
            val jaKey = Utils.md5(jar)
            val spKey = jaKey + key
            if (spiders.containsKey(spKey)) return spiders[spKey]!!
            if (loaders[jaKey] == null) loadJar(jaKey, jar)
            val loader = loaders[jaKey] ?: throw IllegalStateException("Loader is null for JAR: $jar")
            val classPath = "${Constant.catVodSpider}.${api.replace("csp_", "")}"
            val spider: Spider =
                loader!!.loadClass(classPath).getDeclaredConstructor()
                    .newInstance() as Spider
            spider.init(ext)
            spiders[spKey] = spider
            return spider
        } catch (e: Exception) {
            e.printStackTrace()
            return Spider()
        }
    }

    private fun download(jar: String): File {
        val jarPath = Paths.jar(jar)
        log.debug("download jar file {} to:{}", jar, jarPath)

        return Http.Get(jar).execute().use { response ->
            val body = response.body ?: throw java.io.IOException("Empty response body")
            Paths.write(jarPath, body.bytes())
        }
    }

    fun proxyInvoke(params: Map<String, String>): Array<Any>? {
        return try {
            val md5 = Utils.md5(recent ?: "")
            val proxy = methods[md5]

            // 过滤或验证参数值
            val safeParams = params.mapValues { entry ->
                // 检查是否是有效的 base64 字符串
                if (isValidBase64(entry.value)) {
                    entry.value
                } else {
                    entry.value // 保持原值，不尝试解码
                }
            }

            proxy?.invoke(null, safeParams) as Array<Any>
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun isValidBase64(str: String): Boolean {
        return try {
            Base64.getDecoder().decode(str)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    fun SetRecent(jar: String?) {
        recent = jar
    }
}

