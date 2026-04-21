package com.corner.service.player

/**
 * 播放器策略配置常量
 * 
 * 集中管理所有播放器相关的配置参数，避免硬编码
 * 
 * **使用示例：**
 * ```kotlin
 * // 1. 在代码中直接使用常量
 * controller.loadURL(url, PlayerStrategyConfig.INNIE_LOAD_URL_TIMEOUT_MS)
 * 
 * // 2. 在超时操作中使用
 * withTimeout(PlayerStrategyConfig.INNIE_PLAYBACK_START_TIMEOUT_MS) {
 *     // 播放逻辑
 * }
 * 
 * // 3. 条件判断中使用
 * if (bufferProgression >= PlayerStrategyConfig.INNIE_BUFFER_COMPLETE_THRESHOLD) {
 *     // 缓冲完成
 * }
 * ```
 * 
 * **配置调优建议：**
 * - 网络较差时，可增加 `INNIE_LOAD_URL_TIMEOUT_MS` 到 8000-10000ms
 * - 对于大文件，可增加 `INNIE_PLAYBACK_START_TIMEOUT_MS` 到 45000-60000ms
 * - 如需提前开始播放（边下边播），可降低 `INNIE_BUFFER_COMPLETE_THRESHOLD` 到 30-50f
 */
object PlayerStrategyConfig {
    
    // ==================== Innie（内部播放器）配置 ====================
    
    /**
     * 加载媒体URL的超时时间（毫秒）
     * 用于 controller.loadURL() 操作
     */
    const val INNIE_LOAD_URL_TIMEOUT_MS = 5000L
    
    /**
     * 等待播放启动的超时时间（毫秒）
     * 用于 waitForPlaybackToStart() 操作
     */
    const val INNIE_PLAYBACK_START_TIMEOUT_MS = 30000L
    
    /**
     * 缓冲完成阈值（百分比）
     * 当缓冲进度达到此值时认为可以开始播放
     */
    const val INNIE_BUFFER_COMPLETE_THRESHOLD = 100f
    
    // ==================== DetailViewModel 配置 ====================
    
    /**
     * 播放器加载超时时间（毫秒）
     * 用于 startPlaybackWithTimeout() 操作
     */
    const val DETAIL_PLAYBACK_LOAD_TIMEOUT_MS = 30000L
    
    /**
     * 历史记录查询超时时间（毫秒）
     * 用于数据库查询操作
     */
    const val DETAIL_HISTORY_QUERY_TIMEOUT_MS = 3000L
    
    // ==================== 通用配置 ====================
    
    /**
     * 默认重试次数
     */
    const val DEFAULT_RETRY_COUNT = 3
    
    /**
     * 默认重试间隔（毫秒）
     */
    const val DEFAULT_RETRY_INTERVAL_MS = 1000L
}
