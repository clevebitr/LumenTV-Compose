package com.corner.util.json

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*

@OptIn(ExperimentalSerializationApi::class)
val Jsons = Json {
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
    prettyPrintIndent = "   "
}


object ToStringSerializer: JsonTransformingSerializer<String>(String.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val s = element.toString().trim { c -> c == '"' }
        return JsonPrimitive(s)
    }
}

object JsonStrToMapSerializer: JsonTransformingSerializer<Map<String, String>>(MapSerializer(String.serializer(),String.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        println(element.jsonPrimitive.content)
        return Jsons.parseToJsonElement(element.jsonPrimitive.content)
    }

}

/**
 * 清理JSON字符串中的注释
 * 支持单行注释(//)和多行注释(/* */)
 */
fun cleanJsonComments(json: String): String {
    if (json.isBlank()) return json
    
    val result = StringBuilder()
    var i = 0
    val len = json.length
    var inString = false
    var escapeNext = false
    
    while (i < len) {
        val c = json[i]
        
        when {
            // 处理字符串转义
            escapeNext -> {
                result.append(c)
                escapeNext = false
                i++
            }
            // 处理字符串开始/结束
            c == '"' && !inString -> {
                inString = true
                result.append(c)
                i++
            }
            c == '"' && inString -> {
                inString = false
                result.append(c)
                i++
            }
            // 在字符串内部，检查转义字符
            inString -> {
                if (c == '\\') {
                    escapeNext = true
                }
                result.append(c)
                i++
            }
            // 不在字符串中，检查注释
            !inString && c == '/' && i + 1 < len -> {
                when (json[i + 1]) {
                    '/' -> {
                        // 单行注释，跳过到行尾
                        i += 2
                        while (i < len && json[i] != '\n') {
                            i++
                        }
                        // 保留换行符
                        if (i < len) {
                            result.append('\n')
                            i++
                        }
                    }
                    '*' -> {
                        // 多行注释，跳过到 */
                        i += 2
                        while (i < len - 1 && !(json[i] == '*' && json[i + 1] == '/')) {
                            // 保留换行符以维持行号
                            if (json[i] == '\n') {
                                result.append('\n')
                            }
                            i++
                        }
                        i += 2 // 跳过 */
                    }
                    else -> {
                        result.append(c)
                        i++
                    }
                }
            }
            else -> {
                result.append(c)
                i++
            }
        }
    }
    
    return result.toString().trim()
}
