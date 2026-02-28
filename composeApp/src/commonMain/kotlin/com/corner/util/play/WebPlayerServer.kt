package com.corner.util.play

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private val log = LoggerFactory.getLogger("WebPlayerServer")

object WebPlayerServer {
    private var server: EmbeddedServer<*, *>? = null
    private val isRunning = AtomicBoolean(false)
    private val portCounter = AtomicInteger(9000)
    private var currentPort = 0

    // 存储当前播放信息
    private var currentMediaInfo: MediaInfo? = null

    data class MediaInfo(
        val url: String,
        val title: String,
        val episodeNumber: Int? = null
    )

    fun start(preferredPort: Int = 0): Int {
        if (isRunning.get()) {
            log.warn("Web播放器服务器已在运行，端口: $currentPort")
            return currentPort
        }

        val port = if (preferredPort > 0) preferredPort else {
            // 检查端口是否可用，如果不可用则递增
            var testPort = portCounter.get()
            while (!isPortAvailable(testPort) && testPort < 9100) {
                testPort++
            }
            portCounter.set(testPort)
            portCounter.getAndIncrement()
        }

        try {
            server = embeddedServer(Netty, port = port) {
                install(WebSockets)

                routing {
                    // 主播放页面
                    get("/player") {
                        val mediaUrl = call.parameters["url"]
                        val title = call.parameters["title"] ?: "视频播放"

                        if (mediaUrl == null) {
                            call.respond(HttpStatusCode.BadRequest, "缺少视频URL参数")
                            return@get
                        }

                        // 更新当前媒体信息
                        currentMediaInfo = MediaInfo(
                            url = mediaUrl,
                            title = title,
                            episodeNumber = call.parameters["episode"]?.toIntOrNull()
                        )

                        // 使用外部HTML模板
                        val templateContent = this::class.java.classLoader
                            .getResourceAsStream("web_player_template.html")
                            ?.bufferedReader()
                            ?.readText()
                            ?: throw IllegalStateException("找不到web_player_template.html模板文件")

                        // 替换模板中的占位符
                        val htmlContent = templateContent
                            .replace("{{title}}", title)
                            .replace("{{mediaUrl}}", mediaUrl)

                        call.respondText(htmlContent, ContentType.Text.Html)
                    }

                    // 健康检查端点
                    get("/health") {
                        call.respondText("OK", ContentType.Text.Plain)
                    }

                    // 获取当前播放信息
                    get("/info") {
                        val info = currentMediaInfo?.let {
                            """
                            {
                                "title": "${it.title}",
                                "url": "${it.url}",
                                "episode": ${it.episodeNumber ?: "null"}
                            }
                            """.trimIndent()
                        } ?: "{}"

                        call.respondText(info, ContentType.Application.Json)
                    }
                }
            }

            server?.start(wait = false)
            currentPort = port
            isRunning.set(true)

            log.info("Web播放器服务器启动成功，端口: $port")
            return port

        } catch (e: Exception) {
            log.error("启动Web播放器服务器失败", e)
            throw e
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            try {
                server?.stop(1000, 5000)
                server = null
                currentMediaInfo = null
                // 重置端口计数器
                portCounter.set(9000)
                currentPort = 0
                log.info("Web播放器服务器已停止，端口计数器已重置")
            } catch (e: Exception) {
                log.error("停止Web播放器服务器时出错", e)
            }
        }
    }

    fun isServerRunning(): Boolean = isRunning.get()

    fun getCurrentPort(): Int = currentPort

    fun updateMediaInfo(url: String, title: String, episodeNumber: Int? = null) {
        currentMediaInfo = MediaInfo(url, title, episodeNumber)
    }

    // 通过浏览器打开播放页面
    fun openInBrowser(
        mediaUrl: String,
        title: String = "视频播放",
        episodeNumber: Int? = null
    ) {
        try {
            // 确保服务器已启动
            if (!isRunning.get()) {
                start()
            }

            updateMediaInfo(mediaUrl, title, episodeNumber)

            val browserUrl = "http://localhost:$currentPort/player?url=${mediaUrl.encodeURLParameter()}&title=${title.encodeURLParameter()}"
                .let { url ->
                    episodeNumber?.let { "$url&episode=$it" } ?: url
                }

            java.awt.Desktop.getDesktop().browse(java.net.URI(browserUrl))
            log.info("在浏览器中打开播放页面: $browserUrl")

        } catch (e: Exception) {
            log.error("在浏览器中打开播放页面失败", e)
            throw e
        }
    }
}

// 扩展函数用于URL编码
private fun String.encodeURLParameter(): String {
    return java.net.URLEncoder.encode(this, "UTF-8")
}

// 检查端口是否可用
private fun isPortAvailable(port: Int): Boolean {
    return try {
        val socket = java.net.ServerSocket(port)
        socket.close()
        true
    } catch (e: Exception) {
        false
    }
}