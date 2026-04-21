package com.corner.util.core

import com.corner.catvodcore.bean.Episode
import com.corner.catvodcore.bean.Flag
import com.corner.catvodcore.bean.Vod
import com.corner.catvodcore.bean.Vod.Companion.getPage

/**
 * 根据新的线路和剧集信息构建更新后的 Vod 对象
 */
fun Vod.buildUpdatedDetail(selectedFlag: Flag, newEp: Episode?): Vod {
    var newTabIndex = currentTabIndex
    
    if (newEp != null) {
        val newEpisodeIndex = selectedFlag.episodes.indexOfFirst { ep -> ep.number == newEp.number }
        if (newEpisodeIndex != -1) {
            newTabIndex = newEpisodeIndex / Constants.EP_SIZE
        }
    }
    
    val maxTabIndex = if (selectedFlag.episodes.isNotEmpty()) {
        (selectedFlag.episodes.size - 1) / Constants.EP_SIZE
    } else {
        0
    }
    
    val finalTabIndex = minOf(newTabIndex, maxTabIndex)
    
    return this.copy(
        currentFlag = selectedFlag,
        currentTabIndex = finalTabIndex,
        subEpisode = selectedFlag.episodes.getPage(finalTabIndex).toMutableList()
    )
}

/**
 * 更新 Vod 中所有线路的激活状态
 */
fun Vod.updateFlagActivationStates(selectedFlag: Flag) {
    vodFlags.forEach { flag ->
        flag.activated = (flag.show == selectedFlag.show)
        if (flag.activated) {
            currentFlag = flag
        }
    }
}
