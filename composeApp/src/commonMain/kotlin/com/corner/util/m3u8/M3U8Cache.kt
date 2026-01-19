package com.corner.util.m3u8

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object M3U8Cache {
    private val cache = ConcurrentHashMap<String, String>()

    fun put(content: String): String {
        val id = UUID.randomUUID().toString()
        cache[id] = content
        return id
    }

    fun get(id: String): String? = cache.remove(id) // 取完后自动清除
}