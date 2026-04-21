package com.corner.server

import com.corner.server.plugins.configureRouting
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.websocket.*
import io.netty.handler.codec.http.HttpServerCodec
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger("KtorD")

object KtorD {

    /**
     * 默认服务器端口（常量）
     */
    private const val DEFAULT_PORT = 9978
    
    /**
     * 最大尝试端口
     */
    private const val MAX_PORT = 9999

    /**
     * KtorD服务器实际端口（运行时更新）
     * -1 表示服务器未启动
     */
    @Volatile
    private var actualPort: Int = -1

    /**
     * KtorD服务器端口（对外暴露的接口）
     * 如果服务器已启动，返回实际端口；否则返回默认端口
     * 注意：不要直接访问这个字段，使用 getPort() 方法
     */
    @JvmStatic
    fun getPort(): Int {
        return if (actualPort > 0) actualPort else DEFAULT_PORT
    }

    /**
     * 兼容旧代码的字段（不推荐直接使用）
     * Spider JAR 包可能会通过反射访问这个字段
     * 注意：此字段会在服务器启动后自动更新为实际端口
     * 
     * 重要：不能使用 private set，否则 @JvmField 会编译失败
     * Java 反射需要直接访问这个字段，所以必须是公开的
     */
    @Deprecated("Use getPort() method instead", ReplaceWith("getPort()"))
    @JvmField
    @Volatile
    var ports: Int = DEFAULT_PORT  // 默认值，服务器启动后会自动更新

    /**
     * KtorD服务器
     */
    var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    /**
     * KtorD服务器初始化
     * 尝试从 DEFAULT_PORT 开始，依次递增直到找到可用端口
     */
    suspend fun init() {
        log.info("KtorD Init, trying to start on port {}...", DEFAULT_PORT)
        var tryPort = DEFAULT_PORT
        do {
            try {
                server = embeddedServer(Netty, configure = {
                    this.connectors.add(EngineConnectorBuilder().apply {
                        port = tryPort
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
                
                // 服务器启动成功，记录实际端口
                actualPort = server!!.application.engine.resolvedConnectors().first().port
                ports = actualPort  // 同步更新旧字段（兼容反射访问）
                
                if (actualPort != DEFAULT_PORT) {
                    log.warn("Default port {} is occupied, using port {} instead", DEFAULT_PORT, actualPort)
                }
                log.info("KtorD started successfully on port: {}", actualPort)
                break
            } catch (e: Exception) {
                // 只处理端口占用异常，其他异常直接抛出
                val isPortOccupied = e.message?.contains("BindException") == true || 
                                   e.message?.contains("Address already in use") == true ||
                                   e.cause?.javaClass?.name?.contains("BindException") == true
                
                if (isPortOccupied) {
                    log.debug("Port {} is occupied, trying next port...", tryPort)
                    ++tryPort
                    server?.stop()
                } else {
                    // 非端口占用错误，直接抛出
                    log.error("KtorD 启动失败（非端口占用）", e)
                    throw e
                }
            }
        } while (tryPort < MAX_PORT)
        
        if (actualPort <= 0) {
            val errorMsg = "无法启动本地服务器，端口 ${DEFAULT_PORT}-${MAX_PORT} 均被占用"
            log.error(errorMsg)
            throw IllegalStateException(errorMsg)
        }
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
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
    
    install(WebSockets) {
        pingPeriod = null
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    
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
        allowHeader("Upgrade")  // WebSocket 升级请求
        allowHeader("Connection")
        allowHeader("Sec-WebSocket-Key")
        allowHeader("Sec-WebSocket-Version")
        allowHeader("Sec-WebSocket-Extensions")
        allowNonSimpleContentTypes = true

        anyHost()
        
        allowCredentials = false
    }

    configureRouting()
}
