package com.corner.util.net

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.*

private val log = LoggerFactory.getLogger(ProxySelect::class.java)

class ProxySelect : ProxySelector() {

    private var proxy: Proxy? = null
    private var proxyHosts: List<String>? = null

    companion object {
        fun setDefault(proxySelector: ProxySelector) {
            ProxySelector.setDefault(proxySelector)
        }
    }

    override fun select(uri: URI?): MutableList<Proxy> {
        if (uri == null || proxy == null) {
            return mutableListOf(Proxy.NO_PROXY)
        }

        val httpUrl = uri.toHttpUrlOrNull() ?: return mutableListOf(Proxy.NO_PROXY)
        
        // 检查是否是本地地址
        try {
            if (InetAddress.getByName(httpUrl.host).isLoopbackAddress) {
                log.debug("Local address detected, bypassing proxy: {}", httpUrl.host)
                return mutableListOf(Proxy.NO_PROXY)
            }
        } catch (e: Exception) {
            log.warn("Failed to resolve host: {}, continuing with proxy check", httpUrl.host, e)
        }

        // 如果配置了代理白名单，只在白名单中的域名使用代理
        if (!proxyHosts.isNullOrEmpty()) {
            val shouldUseProxy = proxyHosts!!.any { pattern ->
                httpUrl.host.contains(pattern, ignoreCase = true)
            }
            
            if (shouldUseProxy) {
                log.debug("Using proxy for host: {} (matched pattern)", httpUrl.host)
                return mutableListOf(proxy!!)
            } else {
                log.debug("Bypassing proxy for host: {} (not in whitelist)", httpUrl.host)
                return mutableListOf(Proxy.NO_PROXY)
            }
        }

        // 没有配置白名单，所有非本地地址都使用代理
        log.debug("Using proxy for: {}", httpUrl.host)
        return mutableListOf(proxy!!)
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        log.warn("Proxy connection failed for URI: {}, SocketAddress: {}", uri, sa, ioe)
    }

    fun setProxyHosts(hosts: List<String>) {
        proxyHosts = hosts
        log.info("Proxy hosts whitelist updated: {} hosts", hosts.size)
    }

    fun setProxy(proxyUrl: String) {
        val httpUrl = proxyUrl.toHttpUrlOrNull()
        if (httpUrl == null) {
            log.error("Invalid proxy URL: $proxyUrl")
            return
        }
        
        // 设置认证信息
        if (httpUrl.username.isNotEmpty()) {
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    log.debug("Setting proxy authentication for user: {}", httpUrl.username)
                    return PasswordAuthentication(httpUrl.username, httpUrl.password.toCharArray())
                }
            })
        }
        
        // 从 ProxyManager 获取代理配置（统一状态管理）
        // ProxyManager 会测试代理可用性，如果不可用则返回 NO_PROXY
        val proxy = ProxyManager.getProxy()
        this.proxy = if (proxy != Proxy.NO_PROXY) proxy else null
        
        log.info("Proxy configured from ProxyManager: type={}, host={}, port={}", 
            proxy.type(), 
            (proxy.address() as? InetSocketAddress)?.hostName ?: "N/A",
            (proxy.address() as? InetSocketAddress)?.port ?: -1
        )
    }
}
