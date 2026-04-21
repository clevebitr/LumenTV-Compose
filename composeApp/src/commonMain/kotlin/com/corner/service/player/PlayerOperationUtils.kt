package com.corner.service.player

import com.corner.ui.scene.SnackBar
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory

/**
 * 播放器操作工具类
 * 
 * 提供统一的异步操作包装和错误处理机制，消除重复的 try-catch 代码
 */
object PlayerOperationUtils {
    
    private val log = LoggerFactory.getLogger(PlayerOperationUtils::class.java)
    
    /**
     * 安全执行播放器操作
     * 
     * @param operationName 操作名称（用于日志）
     * @param showUserError 是否向用户显示错误提示
     * @param timeoutMillis 超时时间（毫秒），null 表示不设置超时
     * @param block 要执行的操作
     * @return 操作结果（Result 包装）
     */
    suspend fun <T> safeExecute(
        operationName: String,
        showUserError: Boolean = true,
        timeoutMillis: Long? = null,
        block: suspend () -> T
    ): Result<T> {
        return try {
            val result = if (timeoutMillis != null) {
                withTimeout(timeoutMillis) { block() }
            } else {
                block()
            }
            Result.success(result)
        } catch (e: TimeoutCancellationException) {
            log.warn("[{}] 操作超时", operationName)
            if (showUserError) {
                SnackBar.postMsg("$operationName 超时，请检查网络连接", type = SnackBar.MessageType.WARNING)
            }
            Result.failure(e)
        } catch (e: Exception) {
            log.error("[{}] 操作失败", operationName, e)
            if (showUserError) {
                SnackBar.postMsg("$operationName 失败: ${e.message}", type = SnackBar.MessageType.ERROR)
            }
            Result.failure(e)
        }
    }
    
    /**
     * 带重试的安全执行
     * 
     * @param operationName 操作名称（用于日志）
     * @param maxRetries 最大重试次数（默认 3 次）
     * @param retryDelayMs 重试间隔（毫秒，默认 1000ms）
     * @param block 要执行的操作
     * @return 操作结果（Result 包装）
     */
    suspend fun <T> safeExecuteWithRetry(
        operationName: String,
        maxRetries: Int = PlayerStrategyConfig.DEFAULT_RETRY_COUNT,
        retryDelayMs: Long = PlayerStrategyConfig.DEFAULT_RETRY_INTERVAL_MS,
        block: suspend () -> T
    ): Result<T> {
        var lastError: Throwable? = null
        
        repeat(maxRetries + 1) { attempt ->
            val result = safeExecute(operationName, showUserError = false) { block() }
            
            if (result.isSuccess) {
                return result
            }
            
            lastError = result.exceptionOrNull()
            
            if (attempt < maxRetries) {
                log.warn("[{}] 第 {} 次重试失败，{}ms 后重试...", operationName, attempt + 1, retryDelayMs)
                kotlinx.coroutines.delay(retryDelayMs)
            }
        }
        
        log.error("[{}] 所有重试均失败", operationName)
        SnackBar.postMsg("$operationName 失败，已重试 ${maxRetries} 次", type = SnackBar.MessageType.ERROR)
        return Result.failure(lastError ?: Exception("未知错误"))
    }
}
