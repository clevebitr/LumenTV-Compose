package com.corner.service.player

import com.corner.catvodcore.bean.Result
import com.corner.catvodcore.bean.Episode
import com.corner.catvodcore.bean.v
import com.corner.ui.player.PlayerLifecycleManager
import com.corner.ui.player.PlayerLifecycleState.*
import com.corner.ui.player.vlcj.VlcjFrameController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory

/**
 * 内部播放器策略（Innie）
 * 
 * 使用VLCJ嵌入式播放器进行播放
 */
class InniePlayerStrategy(
    private val controller: VlcjFrameController,
    private val lifecycleManager: PlayerLifecycleManager,
    private val viewModelScope: CoroutineScope
) : PlayerStrategy {
    
    private val log = LoggerFactory.getLogger(InniePlayerStrategy::class.java)
    
    override suspend fun play(
        result: Result,
        episode: Episode,
        onPlayStarted: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            // 1. 确保播放器已初始化（从 Idle 状态转换）
            if (lifecycleManager.lifecycleState.value == Idle) {
                log.debug("<Innie> 播放器未初始化，开始初始化...")
                val initResult = lifecycleManager.initializeSync()
                if (initResult.isFailure) {
                    onError("播放器初始化失败: ${initResult.exceptionOrNull()?.message}")
                    return
                }
            }
                
            // 2. 准备播放器（转换到 Ready 状态）
            val prepareResult = lifecycleManager.prepareForPlayback()
            if (prepareResult.isFailure) {
                onError("播放器准备失败: ${prepareResult.exceptionOrNull()?.message}")
                return
            }
                
            // 3. 加载媒体 URL
            val loadSuccess = loadMediaUrl(result, onError)
            if (!loadSuccess) {
                return
            }
                
            // 4. 启动播放并等待真正开始播放
            waitForPlaybackToStart(onPlayStarted, onError)
                
        } catch (e: Exception) {
            log.error("内部播放器播放失败", e)
            onError("播放器初始化失败: ${e.message}")
        }
    }
    
    override fun getStrategyName(): String = "InniePlayer"
    
    /**
     * 加载媒体URL到播放器
     */
    private suspend fun loadMediaUrl(result: Result, onError: (String) -> Unit): Boolean {
        val loadResult = PlayerOperationUtils.safeExecute(
            operationName = "加载媒体URL",
            timeoutMillis = PlayerStrategyConfig.INNIE_LOAD_URL_TIMEOUT_MS
        ) {
            controller.loadURL(result.url.v(), PlayerStrategyConfig.INNIE_LOAD_URL_TIMEOUT_MS)
        }
        
        return if (loadResult.isFailure) {
            onError("加载媒体失败: ${loadResult.exceptionOrNull()?.message}")
            false
        } else {
            true
        }
    }
    
    /**
     * 启动播放并等待真正开始播放
     */
    private suspend fun waitForPlaybackToStart(
        onPlayStarted: () -> Unit,
        onError: (String) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        
        try {
            // 不使用 safeExecute，因为 PlayStartedException 是控制流异常
            withTimeout(PlayerStrategyConfig.INNIE_PLAYBACK_START_TIMEOUT_MS) {
                transitionToPlayingState(onError)
                waitForControllerPlayState(onPlayStarted)
            }
        } catch (e: PlayStartedException) {
            // 正常控制流异常，用于跳出collect，无需处理
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            val elapsed = System.currentTimeMillis() - startTime
            log.warn("⚠️ 播放器加载超时 (耗时: {}ms)", elapsed)
            log.warn("⚠️ 当前播放器状态: {}", lifecycleManager.lifecycleState.value)
            log.warn("⚠️ Controller状态: {}", controller.state.value.state)
            log.warn("⚠️ Controller缓冲进度: {}%", controller.state.value.bufferProgression)
            
            onError("播放器加载超时 (${elapsed/1000}秒)，请检查网络连接或尝试切换线路")
            lifecycleManager.ended()
        } catch (e: Exception) {
            log.error("❌ 播放器准备就绪时发生错误", e)
            onError("播放器准备就绪时发生错误: ${e.message}")
            lifecycleManager.ended()
        }
    }
    
    /**
     * 等待Controller状态变为PLAY
     */
    private suspend fun waitForControllerPlayState(onPlayStarted: () -> Unit) {
        // 收集Controller状态流，直到检测到PLAY状态
        controller.state.collect { playerState ->
            // 检查是否已经开始播放或缓冲完成
            if (playerState.state == com.corner.ui.player.PlayState.PLAY) {
                onPlayStarted()
                throw PlayStartedException() // 使用异常跳出collect
            } else if (playerState.state == com.corner.ui.player.PlayState.BUFFERING && 
                       playerState.bufferProgression >= PlayerStrategyConfig.INNIE_BUFFER_COMPLETE_THRESHOLD) {
                onPlayStarted()
                throw PlayStartedException()
            }
        }
    }
    
    /**
     * 用于跳出collect的自定义异常
     */
    private class PlayStartedException : Exception()
    
    /**
     * 转换到Playing状态
     */
    private suspend fun transitionToPlayingState(onError: (String) -> Unit) {
        lifecycleManager.transitionTo(Playing) {
            lifecycleManager.start()
                .onFailure {
                    onError("播放器状态转换Playing失败: ${it.message}")
                }
        }.onFailure { e ->
            onError("播放器就绪失败: ${e.message}")
        }
    }
}
