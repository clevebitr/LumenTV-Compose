package com.corner.service.player

import com.corner.catvodcore.bean.Result
import com.corner.catvodcore.bean.Episode

/**
 * 播放器策略接口
 * 
 * 定义不同播放器类型的播放行为，遵循策略模式
 * 每种播放器类型（Innie/Outie/Web）实现此接口
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
