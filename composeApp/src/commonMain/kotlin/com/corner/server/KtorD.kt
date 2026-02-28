package com.corner.server

import com.corner.server.plugins.configureRouting
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.netty.handler.codec.http.HttpServerCodec
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("KtorD")

object KtorD {

    /**
     * KtorD服务器端口
     */
    var ports: Int = -1

    /**
     * KtorD服务器
     */
    var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    /**
     * KtorD服务器初始化
     */
    suspend fun init() {
        log.info("KtorD Init")
        ports = 9978
        do {
            try {
                server = embeddedServer(Netty, configure = {
                    this.connectors.add(EngineConnectorBuilder().apply {
                        port = ports
                    }
                    )
                    httpServerCodec = {
                        HttpServerCodec(
                            maxInitialLineLength * 10,
                            maxHeaderSize,
                            maxChunkSize
                        )
                    }
                }, module = Application::module)
                    .start(wait = false)
                break
            } catch (e: Exception) {
                log.error("start server e:", e)
                ++ports
                server?.stop()
            }
        } while (ports < 9999)
        log.info("KtorD init end port:{}", server!!.application.engine.resolvedConnectors().first().port)
    }

    /**
     * 停止 KtorD  服务器
     */
    fun stop() {
        log.info("KtorD stop")
        server?.stop()
    }
}

/**
 * KtorD 模块
 */
private fun Application.module() {
    // 跨域
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        allowHeader(HttpHeaders.Range)
        allowHeader("X-Requested-With")
        allowNonSimpleContentTypes = true
        anyHost() // 允许所有主机访问（开发环境）
        allowCredentials = false // 设置为false避免与具体Origin冲突
    }

    // 路由
    configureRouting()
}
