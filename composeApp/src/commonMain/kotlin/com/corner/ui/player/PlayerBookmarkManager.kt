package com.corner.ui.player

import com.corner.catvodcore.bean.Vod
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 播放器片头片尾管理接口
 * 
 * 负责管理视频的片头和片尾标记
 */
interface PlayerBookmarkManager {
    /**
     * 更新片尾标记
     */
    fun updateEnding(detail: Vod?)
    
    /**
     * 更新片头标记
     */
    fun updateOpening(detail: Vod?)
    
    /**
     * 设置片头片尾时间
     */
    fun setStartEnding(opening: Long, ending: Long)
    
    /**
     * 重置片头片尾标记
     */
    fun resetOpeningEnding()
    
    /**
     * 对播放状态执行操作
     */
    fun doWithPlayState(func: (MutableStateFlow<PlayerState>) -> Unit)
}
