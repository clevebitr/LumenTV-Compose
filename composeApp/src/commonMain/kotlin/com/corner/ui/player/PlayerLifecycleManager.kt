package com.corner.ui.player

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.slf4j.LoggerFactory

/**
 * 统一播放器生命周期管理器
 * 负责协调播放器状态转换和资源管理
 */
class PlayerLifecycleManager(
    private val controller: PlayerController
) {
    private val log = LoggerFactory.getLogger("PlayerLifecycleManager")

    private val _lifecycleState = MutableStateFlow(PlayerLifecycleState.Idle)
    val lifecycleState: StateFlow<PlayerLifecycleState> = _lifecycleState

    private val lifecycleDispatcher = Dispatchers.IO

    /**
     * 状态转换
     */
    suspend fun transitionTo(newState: PlayerLifecycleState): Result<Unit> {
        return withContext(lifecycleDispatcher) {
            try {
                log.debug("播放器状态转换: {} -> {}", _lifecycleState.value, newState)

                // 状态验证
                if (!isValidTransition(_lifecycleState.value, newState)) {
                    log.warn("无效的状态转换: ${_lifecycleState.value} -> $newState")
                    return@withContext Result.failure(
                        IllegalStateException("无效的状态转换: ${_lifecycleState.value} -> $newState")
                    )
                }

                _lifecycleState.value = newState

                // 执行状态对应的操作
                when (newState) {
                    PlayerLifecycleState.Released -> releaseInternal()
                    PlayerLifecycleState.Paused -> stopInternal()
                    PlayerLifecycleState.Initializing -> initializeSyncInternal()
                    PlayerLifecycleState.Ready -> readyInternal()
                    PlayerLifecycleState.Loading -> loadingInternal()
                    PlayerLifecycleState.Playing -> playingInternal()
                    PlayerLifecycleState.Ended -> endedInternal()
                    else -> Result.success(Unit)
                }
            } catch (e: Exception) {
                log.error("状态转换失败", e)
                _lifecycleState.value = PlayerLifecycleState.Error
                Result.failure(e)
            }
        }
    }

    /**
     * 安全地执行状态转换
     * @param targetState 目标状态
     * @param action 状态转换动作
     * @return 转换是否成功执行
     */
    suspend fun transitionTo(targetState: PlayerLifecycleState, action: suspend () -> Result<Unit>): Result<Unit> {
        return if (canTransitionTo(targetState)) {
            action()
        } else {
            Result.failure(IllegalStateException("Cannot transition to $targetState from current state ${lifecycleState.value}"))
        }
    }

    fun canTransitionTo(target: PlayerLifecycleState): Boolean {
        return isValidTransition(lifecycleState.value, target)
    }

    /**
     * 同步初始化
     */
    suspend fun initializeSync(): Result<Unit> = transitionTo(PlayerLifecycleState.Initializing)

    private suspend fun initializeSyncInternal(): Result<Unit> {
        return withContext(lifecycleDispatcher) {
            try {
                controller.vlcjFrameInit()
                _lifecycleState.value = PlayerLifecycleState.Initialized
                Result.success(Unit)
            } catch (e: Exception) {
                log.error("同步初始化失败", e)
                Result.failure(e)
            }
        }
    }


    /**
     * 清理资源
     */
    suspend fun cleanup(): Result<Unit> {
        return withContext(lifecycleDispatcher) {
            try {
                controller.cleanupAsync()
                Result.success(Unit)
            } catch (e: Exception) {
                log.error("清理资源失败:", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 停止播放
     */
    suspend fun stop(): Result<Unit> = transitionTo(PlayerLifecycleState.Paused)

    private suspend fun stopInternal(): Result<Unit> {
        return withContext(lifecycleDispatcher) {
            try {
                controller.pause()
                Result.success(Unit)
            } catch (e: Exception) {
                log.error("停止播放失败", e)
                Result.failure(e)
            }
        }
    }

    suspend fun ended(): Result<Unit> = transitionTo(PlayerLifecycleState.Ended)
    
    /**
     * 停止播放媒体
     */
    private suspend fun endedInternal(): Result<Unit> {
        return withContext(lifecycleDispatcher) {
            try {
                log.debug("<LifecycleManager> -- 停止播放媒体")
                controller.stop()
                Result.success(Unit)
            } catch (e: Exception) {
                log.error("播放结束失败", e)
                Result.failure(e)
            }
        }
    }


    /**
     * 完全释放资源
     */
    suspend fun release(): Result<Unit> = transitionTo(PlayerLifecycleState.Released)

    private suspend fun releaseInternal(): Result<Unit> {
        return withContext(lifecycleDispatcher) {
            try {
                synchronized(controller) {
                    try {
                        controller.dispose()
                    } catch (e: Exception) {
                        // 忽略已释放的错误
                        if (e.message?.contains("Invalid memory access") == true ||
                            e.message?.contains("already released") == true
                        ) {
                            log.debug("资源已释放，忽略错误")
                        } else {
                            throw e
                        }
                    }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                log.error("释放失败", e)
                Result.failure(e)
            }
        }
    }

    suspend fun ready(): Result<Unit> = transitionTo(PlayerLifecycleState.Ready)
    private suspend fun readyInternal(): Result<Unit> {
        return withContext(lifecycleDispatcher) {
            try {
                // 检查播放器实例是否存在
                if (controller.isPlayerInstanceReady()) {
                    Result.success(Unit)
                } else {
                    log.error("播放器实例不存在")
                    Result.failure(IllegalStateException("Player not initialized"))
                }
            } catch (e: Exception) {
                log.error("就绪检查失败", e)
                Result.failure(e)
            }
        }
    }

    suspend fun loading(): Result<Unit> = transitionTo(PlayerLifecycleState.Loading)
    private suspend fun loadingInternal(): Result<Unit> {
        return withContext(lifecycleDispatcher) {
            try {
                Result.success(Unit)
            } catch (e: Exception) {
                log.error("加载失败", e)
                Result.failure(e)
            }
        }
    }

    suspend fun start() = transitionTo(PlayerLifecycleState.Playing)

    private suspend fun playingInternal(): Result<Unit> {
        return withContext(lifecycleDispatcher) {
            try {
                // 如果已经在播放，直接返回成功
                if (controller.playerPlaying) {
                    log.debug("播放器已经在播放状态")
                    return@withContext Result.success(Unit)
                }

                controller.play()
                
                Result.success(Unit)
            } catch (e: Exception) {
                log.error("播放失败", e)
                Result.failure(e)
            }
        }
    }


    /**
     * 准备播放器到 Ready 状态（自动处理各种状态转换）
     * 
     * 这是 InniePlayerStrategy 的便捷方法，封装了复杂的状态转换逻辑
     * 根据当前状态自动选择最优的转换路径
     */
    suspend fun prepareForPlayback(): Result<Unit> {
        return when (lifecycleState.value) {
            PlayerLifecycleState.Ready -> {
                log.debug("播放器已处于 Ready 状态，无需准备")
                Result.success(Unit)
            }
            PlayerLifecycleState.Playing -> {
                log.debug("从 Playing 状态准备播放")
                val stopResult = stop()
                if (stopResult.isFailure) return stopResult
                
                val endedResult = ended()
                if (endedResult.isFailure) return endedResult
                
                ready()
            }
            PlayerLifecycleState.Loading, 
            PlayerLifecycleState.Ended -> {
                log.debug("从 {} 状态准备播放", lifecycleState.value)
                ready()
            }
            PlayerLifecycleState.Error -> {
                log.debug("从 Error 状态恢复并准备播放")
                recoverFromError()
            }
            PlayerLifecycleState.Initialized -> {
                log.debug("从 Initialized 状态准备播放")
                val loadingResult = loading()
                if (loadingResult.isFailure) return loadingResult
                
                ready()
            }
            else -> {
                log.debug("从 {} 状态准备播放（通用路径）", lifecycleState.value)
                val endedResult = ended()
                if (endedResult.isFailure) return endedResult
                
                ready()
            }
        }
    }

    /**
     * 从错误状态恢复
     */
    private suspend fun recoverFromError(): Result<Unit> {
        val cleanupResult = cleanup()
        cleanupResult.onFailure { log.warn("清理资源失败，继续尝试重新初始化: {}", it.message) }
        
        val initResult = initializeSync()
        if (initResult.isFailure) return initResult
        
        val loadingResult = loading()
        if (loadingResult.isFailure) return loadingResult
        
        return ready()
    }

    /**
     * 验证状态转换的合法性
     */
    private fun isValidTransition(from: PlayerLifecycleState, to: PlayerLifecycleState): Boolean {
        return when (from) {
            PlayerLifecycleState.Idle -> to in listOf(
                PlayerLifecycleState.Initializing,
                PlayerLifecycleState.Released
            )

            PlayerLifecycleState.Initializing -> to in listOf(
                PlayerLifecycleState.Initialized,
                PlayerLifecycleState.Error
            )

            PlayerLifecycleState.Initialized -> to in listOf(
                PlayerLifecycleState.Loading,
                PlayerLifecycleState.Released
            )

            PlayerLifecycleState.Loading -> to in listOf(
                PlayerLifecycleState.Ready,
                PlayerLifecycleState.Error,
                PlayerLifecycleState.Ended,
                PlayerLifecycleState.Released
            )

            PlayerLifecycleState.Ready -> to in listOf(
                PlayerLifecycleState.Playing,
                PlayerLifecycleState.Paused,
                PlayerLifecycleState.Released
            )

            PlayerLifecycleState.Playing -> to in listOf(
                PlayerLifecycleState.Paused,
                PlayerLifecycleState.Ended,
                PlayerLifecycleState.Released
            )

            PlayerLifecycleState.Paused -> to in listOf(
                PlayerLifecycleState.Playing,
                PlayerLifecycleState.Ended,
                PlayerLifecycleState.Released
            )

            PlayerLifecycleState.Ended -> to in listOf(
                PlayerLifecycleState.Ready,
                PlayerLifecycleState.Released
            )

            PlayerLifecycleState.Error -> to in listOf(
                PlayerLifecycleState.Initializing,
                PlayerLifecycleState.Released
            )

            PlayerLifecycleState.Released -> to == PlayerLifecycleState.Idle
        }
    }
}