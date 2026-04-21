package com.corner.service.player

import com.corner.service.player.PlayerType
import com.corner.ui.player.PlayerLifecycleManager
import com.corner.ui.player.vlcj.VlcjFrameController
import kotlinx.coroutines.CoroutineScope

/**
 * 播放器策略工厂
 * 
 * 根据播放器类型创建对应的策略实例
 * 遵循工厂模式，封装策略对象的创建逻辑
 * 
 * 使用示例：
 * ```kotlin
 * // 方式1: 通过播放器类型创建（推荐）
 * val strategy = PlayerStrategyFactory.createStrategy(
 *     playerType = PlayerType.Outie.id,
 *     controller = controller,      // Innie需要
 *     lifecycleManager = manager,   // Innie需要
 *     viewModelScope = scope        // Innie需要
 * )
 * strategy.play(result, episode, onPlayStarted = {}, onError = {})
 * 
 * // 方式2: 获取策略名称（用于日志）
 * val name = PlayerStrategyFactory.getStrategyName(PlayerType.Web.id)
 * ```
 */
object PlayerStrategyFactory {
    
    /**
     * 创建播放器策略
     * 
     * @param playerType 播放器类型ID（innie/outie/web）
     * @param controller VLCJ控制器（仅Innie需要）
     * @param lifecycleManager 播放器生命周期管理器（仅Innie需要）
     * @param viewModelScope ViewModel协程作用域（仅Innie需要）
     * @return 对应的播放器策略实例
     */
    fun createStrategy(
        playerType: String,
        controller: VlcjFrameController? = null,
        lifecycleManager: PlayerLifecycleManager? = null,
        viewModelScope: CoroutineScope? = null
    ): PlayerStrategy {
        return when (playerType.lowercase()) {
            PlayerType.Innie.id -> {
                requireNotNull(controller) { "Innie播放器需要提供controller" }
                requireNotNull(lifecycleManager) { "Innie播放器需要提供lifecycleManager" }
                requireNotNull(viewModelScope) { "Innie播放器需要提供viewModelScope" }
                InniePlayerStrategy(controller, lifecycleManager, viewModelScope)
            }
            PlayerType.Outie.id -> OutiePlayerStrategy()
            PlayerType.Web.id -> WebPlayerStrategy()
            else -> {
                // 默认使用外部播放器
                OutiePlayerStrategy()
            }
        }
    }
    
    /**
     * 获取策略名称（用于日志和调试）
     */
    fun getStrategyName(playerType: String): String {
        return when (playerType.lowercase()) {
            PlayerType.Innie.id -> "InniePlayer"
            PlayerType.Outie.id -> "OutiePlayer"
            PlayerType.Web.id -> "WebPlayer"
            else -> "Unknown($playerType)"
        }
    }
}
