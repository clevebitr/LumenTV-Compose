package com.corner.service.episode

import com.corner.catvodcore.bean.Episode
import com.corner.catvodcore.bean.Vod
import com.corner.catvodcore.bean.Vod.Companion.getPage
import com.corner.ui.player.PlayerLifecycleManager
import com.corner.util.core.Constants

/**
 * EpisodeManager实现类
 * 
 * 职责：
 * - 管理剧集选择和导航
 * - 处理剧集分组和分页
 * - 管理剧集激活状态
 */
class EpisodeManagerImpl : EpisodeManager {
    
    override fun getNextEpisodeUrl(
        detail: Vod,
        currentEp: Episode?
    ): String? {
        val currentEpToUse = currentEp ?: detail.subEpisode.firstOrNull() ?: return null
        
        // 如果没有激活的剧集，从当前分组的第一个开始
        if (currentEp == null) {
            return decryptUrl(currentEpToUse.url, detail)
        }
        
        val currentIndex = detail.subEpisode.indexOf(currentEp)
        val nextIndex = currentIndex + 1
        
        // 处理分组切换
        if (nextIndex >= detail.subEpisode.size) {
            val nextTabIndex = detail.currentTabIndex + 1
            
            // 计算总分组数
            val totalEpisodes = detail.currentFlag.episodes.size
            val totalPages = (totalEpisodes + Constants.EP_SIZE - 1) / Constants.EP_SIZE
            
            // 检查是否有更多分组
            if (nextTabIndex >= totalPages) {
                return null // 没有更多剧集
            }
            
            // 切换到下一分组
            val start = nextTabIndex * Constants.EP_SIZE
            val end = minOf(start + Constants.EP_SIZE, totalEpisodes)
            val newSubEpisodes = detail.currentFlag.episodes.subList(start, end)
            
            val newFirstEp = newSubEpisodes.firstOrNull() ?: return null
            return decryptUrl(newFirstEp.url, detail)
        }
        
        // 正常切换到下一集
        val nextEp = detail.subEpisode[nextIndex]
        return decryptUrl(nextEp.url, detail)
    }
    
    private fun decryptUrl(url: String, detail: Vod): String? {
        // 注意：这里需要调用SiteViewModel.playerContent，但由于是Service层
        // 应该由ViewModel层来处理解密逻辑，Service只返回Episode对象
        // 因此这个方法返回原始URL，由ViewModel决定如何解密
        return url
    }
    
    override fun nextEpisode(
        detail: Vod,
        currentEp: Episode?,
        onPlayEpisode: (Vod, Episode) -> Unit
    ) {
        if (currentEp == null) {
            return
        }
        
        val currentIndex = detail.subEpisode.indexOf(currentEp)
        val nextIndex = currentIndex + 1
        val totalEpisodes = detail.currentFlag.episodes.size
        
        if (totalEpisodes <= nextIndex) {
            return
        }
        
        if (shouldSwitchToNextGroup(currentIndex)) {
            // 切换到下一分组
            val nextTabIndex = detail.currentTabIndex + 1
            val totalEpisodesCount = detail.currentFlag.episodes.size
            val totalPages = (totalEpisodesCount + Constants.EP_SIZE - 1) / Constants.EP_SIZE
            
            if (nextTabIndex >= totalPages) {
                return
            }
            
            val newFirstEp = detail.currentFlag.episodes[nextTabIndex * Constants.EP_SIZE]
            onPlayEpisode(detail, newFirstEp)
        } else {
            // 播放当前分组的下一集
            val nextEp = detail.subEpisode[nextIndex]
            onPlayEpisode(detail, nextEp)
        }
    }
    
    private fun shouldSwitchToNextGroup(currentIndex: Int): Boolean {
        return currentIndex >= Constants.EP_SIZE - 1
    }
    
    override fun chooseEpisodeBatch(
        detail: Vod,
        index: Int,
        currentEpUrl: String?
    ): Vod {
        val newTabIndex = index / Constants.EP_SIZE
        val newSubEpisodes = detail.currentFlag.episodes.getPage(newTabIndex).toMutableList()
        
        // 恢复激活状态
        restoreActivationState(newSubEpisodes, currentEpUrl)
        
        return detail.copy(
            currentTabIndex = newTabIndex,
            subEpisode = newSubEpisodes
        )
    }
    
    private fun restoreActivationState(episodes: MutableList<Episode>, activeUrl: String?) {
        if (activeUrl != null) {
            episodes.forEach { episode ->
                episode.activated = (episode.url == activeUrl)
            }
        } else if (episodes.isNotEmpty()) {
            episodes[0].activated = true
        }
    }
    
    override suspend fun chooseEpisode(
        episode: Episode,
        detail: Vod,
        playerTypeId: String,
        lifecycleManager: PlayerLifecycleManager,
        onOpenUri: (String) -> Unit,
        onPlayEpisode: (Vod, Episode) -> Unit
    ) {
        // 更新剧集激活状态
        val updatedDetail = updateEpisodeActivationStates(detail, episode)
        
        // 如果需要，停止当前播放
        stopCurrentPlaybackIfNeeded(playerTypeId, lifecycleManager)
        
        // 播放新剧集（包括磁力链接）
        onPlayEpisode(updatedDetail, episode)
    }
    
    private fun updateEpisodeActivationStates(detail: Vod, targetEpisode: Episode): Vod {
        val updatedCurrentFlagEpisodes = detail.currentFlag.episodes.map { ep ->
            ep.copy(activated = ep.url == targetEpisode.url)
        }.toMutableList()
        
        val updatedSubEpisodes = detail.subEpisode.map { ep ->
            ep.copy(activated = ep.url == targetEpisode.url)
        }.toMutableList()
        
        return detail.copy(
            currentFlag = detail.currentFlag.copy(episodes = updatedCurrentFlagEpisodes),
            subEpisode = updatedSubEpisodes
        )
    }
    
    private fun stopCurrentPlaybackIfNeeded(
        playerTypeId: String,
        lifecycleManager: PlayerLifecycleManager
    ) {
        // 注意：这里无法直接访问PlayerType.Innie.id，需要由调用方判断
        // Service层不应该依赖具体的播放器类型常量
        // 这个逻辑应该在ViewModel中处理
    }
    
    override fun updateEpisodeActivation(
        detail: Vod,
        activeEp: Episode,
        newTabIndex: Int?,
        newSubEpisodes: List<Episode>?
    ): Vod {
        // 清除所有剧集的激活状态
        clearAllEpisodesActivation(detail)
        
        // 激活目标剧集
        activateTargetEpisode(activeEp)
        
        // 构建更新后的子剧集列表
        val updatedSubEpisodes = buildUpdatedSubEpisodes(detail, activeEp, newSubEpisodes)
        
        return detail.copy(
            currentTabIndex = newTabIndex ?: detail.currentTabIndex,
            subEpisode = updatedSubEpisodes
        )
    }
    
    private fun clearAllEpisodesActivation(detail: Vod) {
        detail.currentFlag.episodes.forEach { episode ->
            episode.activated = false
        }
    }
    
    private fun activateTargetEpisode(activeEp: Episode) {
        activeEp.activated = true
    }
    
    private fun buildUpdatedSubEpisodes(
        detail: Vod,
        activeEp: Episode,
        newSubEpisodes: List<Episode>?
    ): MutableList<Episode> {
        return if (newSubEpisodes != null) {
            newSubEpisodes.map { ep -> ep.copy(activated = ep.url == activeEp.url) }.toMutableList()
        } else {
            detail.subEpisode.map { ep -> ep.copy(activated = ep.url == activeEp.url) }.toMutableList()
        }
    }
}
