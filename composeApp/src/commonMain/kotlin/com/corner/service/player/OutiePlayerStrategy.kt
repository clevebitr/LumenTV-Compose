package com.corner.service.player

import com.corner.catvodcore.bean.Result
import com.corner.catvodcore.bean.Episode
import com.corner.catvodcore.bean.v
import com.corner.util.play.Play
import org.slf4j.LoggerFactory

/**
 * 外部播放器策略（Outie）
 * 
 * 使用系统外部播放器进行播放
 */
class OutiePlayerStrategy : PlayerStrategy {
    
    private val log = LoggerFactory.getLogger(OutiePlayerStrategy::class.java)
    
    override suspend fun play(
        result: Result,
        episode: Episode,
        onPlayStarted: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            log.info("使用外部播放器播放: {}", episode.name)
            
            // 启动外部播放器
            Play.start(result.url.v(), episode.name)
            
            // 通知播放开始
            onPlayStarted()
            
        } catch (e: Exception) {
            log.error("外部播放器播放失败", e)
            onError("外部播放器启动失败: ${e.message}")
        }
    }
    
    override fun getStrategyName(): String = "OutiePlayer"
}
