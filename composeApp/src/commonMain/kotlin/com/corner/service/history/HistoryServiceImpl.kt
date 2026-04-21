package com.corner.service.history

import com.corner.catvodcore.bean.Episode
import com.corner.catvodcore.bean.Vod
import com.corner.catvodcore.bean.Vod.Companion.getEpisode
import com.corner.catvodcore.config.ApiConfig
import com.corner.database.Db
import com.corner.database.entity.History
import com.corner.ui.player.vlcj.VlcjFrameController
import com.corner.util.net.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory

/**
 * 历史记录管理服务实现
 * 
 * 职责：
 * - 管理播放历史记录
 * - 处理历史记录的查询、创建和更新
 * - 与播放器控制器同步历史状态
 */
class HistoryServiceImpl(
    private val controllerProvider: () -> VlcjFrameController
) : HistoryService {
    
    private val log = LoggerFactory.getLogger(HistoryServiceImpl::class.java)
    private val controller: VlcjFrameController get() = controllerProvider()
    
    override suspend fun handlePlaybackHistory(
        detail: Vod,
        currentEpisodeIndex: Int
    ): Episode? {
        val historyKey = Utils.getHistoryKey(detail.site?.key!!, detail.vodId)
        log.debug("查询历史记录Key: {}", historyKey)

        val history = Db.History.findHistory(historyKey)

        val findEp = processHistory(history, detail, currentEpisodeIndex)

        syncControllerHistory(history)
        return findEp
    }
    
    override suspend fun updateCurrentEpisode(
        episode: Episode,
        detail: Vod
    ) {
        log.debug("开始更新历史记录，当前选中剧集: {}", episode.name)
        
        val existingHistory = controller.getControllerHistory()
        
        val history = if (existingHistory != null) {
            updateExistingHistory(existingHistory, episode, detail)
        } else {
            createNewHistory(episode, detail)
        }
        
        controller.setControllerHistory(history)
        log.debug("历史记录更新完成")
    }
    
    override suspend fun syncHistory(detail: Vod) {
        log.debug("[历史记录同步]开始同步历史记录，视频ID: {}, 站点: {}", detail.vodId, detail.site?.key)

        val historyKey = Utils.getHistoryKey(detail.site?.key!!, detail.vodId)
        val history = Db.History.findHistory(historyKey)
        
        if (history == null) {
            createAndSyncHistory(detail)
        } else {
            syncExistingHistory(history, detail)
        }
    }
    
    override suspend fun updateHistory(history: History) {
        if (StringUtils.isNotBlank(history.key)) {
            try {
                Db.History.update(history.copy(createTime = Clock.System.now().toEpochMilliseconds()))
            } catch (e: Exception) {
                log.error("历史记录更新失败", e)
            }
        }
    }
    
    // ==================== 私有辅助方法 ====================
    
    private fun syncControllerHistory(history: History?) {
        if (controller.getControllerHistory() == null && history != null) {
            controller.setControllerHistory(history)
        } else if (controller.getControllerHistory() != null) {
            // Controller已有历史记录，保持现状
        }
    }
    
    private fun processHistory(
        history: History?,
        detail: Vod,
        currentEpisodeIndex: Int
    ): Episode? {
        return if (history == null) {
            handleNoHistory(detail)
        } else {
            handleExistingHistory(history, detail, currentEpisodeIndex)
        }
    }
    
    private fun handleNoHistory(detail: Vod): Episode? {
        val firstEp = detail.subEpisode.firstOrNull()
        if (firstEp != null && detail.getEpisode() == null) {
            firstEp.activated = true
            log.debug("默认激活: {}", firstEp.name)
        }

        CoroutineScope(Dispatchers.IO).launch {
            val newHistory = Db.History.create(
                detail,
                detail.currentFlag.flag!!,
                detail.getEpisode()?.name ?: detail.vodName!!
            )
            log.debug("创建新历史记录完成")
            controller.setControllerHistory(newHistory)
        }
        
        return firstEp
    }
    
    private fun handleExistingHistory(
        history: History,
        detail: Vod,
        currentEpisodeIndex: Int
    ): Episode? {
        log.debug(
            "找到历史记录: vodRemarks={}, vodFlag={}, position={}",
            history.vodRemarks, history.vodFlag, history.position
        )

        configurePlaybackRange(history)
        activateHistoryFlag(history, detail)
        
        val findEp = findEpisodeFromHistory(history, detail, currentEpisodeIndex)
        if (findEp != null) {
            log.debug("根据历史记录查找剧集结果: {}", findEp.name)
        }

        controller.setControllerHistory(history)
        
        return findEp
    }
    
    private fun configurePlaybackRange(history: History) {
        val opening = history.opening ?: -1
        val ending = history.ending ?: -1
        log.debug("设置片头片尾时间: opening={}, ending={}", opening, ending)
        controller.setStartEnd(opening, ending)
    }
    
    private fun activateHistoryFlag(history: History, detail: Vod) {
        val historyFlag = detail.vodFlags.find { it.flag == history.vodFlag }
        if (historyFlag != null) {
            detail.vodFlags.forEach { flag ->
                flag.activated = flag.flag == history.vodFlag
            }
            detail.currentFlag = historyFlag
            log.debug("根据历史记录激活线路: {}", historyFlag.flag)
        }
    }
    
    private fun findEpisodeFromHistory(
        history: History,
        detail: Vod,
        currentEpisodeIndex: Int
    ): Episode? {
        log.debug(
            "根据历史记录查找剧集: vodRemarks={}, currentEpNumber={}",
            history.vodRemarks, currentEpisodeIndex
        )
        
        // 优先查找历史记录中指定的剧集
        val findEp = detail.currentFlag.episodes.find { it.name == history.vodRemarks }
        
        // 如果没找到匹配的剧集名称，再使用原来的查找方法
        return findEp ?: detail.findAndSetEpByName(history, currentEpisodeIndex)
    }
    
    private fun updateExistingHistory(history: History, ep: Episode, detail: Vod): History {
        return history.copy(
            episodeUrl = ep.url,
            vodRemarks = ep.name,
            vodFlag = detail.currentFlag.flag,
            position = 0L
        )
    }
    
    private fun createNewHistory(ep: Episode, detail: Vod): History {
        val key = Utils.getHistoryKey(detail.site?.key!!, detail.vodId)
        
        return History(
            key = key,
            vodPic = detail.vodPic ?: "",
            vodName = detail.vodName!!,
            vodFlag = detail.currentFlag.flag,
            vodRemarks = ep.name,
            episodeUrl = ep.url,
            cid = ApiConfig.api.cfg?.id!!,
            createTime = System.currentTimeMillis(),
            position = 0L
        )
    }
    
    private suspend fun createAndSyncHistory(detail: Vod) {
        log.debug("[历史记录同步]未找到历史记录，创建新的历史记录")
        val newHistory = Db.History.create(
            detail, 
            detail.currentFlag.flag!!, 
            detail.getEpisode()?.name ?: detail.vodName!!
        )
        log.debug("[历史记录同步]新历史记录创建完成: {}", newHistory)
    }
    
    private suspend fun syncExistingHistory(history: History, detail: Vod) {
        log.debug(
            "[历史记录同步]现有历史记录: vodRemarks={}, position={}",
            history.vodRemarks, history.position
        )

        val updatedHistory = adjustHistoryPositionIfNeeded(history, detail)
        configureControllerWithHistory(updatedHistory)
        
        val findEp = detail.findAndSetEpByName(updatedHistory, getCurrentEpisodeIndex(detail))
        log.debug("[历史记录同步]查找剧集结果: {}", findEp?.name ?: "未找到")
    }
    
    private fun adjustHistoryPositionIfNeeded(history: History, detail: Vod): History {
        // 注意：这里无法访问_state.value.currentEp，需要ViewModel传入当前剧集信息
        // 简化处理：直接返回原历史记录
        return history
    }
    
    private fun configureControllerWithHistory(history: History) {
        log.debug(
            "[历史记录同步]设置控制器历史记录: vodFlag={}, vodRemarks={}, position={}",
            history.vodFlag, history.vodRemarks, history.position
        )
        controller.setControllerHistory(history)

        log.debug(
            "[历史记录同步]设置片头片尾时间: opening={}, ending={}",
            history.opening, history.ending
        )
        controller.setStartEnd(history.opening ?: -1, history.ending ?: -1)
    }
    
    private fun getCurrentEpisodeIndex(detail: Vod): Int {
        // 从detail中获取当前激活的剧集索引
        val activeEp = detail.subEpisode.find { it.activated }
        return activeEp?.number ?: 1
    }
}
