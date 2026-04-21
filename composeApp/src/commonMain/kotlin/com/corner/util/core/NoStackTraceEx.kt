package com.corner.util.core

class NoStackTraceException(message: String) : RuntimeException(message) {
    override fun fillInStackTrace(): Throwable = this
}