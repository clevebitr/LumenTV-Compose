package com.corner.util.net.interceptor

import com.corner.util.adblock.AdDomainInterceptor
import com.github.catvod.net.OkhttpInterceptor
import com.corner.util.m3u8.M3U8AdFilterInterceptor

object Interceptors {
    val adDomainInterceptor = AdDomainInterceptor()
    val deflateInterceptor = OkhttpInterceptor()
    val m3u8AdInterceptor = M3U8AdFilterInterceptor.Interceptor()
}