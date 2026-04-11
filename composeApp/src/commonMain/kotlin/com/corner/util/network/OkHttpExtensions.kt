package com.corner.util.network

import com.corner.util.net.Http
import com.github.catvod.net.OkhttpInterceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * OkHttpClient.Builder扩展函数
 * 应用默认的代理和SSL配置，确保所有HTTP客户端行为一致
 */
fun OkHttpClient.Builder.withDefaultConfig(): OkHttpClient.Builder {
    return this
        .followRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .proxy(KtorClient.getProxy())  // 使用统一的代理配置
        .sslSocketFactory(Http.getSSLSocketFactory(), Http.getX509TrustManager()!!)
        .hostnameVerifier(Http.getHostnameVerifier())
        .addInterceptor(OkhttpInterceptor())
}

/**
 * 快速创建带默认配置的OkHttpClient实例
 */
fun createDefaultOkHttpClient(): OkHttpClient {
    return OkHttpClient.Builder().withDefaultConfig().build()
}
