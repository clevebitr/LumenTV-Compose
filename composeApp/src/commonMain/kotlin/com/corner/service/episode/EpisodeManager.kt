package com.corner.service.episode

import com.corner.catvodcore.bean.Episode
import com.corner.catvodcore.bean.Vod
import com.corner.ui.player.PlayerLifecycleManager

/**
 * 剧集管理服务接口
 * 
 * 职责：
 * - 管理剧集选择和导航
 * - 处理剧集分组和分页
 * - 管理剧集激活状态
 */
interface EpisodeManager {
    /**
     * 获取下一集的URL
     * @param detail 视频详情
     * @param currentEp 当前剧集
     * @return 下一集URL，如果没有更多剧集返回null
     */
    fun getNextEpisodeUrl(
        detail: Vod,
        currentEp: Episode?
    ): String?
    
    /**
     * 切换到下一集
     * @param detail 视频详情
     * @param currentEp 当前剧集
     * @param onPlayEpisode 播放剧集的回调
     */
    fun nextEpisode(
        detail: Vod,
        currentEp: Episode?,
        onPlayEpisode: (Vod, Episode) -> Unit
    )
    
    /**
     * 批量选择剧集分组
     * @param detail 视频详情
     * @param index 剧集索引
     * @param currentEpUrl 当前激活的剧集URL
     * @return 更新后的视频详情
     */
    fun chooseEpisodeBatch(
        detail: Vod,
        index: Int,
        currentEpUrl: String?
    ): Vod
    
    /**
     * 选择指定剧集
     * @param episode 要选择的剧集
     * @param detail 视频详情
     * @param playerTypeId 播放器类型ID（String）
     * @param lifecycleManager 播放器生命周期管理器
     * @param onOpenUri 打开URI的回调（用于下载链接）
     * @param onPlayEpisode 播放剧集的回调
     */
    suspend fun chooseEpisode(
        episode: Episode,
        detail: Vod,
        playerTypeId: String,
        lifecycleManager: PlayerLifecycleManager,
        onOpenUri: (String) -> Unit,
        onPlayEpisode: (Vod, Episode) -> Unit
    )
    
    /**
     * 更新剧集激活状态
     * @param detail 视频详情
     * @param activeEp 要激活的剧集
     * @param newTabIndex 新的标签页索引（可选）
     * @param newSubEpisodes 新的子剧集列表（可选）
     * @return 更新后的视频详情
     */
    fun updateEpisodeActivation(
        detail: Vod,
        activeEp: Episode,
        newTabIndex: Int? = null,
        newSubEpisodes: List<Episode>? = null
    ): Vod
}
