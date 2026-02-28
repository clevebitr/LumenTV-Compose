package com.corner.server.plugins

import cn.hutool.core.io.file.FileNameUtil
import com.corner.server.logic.proxy
import com.corner.ui.scene.SnackBar
import com.corner.util.m3u8.M3U8Cache
import com.corner.util.toSingleValueMap
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

fun Application.configureRouting() {

    routing {
        // 处理 CORS 预检请求
        options("/video/proxy") {
            call.response.header("Access-Control-Allow-Origin", call.request.headers["Origin"] ?: "*")
            call.response.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            call.response.header("Access-Control-Allow-Headers", "*")
            call.response.header("Access-Control-Allow-Credentials", "true")
            call.respond(HttpStatusCode.OK)
        }

        /**
         * 静态资源
         */
        staticResources("/static", "assets") {
            contentType {
                val suffix = FileNameUtil.getSuffix(it.path)
                when (suffix) {
                    "js" -> ContentType.Text.JavaScript
                    "jsx" -> ContentType.Application.JavaScript
                    "html" -> ContentType.Text.Html
                    "txt" -> ContentType.Text.Plain
                    "htm" -> ContentType.Text.Html
                    else -> null
                }
            }
        }

        /**
         * 根目录
         */
        get("/") {
            val htmlFile = File("src/commonMain/resources/LumenTV Proxy Placeholder Webpage.html")
            if (htmlFile.exists()) {
                call.respondFile(htmlFile)
            } else {
                call.respondText("文件未找到", status = HttpStatusCode.NotFound)
            }
        }

        /**
         * 弹窗消息
         */
        get("/postMsg") {
            val msg = call.request.queryParameters["msg"]
            if (msg?.isBlank() == true) {
                call.respond(HttpStatusCode.MultiStatus, "消息不可为空")
                return@get
            }
            SnackBar.postMsg(msg!!)
        }

        /**
         * 代理
         * 播放器必须支持range请求 否则会返回完整资源 导致拨动进度条加载缓慢
         */
        get("/proxy") {
            val parameters = call.request.queryParameters
            val paramMap = parameters.toSingleValueMap().toMutableMap()
            paramMap.putAll(call.request.headers.toSingleValueMap())
            try {
                val objects: Array<Any> = proxy(paramMap) ?: arrayOf()
                if (objects.isEmpty()) {
                    errorResp(call)
                } else when {
                    objects[0] is Response -> (objects[0] as Response).use { response ->
                        response.headers.forEach { (name, value) ->
                            if (!HttpHeaders.isUnsafe(name)) {
                                call.response.headers.append(name, value)
                            }
                        }
                        // 设置 m3u8 的 Content-Type
                        val contentType = if (response.header("Content-Type")?.contains("m3u8") == true) {
                            ContentType.parse("application/vnd.apple.mpegurl")
                        } else {
                            response.header("Content-Type")?.let { ContentType.parse(it) }
                        }
                        call.respondOutputStream(
                            status = HttpStatusCode.fromValue(response.code),
                            contentType = contentType
                        ) {
                            response.body.byteStream().use { it.transferTo(this) }
                        }
                    }

                    objects[0] == HttpStatusCode.Found.value -> {
                        val redirectUrl = objects[2] as? String ?: run {
                            errorResp(call)
                            return@get
                        }
                        call.respondRedirect(Url(redirectUrl), false)
                    }

                    else -> {
                        (objects.getOrNull(3) as? Map<*, *>)?.forEach { (t, u) ->
                            if (t is String && u is String) call.response.headers.append(t, u)
                        }
                        (objects[2] as? InputStream)?.use { inputStream ->
                            val contentType = if (objects[1].toString().contains("m3u8")) {
                                ContentType.parse("application/vnd.apple.mpegurl")
                            } else {
                                ContentType.parse(objects[1].toString())
                            }
                            call.respondOutputStream(
                                contentType = contentType,
                                status = HttpStatusCode.fromValue(
                                    objects[0] as? Int ?: HttpStatusCode.InternalServerError.value
                                )
                            ) {
                                inputStream.transferTo(this)
                            }
                        } ?: errorResp(call)
                    }
                }
            } catch (_: IOException) {
            } catch (e: Exception) {
                log.error("proxy处理失败", e)
            }
        }

        /**
         * web播放器视频代理
         */
        get("/video/proxy") {
            val url = call.request.queryParameters["url"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "缺少URL参数")
                return@get
            }

            // 添加基本的安全检查
            if (url.isBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                call.respond(HttpStatusCode.BadRequest, "无效的URL格式")
                return@get
            }

            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            try {
                val requestBuilder = Request.Builder()
                    .url(url)
                    // 添加常见的视频请求头
                    .addHeader(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )
                    .addHeader("Accept", "*/*")
                    .addHeader("Connection", "keep-alive")
                    .addHeader("Accept-Encoding", "gzip, deflate, br")
                    .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")

                // 转发客户端的重要请求头
                call.request.headers["Range"]?.let { range ->
                    requestBuilder.addHeader("Range", range)
                }
                call.request.headers["Referer"]?.let { referer ->
                    requestBuilder.addHeader("Referer", referer)
                }
                call.request.headers["Origin"]?.let { origin ->
                    requestBuilder.addHeader("Origin", origin)
                }

                val response = client.newCall(requestBuilder.build()).execute()

                // 检查响应状态
                if (!response.isSuccessful && response.code != HttpStatusCode.PartialContent.value) {
                    val errorMsg = "上游服务器错误: ${response.code} ${response.message}"
                    log.warn("视频代理请求失败: URL=$url, Status=${response.code}")
                    response.close()
                    call.respond(HttpStatusCode.BadGateway, errorMsg)
                    return@get
                }

                // 转发重要的响应头
                response.headers.names().forEach { name ->
                    val values = response.headers(name)
                    if (!HttpHeaders.isUnsafe(name) &&
                        name != "Transfer-Encoding" &&
                        name != "Connection" &&
                        name.lowercase() !in listOf(
                            "access-control-allow-origin",
                            "access-control-allow-methods",
                            "access-control-allow-headers"
                        )
                    ) {
                        val value = if (values.size == 1) values[0] else values.joinToString(", ")
                        call.response.headers.append(name, value)
                    }
                }

                // 特别处理m3u8和视频内容类型
                val contentType = when {
                    response.header("Content-Type")?.contains("m3u8") == true ->
                        ContentType.parse("application/vnd.apple.mpegurl")

                    response.header("Content-Type")?.contains("mpegurl") == true ->
                        ContentType.parse("application/vnd.apple.mpegurl")

                    response.header("Content-Type")?.contains("video") == true ->
                        response.header("Content-Type")?.let { ContentType.parse(it) }
                            ?: ContentType.Video.MPEG

                    else ->
                        response.header("Content-Type")?.let { ContentType.parse(it) }
                            ?: ContentType.Application.OctetStream
                }

                // 设置正确的状态码
                val statusCode = when {
                    response.code == HttpStatusCode.PartialContent.value -> HttpStatusCode.PartialContent
                    response.isSuccessful -> HttpStatusCode.OK
                    else -> HttpStatusCode.fromValue(response.code)
                }

                call.respondOutputStream(
                    status = statusCode,
                    contentType = contentType
                ) {
                    response.body.byteStream().use { input ->
                        try {
                            input.transferTo(this)
                        } catch (e: IOException) {
                            log.warn("视频流传输中断: ${e.message}")
                        }
                    }
                }

            } catch (e: SocketTimeoutException) {
                log.error("视频代理请求超时: URL=$url", e)
                call.respond(HttpStatusCode.GatewayTimeout, "请求超时")
            } catch (e: ConnectException) {
                log.error("视频代理连接失败: URL=$url", e)
                call.respond(HttpStatusCode.BadGateway, "无法连接到目标服务器")
            } catch (e: Exception) {
                log.error("视频代理请求失败: URL=$url", e)
                call.respond(HttpStatusCode.BadGateway, "代理请求失败: ${e.message ?: "未知错误"}")
            }
        }

        /**
         * 代理m3u8文件
         */
        get("/proxy/m3u8") {
            val encodedUrl = call.request.queryParameters["url"] ?: run {
                errorResp(call, "URL参数缺失")
                return@get
            }
            val decodedUrl = try {
                URLDecoder.decode(encodedUrl, "UTF-8").also { url ->
                    if (url.contains("proxy/m3u8") || !url.startsWith("https://")) {
                        errorResp(call, "非法的目标URL")
                        return@get
                    }
                }
            } catch (_: Exception) {
                errorResp(call, "URL解码失败")
                return@get
            }
            val client = OkHttpClient.Builder().build()
            try {
                val content = client.newCall(Request.Builder().url(decodedUrl).build())
                    .execute().use { response ->
                        if (!response.isSuccessful) {
                            errorResp(call, "上游服务器返回错误: ${response.code}")
                            return@get
                        }
                        response.body.string()
                    }
                call.respondText(content, ContentType.Application.OctetStream)
            } catch (e: Exception) {
                log.error("代理请求失败: URL=$decodedUrl", e)
                errorResp(call, "代理请求失败: ${e.message}")
            }
        }

        /**
         * 代理已缓存的m3u8文件
         */
        get("/proxy/cached_m3u8") {
            val id = call.request.queryParameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing cache ID")
                return@get
            }

            val content = M3U8Cache.get(id) ?: run {
                call.respond(HttpStatusCode.NotFound, "Cache expired or invalid")
                return@get
            }

            call.respondText(content, ContentType.Application.OctetStream)
        }
    }
}

/**
 * 错误响应
 */
suspend fun errorResp(call: ApplicationCall) {
    call.respondText(
        text = HttpStatusCode.InternalServerError.description,
        contentType = ContentType.Application.OctetStream,
        status = HttpStatusCode.InternalServerError
    ) {}
}

/**
 * 错误响应
 */
suspend fun errorResp(call: ApplicationCall, msg: String) {
    call.respondText(
        text = msg,
        contentType = ContentType.Application.OctetStream,
        status = HttpStatusCode.InternalServerError
    ) {}
}
