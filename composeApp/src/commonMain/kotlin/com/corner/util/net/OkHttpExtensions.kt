package com.corner.util.net

import com.corner.util.net.interceptor.Interceptors.adDomainInterceptor
import com.corner.util.net.interceptor.Interceptors.deflateInterceptor
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
        .sslSocketFactory(Http.getSSLSocketFactory(), Http.getX509TrustManager()!!)
        .hostnameVerifier(Http.getHostnameVerifier())
        .addInterceptor(deflateInterceptor)
        .addInterceptor(adDomainInterceptor)
}

/**
 * 快速创建带默认配置的OkHttpClient实例
 */
fun createDefaultOkHttpClient(): OkHttpClient {
    return OkHttpClient.Builder().withDefaultConfig().build()
}
