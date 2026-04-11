package com.corner.catvodcore.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.corner.bean.HotData
import com.corner.bean.SettingStore
import com.corner.bean.SettingType
import com.corner.catvodcore.bean.Site
import com.corner.catvodcore.bean.Vod
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jupnp.UpnpService
import org.slf4j.LoggerFactory

object GlobalAppState {
    private val log = LoggerFactory.getLogger(GlobalAppState::class.java)

    /**
     * 是否是深色主题
     */
    val isDarkTheme = MutableStateFlow(
        try {
            SettingStore.getSettingItem(SettingType.THEME) == "dark"
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    )

    /**
     * 是否显示加载指示器
     */
    var showProgress = MutableStateFlow(false)

    /**
     * 热搜列表
     */
    val hotList = MutableStateFlow(listOf<HotData>())

    /**
     * 选择的视频
     */
    val chooseVod = mutableStateOf(Vod())

    /**
     * 主页站点
     */
    val home = MutableStateFlow(Site.get("", ""))

    /**
     * 是否清除数据
     */
    val clear = MutableStateFlow(false)

    /**
     * 是否关闭App
     */
    val closeApp = MutableStateFlow(false)

    /**
     * 是否全屏
     */
    val videoFullScreen = MutableStateFlow(false)

    /**
     * DLNA播放地址
     */
    val DLNAUrl = MutableStateFlow("")

    /**
     * 根协程Job
     */
    private val rootJob = Job()

    /**
     * 根协程作用域
     */
    val rootScope = CoroutineScope(Dispatchers.IO + rootJob)

    /**
     * UPNP服务锁
     */
    private val upnpServiceLock = Any()

    /**
     * 内部UPNP服务访问
     */
    private var _upnpService: UpnpService? = null

    /**
     * UPNP服务
     */
    var upnpService: UpnpService?
        get() = synchronized(upnpServiceLock) { _upnpService }
        set(value) = synchronized(upnpServiceLock) { _upnpService = value }

    /**
     * 窗口状态
     */
    var windowState: WindowState? = null

    /**
     * 详情页来源页面
     */
    var detailFrom = DetailFromPage.HOME

    /**
     * 取消所有操作
     * @param reason 停止的原因
     */
    fun cancelAllOperations(reason: String = "Normal shutdown") {
        if (!rootJob.isCancelled) {
            log.info("Cancelling all operations: $reason")
            rootScope.cancel(reason)
        }
    }

    /**
     * 切换全屏状态
     * @return 当前全屏状态
     */
    fun toggleVideoFullScreen(): Boolean {
        toggleWindowFullScreen()
        videoFullScreen.value = !videoFullScreen.value
        return videoFullScreen.value
    }

    /**
     * 切换窗口全屏状态
     */
    private fun toggleWindowFullScreen() {
        windowState?.placement = when (windowState?.placement) {
            WindowPlacement.Fullscreen -> WindowPlacement.Floating
            else -> WindowPlacement.Fullscreen
        }
    }

    /**
     * 显示加载指示器
     */
    fun showProgress() {
        showProgress.update { true }
    }

    /**
     * 隐藏加载指示器
     */
    fun hideProgress() {
        showProgress.update { false }
    }

    /**
     * 重置所有状态
     * */
    fun resetAllStates() {
        showProgress = MutableStateFlow(false)
        hotList.value = emptyList()
        home.value = Site.get("", "")
        clear.value = false
        videoFullScreen.value = false
        DLNAUrl.value = ""
        chooseVod.value = Vod()
    }
}

/**
 * 详情页来源页面
 */
enum class DetailFromPage {
    SEARCH, HOME
}