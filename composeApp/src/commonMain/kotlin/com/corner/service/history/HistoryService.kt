package com.corner.service.history

import com.corner.catvodcore.bean.Episode
import com.corner.catvodcore.bean.Vod
import com.corner.database.entity.History

/**
 * 历史记录管理服务接口
 * 
 * 职责：
 * - 管理播放历史记录
 * - 处理历史记录的查询、创建和更新
 * - 与播放器控制器同步历史状态
 */
interface HistoryService {
    /**
     * 查询并处理播放历史
     * @param detail 视频详情
     * @param currentEpisodeIndex 当前剧集索引
     * @return 应该播放的剧集，如果没有历史则返回null
     */
    suspend fun handlePlaybackHistory(
        detail: Vod,
        currentEpisodeIndex: Int
    ): Episode?
    
    /**
     * 根据新选中的剧集更新播放历史记录
     * @param episode 选中的剧集
     * @param detail 视频详情
     */
    suspend fun updateCurrentEpisode(
        episode: Episode,
        detail: Vod
    )
    
    /**
     * 同步视频播放的历史记录
     * @param detail 视频详情
     */
    suspend fun syncHistory(detail: Vod)
    
    /**
     * 更新历史记录信息
     * @param history 要更新的历史记录
     */
    suspend fun updateHistory(history: History)
}
