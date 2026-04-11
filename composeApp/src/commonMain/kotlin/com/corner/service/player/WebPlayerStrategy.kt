package com.corner.service.player

import com.corner.catvodcore.bean.Result
import com.corner.catvodcore.bean.Episode
import com.corner.catvodcore.bean.v
import com.corner.util.play.BrowserUtils
import org.slf4j.LoggerFactory

/**
 * Web播放器策略（Web）
 * 
 * 使用浏览器内置播放器进行播放
 */
class WebPlayerStrategy : PlayerStrategy {
    
    private val log = LoggerFactory.getLogger(WebPlayerStrategy::class.java)
    
    override suspend fun play(
        result: Result,
        episode: Episode,
        onPlayStarted: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            log.info("使用Web播放器播放: {}", episode.name)
            
            // 在浏览器中打开Web播放器
            BrowserUtils.openBrowserWithWebPlayer(
                videoUrl = result.url.v(),
                episodeName = episode.name,
                episodeNumber = episode.number
            )
            
            // 通知播放开始
            onPlayStarted()
            
        } catch (e: Exception) {
            log.error("Web播放器播放失败", e)
            onError("Web播放器启动失败: ${e.message}")
        }
    }
    
    override fun getStrategyName(): String = "WebPlayer"
}
