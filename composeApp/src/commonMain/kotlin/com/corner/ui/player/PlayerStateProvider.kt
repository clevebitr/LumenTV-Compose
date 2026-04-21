package com.corner.ui.player

import com.corner.database.entity.History
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 播放器状态提供者接口
 * 
 * 负责提供播放器的各种状态信息
 * 遵循单一职责原则（SRP），只包含状态查询相关的属性和方法
 */
interface PlayerStateProvider {
    /**
     * 播放器状态流
     */
    val state: StateFlow<PlayerState>
    
    /**
     * 显示提示标志
     */
    var showTip: MutableStateFlow<Boolean>
    
    /**
     * 提示文本
     */
    var tip: MutableStateFlow<String>
    
    /**
     * 播放历史记录
     */
    var history: MutableStateFlow<History?>
    
    /**
     * 结束标记是否已处理
     */
    var endingHandled: Boolean
    
    /**
     * 播放器加载状态
     */
    var playerLoading: Boolean
    
    /**
     * 播放器播放状态
     */
    var playerPlaying: Boolean
}
