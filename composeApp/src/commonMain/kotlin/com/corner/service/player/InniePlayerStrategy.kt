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
            // 更新播放状态
            updatePlaybackState(result)
            
            // 加载媒体URL
            val loadSuccess = loadMediaUrl(result, onError)
            if (!loadSuccess) {
                return
            }
            
            // 准备播放器
            prepareForPlayback(result, onError)
            
            // 启动播放并等待真正开始播放
            waitForPlaybackToStart(onPlayStarted, onError)
            
        } catch (e: Exception) {
            log.error("内部播放器播放失败", e)
            onError("播放器初始化失败: ${e.message}")
        }
    }
    
    override fun getStrategyName(): String = "InniePlayer"
    
    /**
     * 更新播放状态
     */
    private fun updatePlaybackState(result: Result) {
        log.debug("更新播放状态: URL={}", result.url.v())
    }
    
    /**
     * 加载媒体URL到播放器
     */
    private suspend fun loadMediaUrl(result: Result, onError: (String) -> Unit): Boolean {
        return try {
            val url = result.url.v()
            controller.loadURL(url, 5000) // 5秒超时
            true
        } catch (e: Exception) {
            log.error("加载媒体URL失败", e)
            onError("加载媒体失败: ${e.message}")
            false
        }
    }
    
    /**
     * 准备播放器：确保播放器处于Ready状态
     */
    private suspend fun prepareForPlayback(result: Result, onError: (String) -> Unit) {
        val success = when (lifecycleManager.lifecycleState.value) {
            Ready -> return // 已经是Ready状态，可以直接播放
            Playing -> transitionFromPlayingToReady()
            Loading, Ended, Ended_Async -> lifecycleManager.ready().isSuccess
            Error -> recoverFromErrorState(onError)
            else -> transitionFromOtherStatesToReady()
        }
        
        if (!success) {
            onError("播放器状态错误，无法准备播放")
        }
    }
    
    /**
     * 从Playing状态转换到Ready
     */
    private suspend fun transitionFromPlayingToReady(): Boolean {
        return lifecycleManager.stop().isSuccess &&
                lifecycleManager.ended().isSuccess &&
                lifecycleManager.ready().isSuccess
    }
    
    /**
     * 从Error状态恢复
     */
    private suspend fun recoverFromErrorState(onError: (String) -> Unit): Boolean {
        return try {
            val cleanupSuccess = lifecycleManager.cleanup().isSuccess
            if (!cleanupSuccess) {
                log.warn("清理资源失败")
            }
            
            val initSuccess = lifecycleManager.initializeSync().isSuccess
            if (!initSuccess) {
                onError("重新初始化失败")
                return false
            }
            
            val loadingSuccess = lifecycleManager.loading().isSuccess
            if (!loadingSuccess) {
                onError("播放器加载失败")
                return false
            }
            
            val readySuccess = lifecycleManager.ready().isSuccess
            if (!readySuccess) {
                onError("播放器准备就绪失败")
                return false
            }
            
            true
        } catch (e: Exception) {
            log.error("错误状态恢复过程中发生异常", e)
            onError("错误状态恢复失败: ${e.message}")
            false
        }
    }
    
    /**
     * 从其他状态转换到Ready
     */
    private suspend fun transitionFromOtherStatesToReady(): Boolean {
        return lifecycleManager.ended().isSuccess &&
                lifecycleManager.ready().isSuccess
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
            withTimeout(30000) {
                transitionToPlayingState(onError)
                waitForControllerPlayState(onPlayStarted)
            }
        } catch (e: PlayStartedException) {
            // 正常控制流异常，用于跳出collect
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
                       playerState.bufferProgression >= 100f) {
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
