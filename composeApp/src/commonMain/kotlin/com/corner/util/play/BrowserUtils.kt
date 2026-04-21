package com.corner.util.play

import com.corner.ui.nav.vm.DetailViewModel
import com.corner.server.plugins.webPlaybackFinishedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private val log = LoggerFactory.getLogger("BrowserUtils")

object BrowserUtils {

    // WebSocket 连接状态流
    val _webSocketConnectionState = MutableStateFlow(false)
    val webSocketConnectionState: StateFlow<Boolean> = _webSocketConnectionState
    private val lastOpenTime = AtomicLong(0)

    // CoroutineScope 实例
    val scope = CoroutineScope(Dispatchers.Default)

    var detailViewModel: DetailViewModel? = null

    // 初始化
    fun initialize(viewModel: DetailViewModel) {
        detailViewModel = viewModel
        log.info("BrowserUtils 已初始化 detailViewModel")
    }

    private val isSwitching = AtomicBoolean(false)

    /**
     * 处理切换到下一集的逻辑。
     */
    fun handleNextEpisode() {
        if (isSwitching.compareAndSet(false, true)) {
            try {
                // 调用切换到下一集并播放的方法
                switchToNextEpisodeAndPlay()
                // 增加延迟，防止短时间内重复处理
                Thread.sleep(1000) // 或使用协程 delay(1000)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                log.error("处理下一集时线程被中断", e)
            } finally {
                // 重置切换标志
                isSwitching.set(false)
            }
        }
    }

    // 模拟切换到下一个选集并播放的方法
    fun switchToNextEpisodeAndPlay() {
        val viewModel = detailViewModel
        if (viewModel == null) {
            log.warn("detailViewModel未初始化，无法切换下一集")
            return
        }

        scope.launch {
            try {
                val nextEpisodeUrl = viewModel.getNextEpisodeUrl()
                nextEpisodeUrl?.let {
                    // 从 viewModel 的状态中获取当前选中的剧集 URL
                    val currentSelectedEpNumber = viewModel.currentSelectedEpNumber
                    // 从状态数据里找到对应的剧集
                    val currentEpisode = viewModel.state.value.detail.subEpisode.find { ep ->
                        ep.number == currentSelectedEpNumber
                    }
                    val episodeName = viewModel.state.value.detail.vodName ?: ""
                    val episodeNumber = currentEpisode?.number
                    openBrowserWithWebPlayer(it, episodeName, episodeNumber)
                }
            } catch (e: Exception) {
                log.error("切换下一集时发生异常", e)
            }
        }
    }

    fun startListening() {
        scope.launch {
            webPlaybackFinishedFlow.collect {
                handleNextEpisode()
            }
        }
    }

    interface TimeProvider {
        fun currentTimeMillis(): Long
    }

    class DefaultTimeProvider : TimeProvider {
        override fun currentTimeMillis(): Long = System.currentTimeMillis()
    }

    /**
     * 使用WebPlayerServer在浏览器中播放视频
     * 该方法会启动WebPlayerServer并在浏览器中打开播放页面
     *
     * @param videoUrl 要播放的视频链接
     * @param episodeName 剧集名称
     * @param episodeNumber 集数
     * @param timeProvider 时间提供者（用于测试）
     */
    fun openBrowserWithWebPlayer(
        videoUrl: String,
        episodeName: String? = null,
        episodeNumber: Int? = null,
        timeProvider: TimeProvider = DefaultTimeProvider()
    ) {
        // 获取当前时间戳
        val now = timeProvider.currentTimeMillis()
        // 检查距离上次打开浏览器是否不足 1 秒，若是则直接返回，避免短时间内重复打开
        if (now - lastOpenTime.get() < 1000) return

        // 使用 CAS 操作更新上次打开时间，确保操作的原子性
        if (lastOpenTime.compareAndSet(lastOpenTime.get(), now)) {
            // 启动监听，当接收到视频播放完成事件时处理下一集
            startListening()

            try {
                // 使用WebPlayerServer打开浏览器
                val title = if (episodeNumber != null && episodeNumber != -1) {
                    "$episodeName 第 $episodeNumber 集"
                } else {
                    episodeName ?: "视频播放"
                }

                WebPlayerServer.openInBrowser(
                    mediaUrl = videoUrl,
                    title = title,
                    episodeNumber = episodeNumber
                )

                log.info("使用WebPlayerServer在浏览器中打开视频: $title")
            } catch (e: Exception) {
                log.error("使用WebPlayerServer打开浏览器失败", e)
                e.printStackTrace()
            }
        }
    }

    /**
     * 清理BrowserUtils实例，包括停止WebPlayerServer和清空detailViewModel引用
     */
    fun cleanup() {
        // 停止WebPlayerServer
        if (WebPlayerServer.isServerRunning()) {
            WebPlayerServer.stop()
            log.info("WebPlayerServer已停止")
        }

        // 更新连接状态
        _webSocketConnectionState.value = false
        log.debug("WebSocket连接状态已重置")

        // 清空detailViewModel引用
        detailViewModel = null
    }
}
