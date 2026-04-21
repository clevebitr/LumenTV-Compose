package com.github.catvod.crawler

import com.corner.util.playwright.PlaywrightBrowserManager
import kotlinx.coroutines.runBlocking

abstract class PlaywrightSpiderBase : Spider() {

    protected var isBrowserReady: Boolean = false

    override fun init() {
        super.init()
        checkAndInitializeBrowser()
    }

    override fun init(extend: String?) {
        super.init(extend)
        checkAndInitializeBrowser()
    }

    private fun checkAndInitializeBrowser() {
        if (!PlaywrightBrowserManager.isBrowserAvailable()) {
            throw IllegalStateException("Playwright 浏览器未安装，请先在设置中下载浏览器")
        }
        isBrowserReady = true
    }

    override fun homeContent(filter: Boolean): String {
        return "{}"
    }

    override fun homeVideoContent(): String {
        return "{}"
    }

    override fun categoryContent(
        tid: String,
        pg: String,
        filter: Boolean,
        extend: HashMap<String, String>
    ): String {
        return "{}"
    }

    override fun detailContent(ids: List<String?>?): String {
        return "{}"
    }

    override fun searchContent(key: String?, quick: Boolean): String {
        return "{}"
    }

    override fun searchContent(key: String?, quick: Boolean, pg: String?): String {
        return "{}"
    }

    override fun playerContent(flag: String?, id: String?, vipFlags: List<String?>?): String {
        return "{}"
    }

    override fun manualVideoCheck(): Boolean {
        return false
    }

    override fun isVideoFormat(url: String?): Boolean {
        return false
    }

    override fun proxyLocal(params: Map<String?, String?>?): Array<Any>? {
        return null
    }

    override fun destroy() {
        super.destroy()
    }

    protected fun getBrowserPath(): String {
        return PlaywrightBrowserManager.getBrowserExecutablePath()
    }

    protected fun getBrowserCacheDir(): String {
        return PlaywrightBrowserManager.getBrowserCacheDir()
    }

    protected fun getTempDir(): String {
        return PlaywrightBrowserManager.getTempDir()
    }

    protected fun isBrowserAvailable(): Boolean {
        return PlaywrightBrowserManager.isBrowserAvailable()
    }

    protected fun ensureBrowserReady() {
        val status = checkBrowserStatus()
        when (status) {
            BrowserStatus.NOT_INSTALLED -> {
                val errorMsg = "Playwright 浏览器未安装，请先在设置中下载浏览器"
                throw IllegalStateException(errorMsg)
            }
            BrowserStatus.NOT_INITIALIZED -> {
                val errorMsg = "Playwright 浏览器未初始化，请稍后重试或重启应用"
                throw IllegalStateException(errorMsg)
            }
            BrowserStatus.READY -> {}
        }
    }

    protected fun checkBrowserStatus(): BrowserStatus {
        return when {
            !isBrowserAvailable() -> BrowserStatus.NOT_INSTALLED
            !isBrowserReady -> BrowserStatus.NOT_INITIALIZED
            else -> BrowserStatus.READY
        }
    }

    enum class BrowserStatus {
        /** 浏览器未安装 */
        NOT_INSTALLED,

        /** 浏览器已安装但未初始化 */
        NOT_INITIALIZED,

        /** 浏览器已就绪 */
        READY
    }
}