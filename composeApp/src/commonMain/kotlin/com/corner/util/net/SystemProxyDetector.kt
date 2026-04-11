package com.corner.util.net

import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI

/**
 * 系统代理检测器
 * 自动检测操作系统的代理设置
 */
object SystemProxyDetector {
    private val log = LoggerFactory.getLogger(SystemProxyDetector::class.java)
    
    /**
     * 检测系统代理配置
     * @return 检测到的代理，如果没有则返回null
     */
    fun detectSystemProxy(): Proxy? {
        return try {
            // 方法1: 检查JVM系统属性
            val systemProxy = detectFromSystemProperties()
            if (systemProxy != null && systemProxy != Proxy.NO_PROXY) {
                log.info("Detected system proxy from JVM properties: {}", systemProxy)
                // 验证代理是否可达
                if (isProxyReachable(systemProxy)) {
                    return systemProxy
                } else {
                    log.warn("Proxy from JVM properties is not reachable, ignoring: {}", systemProxy)
                }
            }
            
            // 方法2: 使用Java的ProxySelector（会读取操作系统代理设置）
            val javaProxy = detectFromJavaProxySelector()
            if (javaProxy != null && javaProxy != Proxy.NO_PROXY) {
                log.info("Detected system proxy from Java ProxySelector: {}", javaProxy)
                // 验证代理是否可达
                if (isProxyReachable(javaProxy)) {
                    return javaProxy
                } else {
                    log.warn("Proxy from ProxySelector is not reachable, ignoring: {}", javaProxy)
                }
            }
            
            log.debug("No usable system proxy detected")
            null
        } catch (e: Exception) {
            log.warn("Failed to detect system proxy", e)
            null
        }
    }
    
    /**
     * 检查代理是否可达（简单测试）
     */
    private fun isProxyReachable(proxy: Proxy): Boolean {
        return try {
            val address = proxy.address() as? InetSocketAddress ?: return false
            val host = address.hostName
            val port = address.port
            
            // 尝试连接到代理服务器（超时2秒）
            val socket = java.net.Socket()
            socket.soTimeout = 2000
            socket.connect(java.net.InetSocketAddress(host, port), 2000)
            socket.close()
            
            log.debug("Proxy is reachable: {}:{}", host, port)
            true
        } catch (e: Exception) {
            log.debug("Proxy is not reachable: {}", proxy, e)
            false
        }
    }
    
    /**
     * 从JVM系统属性中检测代理
     * 检查 http.proxyHost, https.proxyHost 等属性
     */
    private fun detectFromSystemProperties(): Proxy? {
        // 优先检查HTTPS代理
        val httpsProxyHost = System.getProperty("https.proxyHost")
        val httpsProxyPort = System.getProperty("https.proxyPort", "443")
        
        if (!httpsProxyHost.isNullOrBlank()) {
            return createProxyFromHostAndPort(httpsProxyHost, httpsProxyPort, "HTTPS")
        }
        
        // 其次检查HTTP代理
        val httpProxyHost = System.getProperty("http.proxyHost")
        val httpProxyPort = System.getProperty("http.proxyPort", "8080")
        
        if (!httpProxyHost.isNullOrBlank()) {
            return createProxyFromHostAndPort(httpProxyHost, httpProxyPort, "HTTP")
        }
        
        return null
    }
    
    /**
     * 从主机和端口创建代理对象
     */
    private fun createProxyFromHostAndPort(host: String, port: String, protocol: String): Proxy? {
        return try {
            val portNumber = port.toIntOrNull() ?: run {
                log.warn("Invalid {} proxy port: {}", protocol, port)
                return null
            }
            
            if (portNumber <= 0 || portNumber > 65535) {
                log.warn("{} proxy port out of range: {}", protocol, portNumber)
                return null
            }
            
            Proxy(Proxy.Type.HTTP, InetSocketAddress(host, portNumber))
        } catch (e: Exception) {
            log.warn("Failed to create {} proxy from {}:{}", protocol, host, port, e)
            null
        }
    }
    
    /**
     * 使用Java的ProxySelector检测系统代理
     * 这个方法会读取操作系统的代理设置（Windows注册表、macOS网络设置、Linux环境变量等）
     */
    private fun detectFromJavaProxySelector(): Proxy? {
        return try {
            // 使用一个测试URL来触发代理检测
            val testUri = URI("https://www.google.com")
            log.debug("测试代理URL: {}", testUri)
            
            val proxySelector = ProxySelector.getDefault()
            log.debug("Default ProxySelector class: {}", proxySelector?.javaClass?.name ?: "null")
            
            val proxies = proxySelector?.select(testUri)
            log.debug("ProxySelector returned {} proxies", proxies?.size ?: 0)
            
            if (proxies != null && proxies.isNotEmpty()) {
                proxies.forEachIndexed { index, proxy ->
                    log.debug("Proxy[{}]: type={}, address={}", index, proxy.type(), proxy.address())
                }
                
                // 返回第一个非直连的代理
                val proxy = proxies.firstOrNull { it.type() != Proxy.Type.DIRECT }
                if (proxy != null) {
                    log.info("Java ProxySelector detected proxy: {} at {}", proxy.type(), proxy.address())
                    return proxy
                } else {
                    log.debug("All proxies are DIRECT connections")
                }
            }
            
            null
        } catch (e: Exception) {
            log.warn("Failed to detect proxy via ProxySelector", e)
            null
        }
    }
    
    /**
     * 检测SOCKS代理
     */
    fun detectSocksProxy(): Proxy? {
        return try {
            val socksHost = System.getProperty("socksProxyHost")
            val socksPort = System.getProperty("socksProxyPort", "1080")
            
            if (!socksHost.isNullOrBlank()) {
                val portNumber = socksPort.toIntOrNull() ?: return null
                val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, portNumber))
                log.info("Detected SOCKS proxy: {}", proxy)
                return proxy
            }
            
            null
        } catch (e: Exception) {
            log.warn("Failed to detect SOCKS proxy", e)
            null
        }
    }
}
