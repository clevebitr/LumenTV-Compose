package com.corner.service.player

import com.corner.catvodcore.bean.Result
import com.corner.catvodcore.bean.Episode

/**
 * 播放器策略接口
 * 
 * 定义不同播放器类型的播放行为，遵循策略模式
 * 支持三种播放器类型：Innie（内部）、Outie（外部）、Web（浏览器）
 * 
 * 使用示例：
 * ```kotlin
 * // 1. 创建策略
 * val strategy = PlayerStrategyFactory.createStrategy(
 *     playerType = PlayerType.Innie.id,
 *     controller = controller,
 *     lifecycleManager = lifecycleManager,
 *     viewModelScope = scope
 * )
 * 
 * // 2. 执行播放
 * strategy.play(
 *     result = result,
 *     episode = episode,
 *     onPlayStarted = { /* 播放开始回调 */ },
 *     onError = { error -> /* 错误处理 */ }
 * )
 * ```
 * 
 * @see PlayerStrategyFactory 工厂类用于创建策略实例
 */
interface PlayerStrategy {
    /**
     * 执行播放
     * 
     * @param result 播放结果（包含URL等信息）
     * @param episode 剧集信息
     * @param onPlayStarted 播放开始回调
     * @param onError 错误回调
     */
    suspend fun play(
        result: Result,
        episode: Episode,
        onPlayStarted: () -> Unit = {},
        onError: (String) -> Unit = {}
    )
    
    /**
     * 获取策略名称（用于日志和调试）
     */
    fun getStrategyName(): String
}
