package com.corner.ui.player

/**
 * 播放器控制器接口
 * 
 * 为了保持向后兼容性，PlayerController 现在是一个组合接口，
 * 继承了所有专用的子接口：
 * - MediaPlayer: 媒体播放控制
 * - PlayerStateProvider: 状态提供者
 * - PlayerResource: 资源管理
 * - PlayerBookmarkManager: 片头片尾管理
 * 
 * 新代码建议直接使用具体的子接口，以实现更好的职责分离
 */
interface PlayerController : 
    MediaPlayer,
    PlayerStateProvider,
    PlayerResource,
    PlayerBookmarkManager {
    
    /**
     * 切换全屏状态
     */
    fun toggleFullscreen()
    
    /**
     * VLCJ Frame 初始化（可选）
     */
    fun vlcjFrameInit() {}
}