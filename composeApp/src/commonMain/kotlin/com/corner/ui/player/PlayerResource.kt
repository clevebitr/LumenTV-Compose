package com.corner.ui.player

import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer

/**
 * 播放器资源管理接口
 * 
 * 负责播放器的生命周期管理和资源清理
 * 遵循单一职责原则（SRP），只包含资源管理相关的方法
 */
interface PlayerResource {
    /**
     * 初始化播放器
     */
    fun init()
    
    /**
     * 异步清理资源
     */
    suspend fun cleanupAsync()
    
    /**
     * 释放播放器资源
     */
    fun dispose()
    
    /**
     * 当 MediaPlayer 就绪时的回调
     */
    fun onMediaPlayerReady(mediaPlayer: EmbeddedMediaPlayer)
    
    /**
     * 对 MediaPlayer 执行操作（延迟执行）
     */
    fun doWithMediaPlayer(block: (MediaPlayer) -> Unit)
}
