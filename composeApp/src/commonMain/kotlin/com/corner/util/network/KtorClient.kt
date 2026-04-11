package com.corner.util.network

/**
 *  Ktor Client Http客户端
 * */

import com.github.catvod.crawler.Spider.Companion.safeDns
import com.github.catvod.net.OkhttpInterceptor
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import okhttp3.Dispatcher
import org.slf4j.LoggerFactory
import java.net.Proxy
import java.util.concurrent.TimeUnit

class KtorClient {
    companion object {
        val client = HttpClient(OkHttp)
        private val log = LoggerFactory.getLogger(KtorClient::class.java)

        /**
         * 创建自定义配置的HttpClient实例
         * 所有配置与OkHttp原生客户端保持一致
         */
        fun createHttpClient(block: HttpClientConfig<OkHttpConfig>.() -> Unit = {}) = HttpClient(OkHttp) {
            engine {
                config {
                    // 基础配置
                    followRedirects(true)
                    connectTimeout(10, TimeUnit.SECONDS)
                    readTimeout(10, TimeUnit.SECONDS)
                    writeTimeout(10, TimeUnit.SECONDS)
                    
                    // 代理配置（与OkHttp同步）
                    proxy(getProxy())
                    
                    // Dispatcher配置（控制并发）
                    dispatcher(Dispatcher().apply {
                        maxRequests = 64
                        maxRequestsPerHost = 5
                    })
                    
                    // DNS配置（支持DoH）
                    dns(safeDns())
                    
                    // SSL配置
                    sslSocketFactory(
                        com.corner.util.net.Http.getSSLSocketFactory(),
                        com.corner.util.net.Http.getX509TrustManager()!!
                    )
                    hostnameVerifier(com.corner.util.net.Http.getHostnameVerifier())
                    
                    // 拦截器（与OkHttp同步）
                    addInterceptor(OkhttpInterceptor())
                }
            }
            
            // Ktor插件配置
            install(HttpRequestRetry) {
                maxRetries = 1
                delayMillis { 1000L }
            }
            
            // 自定义配置扩展
            block()
        }

        /**
         * 获取代理配置（委托给全局 ProxyManager）
         */
        fun getProxy(): Proxy {
            return com.corner.util.net.ProxyManager.getProxy()
        }
        
        /**
         * 清除代理测试缓存（当用户修改代理配置时调用）
         */
        fun clearProxyCache() {
            com.corner.util.net.ProxyManager.clearCache()
        }
    }
}