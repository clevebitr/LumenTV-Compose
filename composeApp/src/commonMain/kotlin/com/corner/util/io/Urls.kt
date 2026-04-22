package com.corner.util.io

import com.corner.util.core.thisLogger
import java.net.URI

object Urls {
    private val log = thisLogger()

    private val WINDOWS_FILE_URI_REGEX = Regex("^file://[A-Za-z]:/.*")
    private val WINDOWS_PATH_REGEX = Regex("^/[A-Za-z]:/.*")

    fun convert(url:String):String{
        if(url.startsWith("file:/")) return url.replace("file://","").replace("file:/","")
        return url
    }

    fun convert(baseUrl:String, refUrl:String):String{
        try {
            val normalizedBaseUrl = baseUrl.replace('\\', '/')
            val normalizedRefUrl = refUrl.replace('\\', '/')

            val fixedBaseUrl = if (normalizedBaseUrl.matches(WINDOWS_FILE_URI_REGEX)) {
                "file:///" + normalizedBaseUrl.substring(7)
            } else {
                normalizedBaseUrl
            }
            
            val resolvedUri = URI(fixedBaseUrl).resolve(normalizedRefUrl)

            val result = if (!"file".equals(resolvedUri.scheme, ignoreCase = true)) {
                resolvedUri.toString()
            } else {
                resolvedUri.path?.let { path ->
                    when {
                        path.matches(WINDOWS_PATH_REGEX) -> path.substring(1)  // Windows: /C:/... -> C:/...
                        !path.startsWith('/') -> "/$path"                      // 相对路径补前导斜杠
                        else -> path                                           // Linux/macOS绝对路径
                    }
                } ?: ""
            }
                
            log.info("解析url:$result,baseUrl $baseUrl,refUrl $refUrl")
            return result
        } catch (e: Exception) {
            log.error("解析url失败 返回空值", e)
            return ""
        }
    }
}