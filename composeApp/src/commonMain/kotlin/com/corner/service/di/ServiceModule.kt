package com.corner.service.di

import com.corner.service.episode.EpisodeManager
import com.corner.service.episode.EpisodeManagerImpl
import com.corner.service.history.HistoryService
import com.corner.service.history.HistoryServiceImpl
import com.corner.ui.player.vlcj.VlcjFrameController

/**
 * Service层依赖注入模块
 * 
 * 提供Service实例的创建和管理
 * 当前使用简单的手动DI，后续可迁移到Koin等DI框架
 */
object ServiceModule {
    
    /**
     * 创建HistoryService实例
     * @param controllerProvider 播放器控制器提供者
     */
    fun provideHistoryService(
        controllerProvider: () -> VlcjFrameController
    ): HistoryService {
        return HistoryServiceImpl(controllerProvider)
    }
    
    /**
     * 创建EpisodeManager实例
     */
    fun provideEpisodeManager(): EpisodeManager {
        return EpisodeManagerImpl()
    }
}
