package com.github.catvod.net

import okhttp3.ResponseBody
import okio.BufferedSource
import okio.buffer
import okio.source
import java.io.IOException


class OkhttpInterceptor : okhttp3.Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val request = chain.request()

        val finalRequest = if (request.header("User-Agent").isNullOrEmpty()) {
            request.newBuilder()
                .header("User-Agent", com.corner.util.Constants.ChromeUserAgent)
                .build()
        } else {
            request
        }

        val response = chain.proceed(finalRequest)

        // 原有的 deflate 解压逻辑
        val encoding = response.header("Content-Encoding")
        if (encoding == null || encoding != "deflate") return response

        val `is` = java.util.zip.InflaterInputStream(
            response.body.byteStream(),
            java.util.zip.Inflater(true)
        )

        return response.newBuilder()
            .headers(response.headers)
            .body(object : ResponseBody() {
                override fun contentType(): okhttp3.MediaType? = response.body.contentType()
                override fun contentLength(): Long = response.body.contentLength()
                override fun source(): BufferedSource = `is`.source().buffer()
            })
            .build()
    }
}
