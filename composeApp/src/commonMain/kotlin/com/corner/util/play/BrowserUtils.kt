package com.corner.util.play

import com.corner.ui.nav.vm.DetailViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.java_websocket.server.WebSocketServer
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import java.net.InetSocketAddress
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private val log = LoggerFactory.getLogger(VideoEventServer::class.java)

// 定义全局状态
private val _webPlaybackFinishedFlow = MutableSharedFlow<Unit>(
    replay = 0,
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST // 丢弃重复事件
)
val webPlaybackFinishedFlow: SharedFlow<Unit> = _webPlaybackFinishedFlow

// 新增标志位，记录当前视频是否正在播放
private var isVideoPlaying = false

object BrowserUtils {

    // 定义 WebSocket 连接状态流
    val _webSocketConnectionState = MutableStateFlow(false) // 初始状态为未连接
    val webSocketConnectionState: StateFlow<Boolean> = _webSocketConnectionState
    private val lastOpenTime = AtomicLong(0)

    // 定义一个 CoroutineScope 实例
    val scope = CoroutineScope(Dispatchers.Default)
    
    // 使用可空类型而不是lateinit，避免未初始化异常
    var detailViewModel: DetailViewModel? = null

    // 添加初始化方法
    fun initialize(viewModel: DetailViewModel) {
        detailViewModel = viewModel
        log.info("BrowserUtils 已初始化 detailViewModel")
    }

    // 启动 WebSocket 服务器
    private val isWebSocketServerStarted = AtomicBoolean(false)

    fun startWebSocketServer() {
        if (isWebSocketServerStarted.compareAndSet(false, true)) {
            VideoEventServer.start()
        }
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

    // 启动监听
    fun startListening() {
        // 使用自定义的 CoroutineScope 实例启动协程
        scope.launch {
            webPlaybackFinishedFlow.collect {
                handleNextEpisode()
            }
        }
    }

    // 定义一个接口来获取当前时间
    interface TimeProvider {
        fun currentTimeMillis(): Long
    }

    // 默认实现使用 System.currentTimeMillis()
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
            // 启动 WebSocket 服务器，用于接收浏览器发送的视频播放事件
            startWebSocketServer()
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

    fun cleanup() {
        // 检查WebSocket服务器是否正在运行
        if (VideoEventServer.isRunning()) {
            VideoEventServer.stop()
            log.info("WebSocket 服务器已停止")
        } else {
            log.info("WebSocket 服务器未运行，跳过停止操作")
        }
        
        // 停止WebPlayerServer
        if (WebPlayerServer.isServerRunning()) {
            WebPlayerServer.stop()
            log.info("WebPlayerServer已停止")
        }
        
        // 清空detailViewModel引用
        detailViewModel = null
    }
}

// WebSocket 服务器实现
object VideoEventServer : WebSocketServer(InetSocketAddress("localhost", 8888)) {
    // 添加状态标记
    private var _isRunning = AtomicBoolean(false)

    // 提供公共方法检查运行状态
    fun isRunning(): Boolean = _isRunning.get()

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        log.debug("WebSocket 连接已建立")
        // 连接建立后，重置播放状态
        isVideoPlaying = false
        // 连接建立后，更新 WebSocket 连接状态为已连接
        BrowserUtils._webSocketConnectionState.value = true
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        log.debug("WebSocket 连接关闭，关闭码: {}, 关闭原因: {}, 是否远程关闭: {}", code, reason, remote)
        // 若视频正在播放，说明是中途退出，更新 WebSocket 连接状态为未连接
        if (isVideoPlaying) {
            BrowserUtils._webSocketConnectionState.value = false
            log.debug("更新WebSocket 连接状态：{}", BrowserUtils._webSocketConnectionState.value)
        }
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        log.info("收到消息: {}", message)
        // 在这里处理来自浏览器的消息（如播放开始、播放完成事件）
        when (message) {
            "PLAYBACK_STARTED" -> {
                log.info("视频播放开始！")
                // 设置视频正在播放标志
                isVideoPlaying = true
            }
            "PLAYBACK_FINISHED" -> {
                log.info("视频播放完成！")
                // 重置视频正在播放标志
                isVideoPlaying = false
                // 使用自定义的 CoroutineScope 实例启动协程
                BrowserUtils.scope.launch {
                    _webPlaybackFinishedFlow.emit(Unit)
                }
                log.info("当前视频播放完成，尝试切换下一集...")
            }
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        log.error("WebSocket 发生错误", ex)
    }

    // 重写 onStart 方法
    override fun onStart() {
        _isRunning.set(true)
        log.info("WebSocket 服务器已启动")
    }

    // 重写 stop 方法
    override fun stop() {
        super.stop()
        _isRunning.set(false)
    }
}