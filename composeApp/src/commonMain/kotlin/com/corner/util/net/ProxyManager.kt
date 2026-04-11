package com.corner.util.net

import com.corner.bean.SettingStore
import com.corner.bean.SettingType
import com.corner.bean.parseAsSettingEnable
import com.corner.ui.scene.SnackBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

/**
 * 全局代理管理器
 * 
 * 统一管理应用所有 HTTP 客户端的代理状态，确保：
 * 1. 代理测试结果在所有客户端间共享
 * 2. 代理禁用状态同步到所有客户端
 * 3. 避免重复测试和状态不一致
 */
object ProxyManager {
    private val log = LoggerFactory.getLogger(ProxyManager::class.java)
    
    /**
     * 代理测试结果缓存
     */
    @Volatile
    private var proxyTestResult: ProxyTestResult? = null
    
    /**
     * 锁对象，用于同步代理测试
     */
    private val proxyTestLock = Any()
    
    data class ProxyTestResult(
        val proxyUrl: String,
        val isAvailable: Boolean,
        val testTime: Long
    )
    
    /**
     * 获取代理配置（统一入口）
     * 
     * 策略：
     * 1. 首次调用时测试代理可用性（线程安全）
     * 2. 如果代理不可用，自动禁用并提示用户
     * 3. 后续调用直接返回缓存结果，不再重复测试
     * 4. 所有 HTTP 客户端（Ktor、OkHttp）都使用此方法
     */
    fun getProxy(): Proxy {
        val settingEnable = SettingStore.getSettingItem(SettingType.PROXY).parseAsSettingEnable()
        
        // 如果用户没有启用代理，检查系统代理缓存
        if (!settingEnable.isEnabled || settingEnable.value.isBlank()) {
            // 第一次检查（无锁，快速路径）
            val cachedResult = proxyTestResult
            if (cachedResult != null && cachedResult.proxyUrl == "SYSTEM_PROXY_CHECK") {
                // 使用缓存的系统代理检测结果（静默）
                return if (cachedResult.isAvailable) {
                    // 缓存中存储的是系统代理
                    SystemProxyDetector.detectSystemProxy() ?: Proxy.NO_PROXY
                } else {
                    Proxy.NO_PROXY
                }
            }
            
            // 双重检查锁定（DCL）
            synchronized(proxyTestLock) {
                // 第二次检查（有锁）
                val currentResult = proxyTestResult
                if (currentResult != null && currentResult.proxyUrl == "SYSTEM_PROXY_CHECK") {
                    return if (currentResult.isAvailable) {
                        SystemProxyDetector.detectSystemProxy() ?: Proxy.NO_PROXY
                    } else {
                        Proxy.NO_PROXY
                    }
                }
                
                // 执行系统代理检测（只执行一次）
                log.debug("No manual proxy configured, attempting to detect system proxy...")
                val systemProxy = SystemProxyDetector.detectSystemProxy()
                
                // 缓存系统代理检测结果
                proxyTestResult = ProxyTestResult(
                    proxyUrl = "SYSTEM_PROXY_CHECK",
                    isAvailable = systemProxy != null,
                    testTime = System.currentTimeMillis()
                )
                
                if (systemProxy != null) {
                    log.debug("System proxy detected: {}", systemProxy)
                    return systemProxy
                } else {
                    log.debug("No proxy configured, using direct connection")
                    return Proxy.NO_PROXY
                }
            }
        }
        
        val proxyUrl = settingEnable.value
        
        // 第一次检查（无锁，快速路径）
        val cachedResult = proxyTestResult
        if (cachedResult != null && cachedResult.proxyUrl == proxyUrl) {
            // 使用缓存结果（静默，不输出日志）
            return if (cachedResult.isAvailable) parseProxyFromUrl(proxyUrl) else Proxy.NO_PROXY
        }
        
        // 双重检查锁定（DCL），确保只有一个线程执行测试
        synchronized(proxyTestLock) {
            // 第二次检查（有锁，防止重复测试）
            val currentResult = proxyTestResult
            if (currentResult != null && currentResult.proxyUrl == proxyUrl) {
                // 其他线程已经完成了测试，直接使用结果（静默，不输出日志）
                return if (currentResult.isAvailable) parseProxyFromUrl(proxyUrl) else Proxy.NO_PROXY
            }
            
            // 执行代理测试（只有一个线程会到达这里）
            val proxy = parseProxyFromUrl(proxyUrl)
            val isAvailable = testProxyConnectionSync(proxy, proxyUrl)
            
            // 缓存测试结果
            proxyTestResult = ProxyTestResult(
                proxyUrl = proxyUrl,
                isAvailable = isAvailable,
                testTime = System.currentTimeMillis()
            )
            
            if (isAvailable) {
                log.debug("代理测试成功，使用代理: {}", proxyUrl)
                return proxy
            } else {
                log.warn("代理测试失败，自动禁用代理: {}", proxyUrl)
                // 显示错误提示（使用 IO dispatcher，避免 Main dispatcher 未初始化的问题）
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        SnackBar.postMsg(
                            "代理服务器连接失败: $proxyUrl\n已自动禁用代理，请在设置中检查配置",
                            type = SnackBar.MessageType.ERROR
                        )
                    } catch (e: Exception) {
                        // 如果 SnackBar 不可用（例如启动阶段），只记录日志
                        log.error("无法显示代理错误提示: {}", e.message)
                    }
                }
                return Proxy.NO_PROXY
            }
        }
    }
    
    /**
     * 从 URL 字符串解析代理配置
     */
    private fun parseProxyFromUrl(proxyUrl: String): Proxy {
        return try {
            val uri = URI.create(proxyUrl)
            val type = when (uri.scheme?.lowercase()) {
                "socks", "socks5" -> Proxy.Type.SOCKS
                else -> Proxy.Type.HTTP
            }
            Proxy(type, InetSocketAddress(uri.host, uri.port))
        } catch (e: Exception) {
            log.error("解析代理 URL 失败: $proxyUrl", e)
            Proxy.NO_PROXY
        }
    }
    
    /**
     * 同步测试代理连接（阻塞，只在首次调用时使用）
     * 
     * @return true 如果代理可用，false 否则
     */
    private fun testProxyConnectionSync(proxy: Proxy, proxyUrl: String): Boolean {
        return try {
            val socket = java.net.Socket()
            val address = proxy.address() as? InetSocketAddress
            
            if (address != null) {
                // 设置超时时间为 2 秒
                socket.connect(address, 2000)
                socket.close()
                log.debug("代理连接测试成功: {}", proxyUrl)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            log.warn("代理连接测试失败: {} - {}", proxyUrl, e.message)
            false
        }
    }
    
    /**
     * 清除代理测试缓存（当用户修改代理配置时调用）
     */
    fun clearCache() {
        proxyTestResult = null
        log.debug("代理测试缓存已清除")
    }
    
    /**
     * 获取当前代理状态（用于调试）
     */
    fun getProxyStatus(): ProxyStatus {
        val settingEnable = SettingStore.getSettingItem(SettingType.PROXY).parseAsSettingEnable()
        val cachedResult = proxyTestResult
        
        return ProxyStatus(
            userEnabled = settingEnable.isEnabled,
            configuredUrl = settingEnable.value,
            testResult = cachedResult
        )
    }
    
    data class ProxyStatus(
        val userEnabled: Boolean,
        val configuredUrl: String,
        val testResult: ProxyTestResult?
    )
}
