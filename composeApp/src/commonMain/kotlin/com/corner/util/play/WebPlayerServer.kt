package com.corner.util.play

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private val log = LoggerFactory.getLogger("WebPlayerServer")

object WebPlayerServer {
    private var server: EmbeddedServer<*, *>? = null // 使用泛型通配符
    private val scope = CoroutineScope(Dispatchers.IO + Job())
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

        val port = if (preferredPort > 0) preferredPort else portCounter.getAndIncrement()

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

                        // 使用HTML流生成器替代respondHtml
                        val htmlContent = buildString {
                            append("""
                                <!DOCTYPE html>
                                <html>
                                <head>
                                    <title>$title</title>
                                    <meta charset="UTF-8">
                                    <style>
                                        body {
                                            margin: 0;
                                            padding: 0;
                                            background: #000;
                                            font-family: Arial, sans-serif;
                                        }
                                        #player-container {
                                            width: 100vw;
                                            height: 100vh;
                                            display: flex;
                                            justify-content: center;
                                            align-items: center;
                                        }
                                        #player {
                                            width: 100%;
                                            height: 100%;
                                            max-width: 100%;
                                            max-height: 100%;
                                        }
                                        .video-title {
                                            position: absolute;
                                            top: 20px;
                                            left: 20px;
                                            color: white;
                                            background: rgba(0,0,0,0.7);
                                            padding: 10px 15px;
                                            border-radius: 5px;
                                            font-size: 18px;
                                            z-index: 1000;
                                        }
                                    </style>
                                </head>
                                <body>
                                    <div class="video-title">$title</div>
                                    <div id="player-container">
                                        <video id="player" controls autoplay>
                                            <source src="$mediaUrl" type="${when {
                                mediaUrl.endsWith(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
                                mediaUrl.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
                                else -> "video/mp4"
                            }}">
                                        </video>
                                    </div>
                                    <script>
                                        let ws = null;
                                        let retryCount = 0;
                                        const maxRetries = 5;

                                        function connectWebSocket() {
                                            if (retryCount >= maxRetries) {
                                                console.error('WebSocket连接失败，达到最大重试次数');
                                                return;
                                            }

                                            try {
                                                ws = new WebSocket('ws://localhost:8888');

                                                ws.onopen = function(event) {
                                                    console.log('WebSocket连接已建立');
                                                    retryCount = 0;
                                                };

                                                ws.onclose = function(event) {
                                                    console.log('WebSocket连接已关闭');
                                                    // 尝试重连
                                                    setTimeout(() => {
                                                        retryCount++;
                                                        connectWebSocket();
                                                    }, 2000);
                                                };

                                                ws.onerror = function(error) {
                                                    console.error('WebSocket错误:', error);
                                                };
                                            } catch (error) {
                                                console.error('WebSocket连接异常:', error);
                                                setTimeout(() => {
                                                    retryCount++;
                                                    connectWebSocket();
                                                }, 2000);
                                            }
                                        }

                                        // 初始化WebSocket连接
                                        connectWebSocket();

                                        const video = document.getElementById('player');

                                        video.addEventListener('play', function() {
                                            if (ws && ws.readyState === WebSocket.OPEN) {
                                                ws.send('PLAYBACK_STARTED');
                                            }
                                        });

                                        video.addEventListener('ended', function() {
                                            if (ws && ws.readyState === WebSocket.OPEN) {
                                                ws.send('PLAYBACK_FINISHED');
                                            }
                                        });

                                        video.addEventListener('error', function(e) {
                                            console.error('视频播放错误:', e);
                                        });

                                        // 页面卸载时关闭WebSocket
                                        window.addEventListener('beforeunload', function() {
                                            if (ws) {
                                                ws.close();
                                            }
                                        });
                                    </script>
                                </body>
                                </html>
                            """.trimIndent())
                        }

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
                log.info("Web播放器服务器已停止")
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