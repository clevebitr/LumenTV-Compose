package com.corner.util.core

class NoStackTraceException(message: String) : RuntimeException(message) {
    override fun fillInStackTrace(): Throwable = this // 重写此方法不填充堆栈
}
