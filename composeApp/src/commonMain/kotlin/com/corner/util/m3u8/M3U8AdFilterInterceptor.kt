package com.corner.util.m3u8

import com.corner.util.settings.SettingStore
import com.corner.ui.scene.SnackBar
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.slf4j.LoggerFactory
import java.net.URI


private val log = LoggerFactory.getLogger("M3U8Interceptor")

class M3U8AdFilterInterceptor {
    class Interceptor() : okhttp3.Interceptor {
        override fun intercept(chain: okhttp3.Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url.toString()

            // 只拦截m3u8请求
            if (!url.endsWith(".m3u8", ignoreCase = true)) {
                return chain.proceed(request)
            }
            log.info("拦截请求，URL: $url")

            val response = chain.proceed(request)
            if (!response.isSuccessful) return response

            val originalContent = response.body.string()

            // 1. 转换相对路径
            val baseUrl = url.substringBeforeLast("/") + "/"
            val absolutePathContent = originalContent.lines().joinToString("\n") { line ->
                when {
                    line.startsWith("#") || line.isBlank() -> line
                    line.startsWith("http") -> line
                    line.startsWith("/") -> URI(baseUrl).resolve(line).toString()
                    else -> "$baseUrl$line"
                }
            }

            // 2. 每次请求时获取配置并创建过滤器
            val config = SettingStore.getM3U8FilterConfig()
            val filter = M3U8Filter(config)
            
            val filteredContent = if (SettingStore.isAdFilterEnabled()) {
                filter.safelyProcessM3u8(url, absolutePathContent)
            } else {
                absolutePathContent
            }

            val adCount = filter.getFilteredAdCount()
            if (adCount > 0) {
                log.info("广告过滤完成，共过滤 $adCount 条广告")
                SnackBar.postMsg("广告过滤完成，共过滤 $adCount 条广告")
            }

            // 3. 直接返回处理后的内容（不再走代理）
            return response.newBuilder()
                .body(filteredContent.toResponseBody(response.body.contentType()))
                .build()
        }
    }
}