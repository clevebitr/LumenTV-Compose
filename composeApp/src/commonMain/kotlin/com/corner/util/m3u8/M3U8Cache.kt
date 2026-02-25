package com.corner.util.m3u8

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object M3U8Cache {
    private val cache = ConcurrentHashMap<String, String>()
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

    // 默认过期时间：5分钟（毫秒）
    private const val DEFAULT_EXPIRY_TIME = 5L * 60 * 1000

    fun put(content: String): String {
        val id = UUID.randomUUID().toString()
        cache[id] = content
        scheduleCleanup(id)
        return id
    }

    fun get(id: String): String? = cache[id]

    fun get(id: String, expiryTimeMs: Long): String? {
        val content = cache[id]
        if (content != null) {
            // 内容存在时，重新设置过期时间
            scheduleCleanup(id, expiryTimeMs)
        }
        return content
    }

    private fun scheduleCleanup(id: String, delay: Long = DEFAULT_EXPIRY_TIME) {
        // 取消之前可能存在的清理任务
        // 这里我们简单地让旧的任务自然执行完，或者可以通过更复杂的方式来追踪任务

        // 计划在指定延迟后清理缓存项
        scheduler.schedule({
            cache.remove(id)
        }, delay, TimeUnit.MILLISECONDS)
    }

    // 清理所有资源的方法
    fun cleanup() {
        scheduler.shutdown()
        cache.clear()
    }
}