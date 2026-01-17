package com.corner.util.update

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

suspend fun fetchChangelogFromUrl(url: String): String {
    val client = HttpClient()
    try {
        val response = client.get(url)
        return response.bodyAsText()
    } finally {
        client.close()
    }
}