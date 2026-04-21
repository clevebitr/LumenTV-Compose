package com.corner.util.update

import com.corner.util.net.KtorClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

suspend fun fetchChangelogFromUrl(url: String): String {
    // 使用带代理配置的HTTP客户端
    val client = KtorClient.createHttpClient()
    try {
        val response = client.get(url)
        return response.bodyAsText()
    } finally {
        client.close()
    }
}