package com.corner.util.core

fun catch(body: () -> Unit) = runCatching { body() }.onFailure { it.printStackTrace() }.getOrNull() ?: Unit