package com.corner.util.adblock

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("AdDomainInterceptor")

/**
 * 基于域名黑名单的广告拦截器
 * 对应 Android WebView 的 shouldInterceptRequest 逻辑
 */
class AdDomainInterceptor : Interceptor {
    
    companion object {
        /**
         * 空的 WebResourceResponse（用于阻止广告加载）
         */
        private val EMPTY_RESPONSE_BODY = byteArrayOf().toResponseBody(null)
    }
    
    // 广告域名列表（从 Api.ads 注入）
    @Volatile
    private var adDomains: List<String> = emptyList()
    
    /**
     * 更新广告域名列表
     */
    fun setAdDomains(domains: List<String>) {
        adDomains = domains
        log.info("Updated ad domain list, count: ${domains.size}")
    }
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        val host = request.url.host
        
        // 检查是否是广告域名
        if (host.isNotBlank() && isAdDomain(host)) {
            log.info("Blocked ad request: $url")
            
            // 返回空响应（类似 WebView 的 empty 对象）
            return Response.Builder()
                .request(request)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(204)  // No Content
                .message("Blocked by Ad Filter")
                .body(EMPTY_RESPONSE_BODY)
                .build()
        }
        
        // 非广告请求，正常放行
        return chain.proceed(request)
    }
    
    /**
     * 检查主机是否是广告域名
     */
    private fun isAdDomain(host: String): Boolean {
        if (adDomains.isEmpty()) return false
        
        return adDomains.any { pattern ->
            containOrMatch(host, pattern)
        }
    }
    
    /**
     * 域名匹配逻辑（支持多种模式）
     */
    private fun containOrMatch(host: String, pattern: String): Boolean {
        // 1. 正则表达式匹配
        if (isRegexPattern(pattern)) {
            return try {
                val matches = host.matches(pattern.toRegex())
                if (matches) {
                    log.debug("Regex matched: host=$host, pattern=$pattern")
                }
                matches
            } catch (e: Exception) {
                log.warn("Invalid regex pattern: $pattern", e)
                false
            }
        }
        
        // 2. 简单字符串包含匹配（忽略大小写）
        val contains = host.contains(pattern, ignoreCase = true)
        if (contains) {
            log.debug("Pattern matched: host=$host, pattern=$pattern")
        }
        return contains
    }
    
    /**
     * 判断是否为正则表达式模式
     */
    private fun isRegexPattern(pattern: String): Boolean {
        // 如果包含正则特殊字符，则视为正则表达式
        val regexSpecialChars = setOf('.', '*', '+', '?', '^', '$', '{', '}', '(', ')', '|', '[', ']', '\\')
        return pattern.any { it in regexSpecialChars }
    }
}
