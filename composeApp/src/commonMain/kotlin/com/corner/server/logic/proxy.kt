package com.corner.server.logic

import com.corner.catvodcore.loader.JarLoader
import com.corner.util.network.KtorClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory


fun proxy(params: Map<String, String>): Array<Any>? {
    when (params["do"]) {
        "js" -> { /* js */ }
        "py" -> { /* py */ }
        else -> return JarLoader.proxyInvoke(params)
    }
    return null
}