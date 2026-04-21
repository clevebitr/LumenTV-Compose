package com.corner.server.logic

import com.corner.catvodcore.loader.JarLoader

fun proxy(params: Map<String, String>): Array<Any>? {
    when (params["do"]) {
        "js" -> { /* js */ }
        "py" -> { /* py */ }
        else -> return JarLoader.proxyInvoke(params)
    }
    return null
}