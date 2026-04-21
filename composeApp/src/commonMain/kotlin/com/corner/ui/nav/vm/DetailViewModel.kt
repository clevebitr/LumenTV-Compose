package com.corner.ui.nav.vm

import com.corner.catvodcore.viewmodel.SiteViewModel
import com.corner.ui.scene.SnackBar
import androidx.compose.runtime.*
import com.corner.util.settings.SettingStore
import com.corner.util.settings.SettingType
import com.corner.service.player.PlayerType
import com.corner.util.settings.getPlayerSetting
import com.corner.catvodcore.bean.Vod
import com.corner.catvodcore.bean.Vod.Companion.getPage
import com.corner.util.core.playResultIsEmpty
import com.corner.util.core.detailIsEmpty
import com.corner.util.core.buildUpdatedDetail
import com.corner.util.core.updateFlagActivationStates
import com.corner.catvodcore.bean.*
import com.corner.catvodcore.config.ApiConfig
import com.corner.util.net.Utils
import com.corner.catvodcore.viewmodel.DetailFromPage
import com.corner.catvodcore.viewmodel.GlobalAppState
import com.corner.catvodcore.viewmodel.GlobalAppState.hideProgress
import com.corner.catvodcore.viewmodel.GlobalAppState.showProgress
import com.corner.service.history.HistoryService
import com.corner.service.di.ServiceModule
import com.corner.service.episode.EpisodeManager
import com.corner.service.player.PlayerStrategyFactory
import com.corner.service.player.PlayerStrategyConfig
import com.corner.database.entity.History
import com.corner.ui.nav.BaseViewModel
import com.corner.ui.nav.data.DetailScreenState
import com.corner.ui.onUserSelectEpisode
import com.corner.ui.player.PlayState
import com.corner.ui.player.PlayerLifecycleManager
import com.corner.ui.player.PlayerLifecycleState.*
import com.corner.ui.player.vlcj.VlcJInit
import com.corner.ui.player.vlcj.VlcjFrameController
import com.corner.util.core.Constants
import com.corner.util.core.isEmpty
import com.corner.util.play.BrowserUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import org.apache.commons.lang3.StringUtils
import java.util.concurrent.CopyOnWriteArrayList


class DetailViewModel : BaseViewModel() {
    // ==================== 状态管理 ====================
    private val _state = MutableStateFlow(DetailScreenState())
    val state: StateFlow<DetailScreenState> = _state

    // ==================== 协程作用域 ====================
    private var supervisor = SupervisorJob()
    private val searchScope = CoroutineScope(Dispatchers.Default + supervisor)

    /**
     * 用于资源清理的独立协程作用域
     * 不受ViewModel生命周期影响，确保清理操作能完整执行
     */
    private val cleanupJob = SupervisorJob()
    private val cleanupScope = CoroutineScope(Dispatchers.IO + cleanupJob)

    // ==================== 播放器相关 ====================
    val controller: VlcjFrameController = VlcjFrameController(this).apply { VlcJInit.setController(this) }
    val lifecycleManager: PlayerLifecycleManager = PlayerLifecycleManager(controller)
    var controllerHistory: History? = null
    val vmPlayerType = SettingStore.getSettingItem(SettingType.PLAYER.id)
        .getPlayerSetting(_state.value.detail.site?.playerType)

    // ==================== Service层 ====================
    private val historyService: HistoryService = ServiceModule.provideHistoryService { controller }
    private val episodeManager: EpisodeManager = ServiceModule.provideEpisodeManager()

    // ==================== 业务状态 ====================
    @Volatile
    private var launched = false
    private var currentSiteKey = MutableStateFlow("")
    private val jobList = mutableListOf<Job>()
    private var fromSearchLoadJob: Job = Job()
    var currentSelectedEpNumber by mutableStateOf(1)
    val currentEpisodeIndex: Int get() = currentSelectedEpNumber

    private val _currentFlagName = MutableStateFlow("")
    val currentFlagName: StateFlow<String> = _currentFlagName

    private val nextEpisodeLock = Object()
    private val playerStateLock = Mutex()
    private var consecutiveLoadFailures = 0
    private val maxConsecutiveFailures = 3
    var isDownloadUrl = MutableStateFlow<Boolean>(false)
    private val lock = Any()

    val isLastEpisode: Boolean
        get() {
            val detail = _state.value.detail
            val totalEpisodes = detail.currentFlag.episodes.size
            val currentEp = detail.currentFlag.episodes.find { it.activated }
            if (currentEp != null) {
                val currentIndex = detail.currentFlag.episodes.indexOf(currentEp)
                return currentIndex >= totalEpisodes - 1
            }
            return false
        }

    init {
        BrowserUtils.initialize(this)
        setupPlayerStateObserver()
    }

    /**
     * 设置播放器状态观察者
     */
    private fun setupPlayerStateObserver() {
        scope.launch {
            controller.state.collect { playerState ->
                handlePlayerStateChange(playerState)
            }
        }
    }

    /**
     * 处理播放器状态变化
     */
    private fun handlePlayerStateChange(playerState: com.corner.ui.player.PlayerState) {
        when (playerState.state) {
            PlayState.ERROR -> handlePlayError()
            PlayState.BUFFERING -> _state.update { it.copy(isBuffering = true) }
            else -> _state.update { it.copy(isBuffering = false) }
        }
    }

    /**
     * 处理播放错误
     */
    private fun handlePlayError() {
        log.error("播放错误")
        scope.launch {
            when (lifecycleManager.lifecycleState.value) {
                Playing -> {
                    lifecycleManager.stop()
                    lifecycleManager.ended()
                }

                Loading, Ready, Paused, Ended, Error -> {
                    lifecycleManager.ended()
                }

                Initialized -> {
                    // Initialized 状态需要先经过 Loading 才能到 Ended
                    lifecycleManager.loading()
                    lifecycleManager.ended()
                }

                else -> {
                    // 其他未知状态，尝试 ended
                    lifecycleManager.ended()
                }
            }
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 统一错误处理
     */
    private fun handleError(message: String, e: Exception? = null) {
        if (e != null) {
            log.error(message, e)
        } else {
            log.error(message)
        }
        SnackBar.postMsg(message, type = SnackBar.MessageType.ERROR)
    }

    /**
     * 安全执行异步操作
     */
    private suspend fun <T> safeExecute(
        errorMessage: String,
        block: suspend () -> T
    ): T? {
        return try {
            block()
        } catch (e: Exception) {
            handleError(errorMessage, e)
            null
        }
    }

    // ==================== 生命周期管理 ====================

    /**
     * ViewModel销毁时调用，清理所有资源
     */
    override fun onCleared() {
        super.onCleared()
        log.debug("DetailViewModel onCleared - 开始清理")
        supervisor.cancel()
        log.debug("DetailViewModel onCleared - 清理完成")
    }

    // ==================== 页面加载流程 ====================


    /**
     * 加载详情页并根据不同来源执行相应操作
     */
    suspend fun load() {
        if (vmPlayerType.first() == PlayerType.Innie.id) {
            lifecycleManager.initializeSync()
        }
        val chooseVod = loadChooseVod()

        try {
            _state.update { it.copy(isLoading = true) }
            SiteViewModel.viewModelScope.launch {
                if (GlobalAppState.detailFrom == DetailFromPage.SEARCH) {
                    loadFromSearch(chooseVod)
                } else {
                    loadFromNonSearch(chooseVod)
                }
            }.invokeOnCompletion { _state.update { it.copy(isLoading = false) } }
        } catch (e: Exception) {
            log.error("启动加载任务失败", e)
            _state.update { it.copy(isLoading = false) }
            SnackBar.postMsg("启动加载失败: ${e.message}", type = SnackBar.MessageType.ERROR)
        }
    }

    /**
     * 从搜索页加载
     */
    private suspend fun loadFromSearch(chooseVod: Vod) {
        val list = SiteViewModel.getSearchResultActive().list
        loadSearchResult(chooseVod, list)
    }

    /**
     * 从非搜索页加载详情
     */
    private suspend fun loadFromNonSearch(chooseVod: Vod) {
        val dt = fetchDetailContent(chooseVod)

        if (chooseVod.vodId.isBlank()) return
        if (dt == null || dt.detailIsEmpty()) {
            quickSearch()
        } else {
            loadVodDetail(dt)
            startPlay(_state.value.detail)
        }
    }

    /**
     * 获取详情内容
     */
    private suspend fun fetchDetailContent(chooseVod: Vod): Result? {
        return try {
            SiteViewModel.detailContent(chooseVod.site?.key ?: "", chooseVod.vodId)
        } catch (e: Exception) {
            log.error("加载详情失败: {}", e.message, e)
            SnackBar.postMsg("加载失败: ${e.message}", type = SnackBar.MessageType.ERROR)
            null
        }
    }

    // ==================== 历史记录管理 ====================

    /**
     * 更新历史记录信息
     */
    fun updateHistory(it: History) {
        scope.launch {
            historyService.updateHistory(it)
        }
    }

    /**
     * 从Controller触发的历史记录同步
     * 用于播放器重新加载后同步历史记录状态
     */
    internal fun syncHistoryFromController() {
        val detail = _state.value.detail
        if (detail.vodId.isNotBlank()) {
            scope.launch {
                try {
                    historyService.syncHistory(detail)
                } catch (e: Exception) {
                    log.error("同步历史记录失败", e)
                }
            }
        }
    }

    // ==================== 数据加载辅助方法 ====================

    /**
     * 获取视频信息，并更新当前站点key
     */
    private fun loadChooseVod(): Vod {
        val chooseVod = getChooseVod()
        _state.update { it.copy(detail = chooseVod) }
        currentSiteKey.value = chooseVod.site?.key ?: ""
        return chooseVod
    }

    private fun getChooseVod(): Vod = GlobalAppState.chooseVod.value

    /**
     * 加载搜索详情页信息
     */
    private fun loadSearchResult(chooseVod: Vod, list: MutableList<Vod>) {
        _state.update {
            it.copy(
                detail = chooseVod,
                quickSearchResult = CopyOnWriteArrayList(list)
            )
        }
        fromSearchLoadJob = SiteViewModel.viewModelScope.launch {
            if (_state.value.quickSearchResult.isNotEmpty()) {
                _state.value.detail.let { loadDetail(it) }
            }
        }
    }

    /**
     * 加载详情页信息
     */
    private fun loadVodDetail(dt: Result) {
        var detail = dt.list[0]
        detail = detail.copy(subEpisode = detail.currentFlag.episodes.getPage(detail.currentTabIndex))

        if (StringUtils.isNotBlank(getChooseVod().vodRemarks)) {
            for (it: Episode in detail.subEpisode) {
                if (it.name == getChooseVod().vodRemarks) {
                    it.activated = true
                    break
                }
            }
        }
        detail.site = getChooseVod().site
        _state.update { it.copy(detail = detail) }
        _currentFlagName.value = detail.currentFlag.flag.toString()
    }

    // ==================== 快速搜索功能 ====================

    /**
     * 执行快速搜索操作，从可切换的站点中搜索视频信息
     * @param onComplete 搜索完成后的回调函数
     */
    fun quickSearch(onComplete: ((List<Vod>) -> Unit)? = null) {
        resetQuickSearchState()

        searchScope.launch {
            _state.update { it.copy(isLoading = true, isBuffering = false) }
            val quickSearchSites = ApiConfig.api.sites.filter { it.changeable == 1 }.shuffled()
            val totalSites = quickSearchSites.size

            if (totalSites == 0) {
                handleNoSearchSites(onComplete)
                return@launch
            }

            log.debug("开始执行快搜 sites:{}", quickSearchSites.map { it.name })
            postQuickSearchProgress(0, totalSites)

            executeSearchTasks(quickSearchSites, totalSites, onComplete)
        }.invokeOnCompletion {
            _state.update { it.copy(isLoading = false) }
            onComplete?.invoke(_state.value.quickSearchResult)
        }
    }

    private fun resetQuickSearchState() {
        launched = false
        consecutiveLoadFailures = 0
        jobList.clear()
    }

    private suspend fun handleNoSearchSites(onComplete: ((List<Vod>) -> Unit)?) {
        log.warn("没有可用的搜索站点")
        _state.update { it.copy(isLoading = false) }
        SnackBar.postMsg("暂无可用站点", type = SnackBar.MessageType.WARNING)
        onComplete?.invoke(emptyList())
    }

    private suspend fun executeSearchTasks(
        quickSearchSites: List<com.corner.catvodcore.bean.Site>,
        totalSites: Int,
        onComplete: ((List<Vod>) -> Unit)?
    ) {
        val semaphore = Semaphore(2)
        val completedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val hasLoadedDetail = java.util.concurrent.atomic.AtomicBoolean(false)

        quickSearchSites.forEach { site ->
            val job = launchSearchTask(site, semaphore, completedCount, totalSites, hasLoadedDetail)
            jobList.add(job)
        }

        try {
            jobList.joinAll()
        } catch (e: Exception) {
            log.error("等待搜索任务完成时发生异常: {}", e.message)
        } finally {
            jobList.clear()
        }

        handleSearchCompletion()
    }

    private fun launchSearchTask(
        site: com.corner.catvodcore.bean.Site,
        semaphore: Semaphore,
        completedCount: java.util.concurrent.atomic.AtomicInteger,
        totalSites: Int,
        hasLoadedDetail: java.util.concurrent.atomic.AtomicBoolean
    ): Job {
        val job = searchScope.launch {
            semaphore.acquire()
            try {
                withTimeout(2500L) {
                    SiteViewModel.searchContent(site, getChooseVod().vodName ?: "", true)
                    log.debug("{}完成搜索", site.name)
                }
            } catch (e: TimeoutCancellationException) {
                log.warn("搜索站点 {} 超时", site.name)
            } catch (e: Exception) {
                log.error("搜索站点 {} 时发生异常: {}", site.name, e.message)
            } finally {
                semaphore.release()
            }
        }

        job.invokeOnCompletion { throwable ->
            handleSearchTaskCompletion(
                throwable,
                completedCount,
                totalSites,
                hasLoadedDetail,
                searchScope
            )
        }

        return job
    }

    private fun handleSearchTaskCompletion(
        throwable: Throwable?,
        completedCount: java.util.concurrent.atomic.AtomicInteger,
        totalSites: Int,
        hasLoadedDetail: java.util.concurrent.atomic.AtomicBoolean,
        scope: CoroutineScope
    ) {
        val count = completedCount.incrementAndGet()
        if (throwable != null && throwable !is TimeoutCancellationException) {
            log.error("quickSearch 协程执行异常: {}", throwable.message)
        }

        val currentSiteName = ApiConfig.api.sites.filter { it.changeable == 1 }
            .getOrNull(count - 1)?.name ?: "完成"
        postQuickSearchProgress(count, totalSites, currentSiteName)

        updateSearchResults()

        if (shouldLoadDetail(throwable, hasLoadedDetail)) {
            synchronized(lock) {
                if (!_state.value.quickSearchResult.isEmpty() &&
                    _state.value.detail.isEmpty() &&
                    !launched
                ) {
                    scope.launch {
                        try {
                            log.info("开始加载详情")
                            launched = true
                            val firstResult = _state.value.quickSearchResult.firstOrNull()
                            if (firstResult != null) {
                                loadDetail(firstResult)
                            } else {
                                launched = false
                                hasLoadedDetail.set(false)
                            }
                        } catch (e: Exception) {
                            log.error("加载详情时发生异常: {}", e.message)
                            launched = false
                            hasLoadedDetail.set(false)
                        }
                    }
                }
            }
        }
    }

    private fun updateSearchResults() {
        try {
            val searchResults = SiteViewModel.quickSearch.value
            if (searchResults.isNotEmpty() && searchResults[0].list.isNotEmpty()) {
                _state.update { state ->
                    val existingUrls = state.quickSearchResult.map { it.vodId }.toSet()
                    val newVods = searchResults[0].list.filter { it.vodId !in existingUrls }
                    if (newVods.isNotEmpty()) {
                        val updatedList = CopyOnWriteArrayList(state.quickSearchResult)
                        updatedList.addAll(newVods)
                        state.copy(quickSearchResult = updatedList)
                    } else {
                        state
                    }
                }
            }
        } catch (e: Exception) {
            log.error("更新搜索结果时发生异常: {}", e.message)
        }
    }

    private fun shouldLoadDetail(
        throwable: Throwable?,
        hasLoadedDetail: java.util.concurrent.atomic.AtomicBoolean
    ): Boolean {
        return (throwable == null || throwable is TimeoutCancellationException) &&
                !_state.value.quickSearchResult.isEmpty() &&
                _state.value.detail.isEmpty() &&
                !launched &&
                hasLoadedDetail.compareAndSet(false, true)
    }

    private fun handleSearchCompletion() {
        if (_state.value.quickSearchResult.isEmpty() && _state.value.detail.isEmpty()) {
            _state.update {
                it.copy(
                    detail = GlobalAppState.chooseVod.value,
                    isLoading = false
                )
            }
            SnackBar.postMsg("暂无线路数据", type = SnackBar.MessageType.WARNING)
        }
    }


    // ==================== 详情加载与切换 ====================

    /**
     * 加载快速搜索出的视频的详细信息
     */
    suspend fun loadDetail(vod: Vod) {
        log.info("加载详情 <${vod.vodName}> <${vod.vodId}> site:<${vod.site}>")

        try {
            _state.update { it.copy(isLoading = true) }
            val siteKey = vod.site?.key ?: run {
                handleSiteEmpty()
                return
            }

            val dt = fetchDetailWithRetry(siteKey, vod.vodId)
            if (dt == null || dt.detailIsEmpty()) {
                handleDetailLoadFailure(vod)
                return
            }

            val first = dt.list.firstOrNull()
            if (first == null || first.isEmpty()) {
                handleEmptyDetail(vod)
                return
            }

            // 成功加载详情
            onDetailLoadSuccess(first, vod)
        } catch (e: Exception) {
            handleError("加载详情时发生未预期异常: ${e.message}", e)
            _state.update { it.copy(isLoading = false) }
        } finally {
            launched = false
        }
    }

    private fun handleSiteEmpty() {
        log.warn("站点为空")
        SnackBar.postMsg("站点为空", type = SnackBar.MessageType.WARNING)
        _state.update { it.copy(isLoading = false) }
    }

    private suspend fun fetchDetailWithRetry(siteKey: String, vodId: String): Result? {
        return safeExecute("获取视频详情信息时发生异常") {
            SiteViewModel.detailContent(siteKey, vodId)
        }
    }

    private fun handleDetailLoadFailure(vod: Vod) {
        log.info("请求详情为空，加载下一个站源数据")
        SnackBar.postMsg("请求详情为空，尝试下一个站源", type = SnackBar.MessageType.INFO)
        _state.update { it.copy(isLoading = false) }

        if (incrementAndCheckFailures()) {
            nextSite(vod)
        }
    }

    private fun handleEmptyDetail(vod: Vod) {
        log.warn("详情对象为空，尝试下一个站源")
        _state.update { it.copy(isLoading = false) }

        if (incrementAndCheckFailures()) {
            nextSite(vod)
        }
    }

    private fun incrementAndCheckFailures(): Boolean {
        consecutiveLoadFailures++
        if (consecutiveLoadFailures >= maxConsecutiveFailures) {
            log.warn("连续加载失败次数达到上限")
            SnackBar.postMsg("连续加载失败次数达到上限，取消加载", type = SnackBar.MessageType.WARNING)
            return false
        }
        return true
    }

    private fun onDetailLoadSuccess(first: Vod, vod: Vod) {
        consecutiveLoadFailures = 0
        first.site = vod.site
        setDetail(first)
        log.debug("切换站源，新的站源: {}", first.site?.name)
        _currentFlagName.value = first.currentFlag.flag.toString()

        // 取消剩余的搜索任务
        supervisor.cancelChildren()
        jobList.forEach { it.cancel("detail loaded") }
        jobList.clear()
    }

    /**
     * 尝试从快速搜索结果中加载下一个视频的详情
     */
    fun nextSite(lastVod: Vod?) {
        _state.update { it.copy(isLoading = true) }

        if (_state.value.quickSearchResult.isEmpty()) {
            log.warn("快搜结果为空,无法加载下一个视频")
            _state.update { it.copy(isLoading = false) }
            SnackBar.postMsg("暂无更多视频", type = SnackBar.MessageType.WARNING)
            return
        }

        val list = _state.value.quickSearchResult
        if (lastVod != null) {
            val remove = list.remove(lastVod)
            log.debug("remove last vod result:$remove")
        }

        _state.update { it.copy(quickSearchResult = list, isLoading = false) }

        if (_state.value.quickSearchResult.isNotEmpty()) {
            searchScope.launch {
                loadDetail(_state.value.quickSearchResult[0])
            }
        }
    }

    /**
     * 设置快速搜索出的视频详情信息并准备播放新视频
     */
    private fun setDetail(vod: Vod) {
        if (currentSiteKey.value != vod.site?.key) {
            SnackBar.postMsg("正在切换站源至 [${vod.site!!.name}]", type = SnackBar.MessageType.INFO)
        }

        updateDetailState(vod)
        setupDefaultEpisode(vod)

        scope.launch {
            log.info("开始播放视频: ${vod.vodName}")
            val effectiveEpisode = vod.vodFlags.first().episodes.firstOrNull() ?: Episode.create("", "")
            startPlay(vod, effectiveEpisode)
        }.invokeOnCompletion { _state.update { it.copy(isLoading = false) } }
    }

    private fun updateDetailState(vod: Vod) {
        _state.update {
            it.copy(
                detail = vod.copy(
                    subEpisode = vod.vodFlags.first().episodes.getPage(vod.currentTabIndex).toMutableList()
                ),
                isLoading = true
            )
        }
    }

    private fun setupDefaultEpisode(vod: Vod) {
        val firstEpisode = vod.vodFlags.first().episodes.firstOrNull()
        if (firstEpisode != null) {
            updateEpisodeActivation(firstEpisode)
        } else {
            _state.update { it.copy(currentEp = null) }
        }
    }

    // ==================== UI辅助方法 ====================

    /**
     * 发布快速搜索进度消息
     */
    fun postQuickSearchProgress(current: Int, total: Int, currentSite: String = "") {
        val message = if (currentSite.isNotEmpty()) {
            "搜索进度: $current/$total - $currentSite"
        } else {
            "搜索进度: $current/$total"
        }
        SnackBar.postMsg(message, priority = 1, type = SnackBar.MessageType.INFO, key = "quick_search_progress")
    }

    /**
     * 清理详情页相关资源和状态
     */
    fun clear(releaseController: Boolean = true, onComplete: () -> Unit = {}) {
        log.debug("----------开始清理详情页资源----------")

        cleanupScope.launch(Dispatchers.IO) {
            var progressJob: Job? = null

            try {
                progressJob = launch {
                    delay(2000L)
                    SnackBar.postMsg("播放器等资源清理异常缓慢，请耐心等待...", type = SnackBar.MessageType.WARNING)
                    showProgress()
                }

                performCleanup(releaseController)
                resetStateAndResources()

                withContext(Dispatchers.Swing) {
                    onComplete()
                }
            } catch (e: Exception) {
                log.error("----------清理过程中出错----------", e)
            } finally {
                progressJob?.cancel()
                hideProgress()
            }
        }
    }

    private suspend fun performCleanup(releaseController: Boolean) {
        log.debug("<清理资源>当前播放器类型:${vmPlayerType.first()}，手动放弃清理播放器资源:{${!releaseController}}")

        if (releaseController && vmPlayerType.first() == PlayerType.Innie.id) {
            cleanupPlayerLifecycle()
        }
    }

    private suspend fun cleanupPlayerLifecycle() {
        try {
            lifecycleManager.cleanup()
                .onSuccess {
                    lifecycleManager.release()
                        .onSuccess { log.debug("生命周期释放完成") }
                        .onFailure { e -> log.error("生命周期释放失败", e) }
                }
                .onFailure { e -> log.error("生命周期清理失败", e) }
        } catch (e: Exception) {
            log.error("清理播放器时发生异常", e)
        }
    }

    private fun resetStateAndResources() {
        jobList.forEach {
            try {
                it.cancel("detail clear")
            } catch (e: Exception) {
                log.warn("取消协程任务时出错", e)
            }
        }
        jobList.clear()

        _state.update { it.copy() }
        SiteViewModel.clearQuickSearch()
        launched = false

        BrowserUtils.cleanup()
        BrowserUtils.detailViewModel = null

        log.debug("----------清理详情页资源完成----------")
    }

    // ==================== 播放控制 - 内部播放器 ====================

    /**
     * 内部播放器播放入口
     */
    fun inniePlay(result: Result?) {
        if (result == null || result.playResultIsEmpty()) {
            SnackBar.postMsg("加载内容失败，尝试切换线路", type = SnackBar.MessageType.WARNING)
            nextFlag()
            return
        }

        scope.launch {
            try {
                updatePlaybackState(result)
                prepareForPlayback(result)
            } catch (e: Exception) {
                handleError("播放器初始化失败: ${e.message}", e)
                _state.update { it.copy() }
            }
        }
    }

    private fun updatePlaybackState(result: Result) {
        _state.update {
            it.copy(
                currentPlayUrl = result.url.v(),
                playResult = result,
                isDLNA = false
            )
        }
    }

    /**
     * 准备播放：确保播放器处于Ready状态
     */
    private suspend fun prepareForPlayback(result: Result) {
        log.debug("当前状态:{}", lifecycleManager.lifecycleState.value)

        val success = when (lifecycleManager.lifecycleState.value) {
            Ready -> {
                playInitPlayer(result)
                return
            }

            Playing -> transitionFromPlayingToReady()
            Loading, Ended -> lifecycleManager.ready().isSuccess
            Error -> recoverFromErrorState()
            else -> transitionFromOtherStatesToReady()
        }

        if (success) {
            playInitPlayer(result)
        } else {
            handleError("播放器状态错误，无法准备播放")
        }
    }

    private suspend fun transitionFromPlayingToReady(): Boolean {
        log.debug("当前状态为playing，需要状态转换")
        return lifecycleManager.stop().isSuccess &&
                lifecycleManager.ended().isSuccess &&
                lifecycleManager.ready().isSuccess
    }

    private suspend fun recoverFromErrorState(): Boolean {
        return try {
            val cleanupSuccess = lifecycleManager.cleanup().isSuccess
            if (!cleanupSuccess) {
                log.warn("清理资源失败")
            }

            val initSuccess = lifecycleManager.initializeSync().isSuccess
            if (!initSuccess) {
                handleError("重新初始化失败")
                return false
            }

            val loadingSuccess = lifecycleManager.loading().isSuccess
            if (!loadingSuccess) {
                handleError("播放器加载失败")
                return false
            }

            val readySuccess = lifecycleManager.ready().isSuccess
            if (!readySuccess) {
                handleError("播放器准备就绪失败")
                return false
            }

            log.debug("错误状态恢复成功")
            true
        } catch (e: Exception) {
            handleError("错误状态恢复过程中发生异常: ${e.message}", e)
            false
        }
    }

    private suspend fun transitionFromOtherStatesToReady(): Boolean {
        val currentState = lifecycleManager.lifecycleState.value
        log.debug("当前状态:{}，转换到ready状态", currentState)

        return when (currentState) {
            Initialized -> {
                lifecycleManager.loading().isSuccess &&
                        lifecycleManager.ready().isSuccess
            }

            else -> {
                lifecycleManager.ended().isSuccess &&
                        lifecycleManager.ready().isSuccess
            }
        }
    }

    /**
     * 初始化播放器并加载视频
     */
    private suspend fun playInitPlayer(result: Result) {
        _state.update { it.copy(isLoading = false, isBuffering = false) }

        if (!validatePlayerState()) return

        if (!loadVideoUrl(result)) return

        startPlaybackWithTimeout()
    }

    private fun validatePlayerState(): Boolean {
        if (lifecycleManager.lifecycleState.value != Ready) {
            log.error("播放器状态不正确: {},播放器检查失败！", lifecycleManager.lifecycleState.value)
            return false
        }
        return true
    }

    private fun loadVideoUrl(result: Result): Boolean {
        return try {
            controller.load(result.url.v())
            true
        } catch (e: Exception) {
            handleError("加载播放链接失败: ${e.message}", e)
            false
        }
    }

    private suspend fun startPlaybackWithTimeout() {
        try {
            withTimeout(PlayerStrategyConfig.DETAIL_PLAYBACK_LOAD_TIMEOUT_MS) {
                log.info("播放器加载完成，开始转换状态")
                transitionToPlayingState()
            }
        } catch (e: TimeoutCancellationException) {
            handleError("播放器加载超时")
            lifecycleManager.ended()
        } catch (e: Exception) {
            handleError("播放器准备就绪时发生错误: ${e.message}", e)
            lifecycleManager.ended()
        }
    }

    private suspend fun transitionToPlayingState() {
        lifecycleManager.transitionTo(Playing) {
            lifecycleManager.start()
                .onFailure {
                    handleError("播放器状态转换 Playing 失败: ${it.message}")
                }
        }.onFailure { e ->
            handleError("播放器就绪失败: ${e.message}")
        }
    }

    /**
     * 播放指定视频的指定剧集
     */
    private fun playEp(detail: Vod, ep: Episode) {
        _state.update { it.copy(isBuffering = true) }
        onUserSelectEpisode()

        val result = fetchPlayResult(detail, ep)
        currentSelectedEpNumber = ep.number

        if (handleSpecialLink(ep, result)) return

        if (result == null || result.playResultIsEmpty()) {
            handleEmptyPlayResult()
            return
        }

        updatePlayState(result, ep)
        executePlaybackByType(result, ep)
    }

    private fun fetchPlayResult(detail: Vod, ep: Episode): Result? {
        return SiteViewModel.playerContent(
            detail.site?.key ?: "",
            detail.currentFlag.flag ?: "",
            ep.url
        )
    }

    private fun handleSpecialLink(ep: Episode, result: Result?): Boolean {
        if (isSpecialVideoLink(ep)) {
            _state.update { it.copy(isBuffering = false) }
            return true
        }
        return false
    }

    private fun handleEmptyPlayResult() {
        _state.update { it.copy(isBuffering = false) }
        log.warn("播放结果为空,无法播放")
        nextFlag()
    }

    private fun updatePlayState(result: Result, ep: Episode) {
        _state.update { it.copy(currentUrl = result.url) }

        controller.doWithHistory { history ->
            history.copy(
                episodeUrl = ep.url,
                vodRemarks = ep.name,
                position = history.position ?: 0L
            )
        }

        updateEpisodeActivation(ep)
    }

    /**
     * 根据播放器类型执行播放（使用策略模式）
     */
    private fun executePlaybackByType(result: Result, ep: Episode) {
        // 创建策略并执行播放
        val strategy = PlayerStrategyFactory.createStrategy(
            playerType = vmPlayerType.first(),
            controller = controller,
            lifecycleManager = lifecycleManager,
            viewModelScope = scope
        )

        log.debug("使用播放器策略: {}", strategy.getStrategyName())

        scope.launch {
            try {
                strategy.play(
                    result = result,
                    episode = ep,
                    onPlayStarted = {
                        _state.update { it.copy(isBuffering = false) }
                        SnackBar.postMsg("即将播放: ${ep.name}", type = SnackBar.MessageType.INFO)
                    },
                    onError = { error ->
                        handleError(error)
                        _state.update { it.copy(isBuffering = false) }
                    }
                )
            } catch (e: Exception) {
                handleError("播放执行失败: ${e.message}", e)
                _state.update { it.copy(isBuffering = false) }
            }
        }
    }

    /**
     * 检测特殊视频链接（下载链接或特殊链接）
     */
    private fun isSpecialVideoLink(ep: Episode): Boolean {
        if (Utils.isDownloadLink(ep.url)) {
            isDownloadUrl.value = true
            log.info("检测到磁力链接，将通过对话框提示用户选择，url:{}", ep.url)
            return true  // 磁力链接是特殊链接，需要弹出对话框
        }

        val isSpecialLink = SiteViewModel.state.value.isSpecialVideoLink
        if (isSpecialLink) {
            log.debug("检测到特殊链接，驳回播放请求")
            updateEpisodeActivation(ep)
            scope.launch {
                historyService.updateCurrentEpisode(ep, _state.value.detail)
            }
            return true
        }

        return false
    }

    /**
     * 启动视频播放流程
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun startPlay(dt: Vod, ep: Episode = Episode.create("", "")) {
        if (dt.isEmpty()) {
            handleError("视频详情为空")
            return
        }

        scope.launch {
            _state.update { it.copy(isLoading = true) }
            val effectiveEp = resolveEffectiveEpisode(ep, dt)

            if (effectiveEp != null) {
                log.debug("开始播放视频:{}", effectiveEp.name)
                _state.update { it.copy(isLoading = false) }
                playEp(dt, effectiveEp)
            }
        }
    }

    private suspend fun resolveEffectiveEpisode(ep: Episode, dt: Vod): Episode? {
        return if (ep.url.isNotBlank()) {
            ep
        } else {
            val findEp = historyService.handlePlaybackHistory(dt, currentEpisodeIndex)
            findEp ?: dt.subEpisode.firstOrNull()
        }
    }

    /**
     * 返回下一集的链接，并更新详情页状态。
     * 1. 查找当前激活的剧集，计算下一集的索引。
     * 2. 如果当前分组已播完最后一集，自动切换到下一分组。
     * 3. 如果没有更多剧集，返回 null。
     */
    fun getNextEpisodeUrl(): String? {
        synchronized(nextEpisodeLock) {
            val currentDetail = _state.value.detail
            val currentEp = currentDetail.subEpisode.find { it.activated }

            // 使用EpisodeManager获取下一集URL
            val nextEpUrl = episodeManager.getNextEpisodeUrl(currentDetail, currentEp)
                ?: return null

            // 如果没有激活的剧集，从当前分组的第一个开始
            if (currentEp == null) {
                val firstEp = currentDetail.subEpisode.firstOrNull() ?: return null
                log.debug("没有激活的剧集，从当前分组的第一个开始更新ui激活剧集{}", firstEp.name)
                updateEpisodeActivation(firstEp)
                updateEpisodeUI(firstEp)
                return decryptUrl(firstEp.url)
            }

            val currentIndex = currentDetail.subEpisode.indexOf(currentEp)
            val nextIndex = currentIndex + 1

            // 处理分组切换
            if (nextIndex >= currentDetail.subEpisode.size) {
                val nextTabIndex = currentDetail.currentTabIndex + 1
                val totalEpisodes = currentDetail.currentFlag.episodes.size
                val totalPages = (totalEpisodes + Constants.EP_SIZE - 1) / Constants.EP_SIZE

                if (nextTabIndex >= totalPages) {
                    return null
                }

                val start = nextTabIndex * Constants.EP_SIZE
                val end = minOf(start + Constants.EP_SIZE, totalEpisodes)
                val newSubEpisodes = currentDetail.currentFlag.episodes.subList(start, end)
                val newFirstEp = newSubEpisodes.firstOrNull() ?: return null

                updateEpisodeActivation(newFirstEp, nextTabIndex, newSubEpisodes)
                updateEpisodeUI(newFirstEp)
                return decryptUrl(newFirstEp.url)
            }

            // 正常切换到下一集
            val nextEp = currentDetail.subEpisode[nextIndex]
            log.debug("切换下一集更新ui激活剧集{}", nextEp.name)
            updateEpisodeActivation(nextEp)
            scope.launch {
                historyService.updateCurrentEpisode(nextEp, currentDetail)
            }
            updateEpisodeUI(nextEp)
            return decryptUrl(nextEp.url)
        }
    }

    /**更新选集ui*/
    private fun updateEpisodeUI(episode: Episode) {
        _state.update { state ->
            val updatedSubEpisodes = state.detail.subEpisode.map { ep ->
                ep.copy(activated = ep == episode)
            }.toMutableList()

            state.copy(
                detail = state.detail.copy(subEpisode = updatedSubEpisodes),
                currentEp = episode
            )
        }
    }


    /**
     * 更新剧集中激活状态和当前选中的剧集信息。
     */
    private fun updateEpisodeActivation(
        activeEp: Episode,
        newTabIndex: Int? = null,
        newSubEpisodes: List<Episode>? = null
    ) {
        currentSelectedEpNumber = activeEp.number

        _state.update { state ->
            clearAllEpisodesActivation(state)
            activateTargetEpisode(activeEp)

            val updatedSubEpisodes = buildUpdatedSubEpisodes(state, activeEp, newSubEpisodes)

            state.copy(
                detail = state.detail.copy(
                    currentTabIndex = newTabIndex ?: state.detail.currentTabIndex,
                    subEpisode = updatedSubEpisodes
                ),
                currentEp = activeEp,
            )
        }
    }

    private fun clearAllEpisodesActivation(state: DetailScreenState) {
        state.detail.currentFlag.episodes.forEach { episode ->
            episode.activated = false
        }
    }

    private fun activateTargetEpisode(activeEp: Episode) {
        activeEp.activated = true
    }

    private fun buildUpdatedSubEpisodes(
        state: DetailScreenState,
        activeEp: Episode,
        newSubEpisodes: List<Episode>?
    ): MutableList<Episode> {
        return if (newSubEpisodes != null) {
            newSubEpisodes.map { ep -> ep.copy(activated = ep.url == activeEp.url) }.toMutableList()
        } else {
            state.detail.subEpisode.map { ep -> ep.copy(activated = ep.url == activeEp.url) }.toMutableList()
        }
    }


    /**
     * 解密URL获取播放链接
     */
    private fun decryptUrl(url: String): String? {
        return SiteViewModel.playerContent(
            _state.value.detail.site?.key ?: "",
            _state.value.detail.currentFlag.flag ?: "",
            url
        )?.url?.v()
    }

    /**
     * 尝试播放下一集视频
     */
    fun nextEP() {
        log.info("加载下一集")
        val detail = _state.value.detail
        val currentEp = detail.subEpisode.find { it.activated }

        if (currentEp == null) {
            log.debug("当前没有激活的剧集")
            SnackBar.postMsg("当前没有激活的剧集", type = SnackBar.MessageType.WARNING)
            return
        }

        controller.doWithHistory { it.copy(position = 0) }
        currentSelectedEpNumber = currentEp.number

        // 使用EpisodeManager处理下一集逻辑
        episodeManager.nextEpisode(detail, currentEp) { updatedDetail, nextEp ->
            currentSelectedEpNumber = nextEp.number
            startPlay(updatedDetail, nextEp)
        }

        // 检查是否还有更多剧集
        val currentIndex = detail.subEpisode.indexOf(currentEp)
        val nextIndex = currentIndex + 1
        val totalEpisodes = detail.currentFlag.episodes.size

        if (totalEpisodes <= nextIndex) {
            SnackBar.postMsg("没有更多剧集", type = SnackBar.MessageType.INFO)
            return
        }
    }

    /**
     * 尝试切换到下一个视频播放线路
     */
    fun nextFlag() {
        _state.update { it.copy(isLoading = true, isBuffering = false) }
        log.info("nextFlag")

        if (!validateFlagSwitch()) return

        val detail = _state.value.detail.copy()
        val nextFlag = _state.value.detail.nextFlag()

        if (nextFlag == null) {
            handleNoMoreFlags(detail)
            return
        }

        detail.currentFlag = nextFlag
        _currentFlagName.value = nextFlag.flag.toString()

        if (detail.currentFlag.isEmpty()) {
            handleEmptyFlagWithQuickSearch(detail)
            return
        }

        completeFlagSwitch(detail)
    }

    private fun validateFlagSwitch(): Boolean {
        if (_state.value.detail.vodFlags.size > 1) {
            SnackBar.postMsg("加载数据失败，尝试切换线路", type = SnackBar.MessageType.WARNING)
        } else {
            _state.update { it.copy(isLoading = false, isBuffering = false) }
            SnackBar.postMsg("加载数据失败", type = SnackBar.MessageType.ERROR)
            return false
        }
        return true
    }

    private fun handleNoMoreFlags(detail: Vod) {
        log.info("没有更多线路可切换，当前线路数: {}", _state.value.detail.vodFlags.size)
        SnackBar.postMsg("没有更多线路", type = SnackBar.MessageType.WARNING)
        _state.update { it.copy(detail = it.detail.copy(), isLoading = false, isBuffering = false) }
    }

    private fun handleEmptyFlagWithQuickSearch(detail: Vod) {
        log.info("当前线路为空，需要执行快速搜索寻找可用站源")
        detail.vodId = ""

        quickSearch { results ->
            scope.launch {
                handleQuickSearchResults(results, detail)
            }
        }
    }

    private suspend fun handleQuickSearchResults(results: List<Vod>, detail: Vod) {
        if (results.isNotEmpty() && !results.all { it.vodId.isBlank() }) {
            log.info("快速搜索完成，找到 {} 个结果，准备加载详情", results.size)
            SnackBar.postMsg(
                "找到 ${results.size} 个可用站源，正在加载...",
                type = SnackBar.MessageType.INFO
            )
            loadDetail(results.first())
        } else {
            log.warn("快速搜索完成但未找到有效结果，取消自动换源")
            _state.update {
                it.copy(
                    detail = detail,
                    isLoading = false,
                    isBuffering = false
                )
            }
            SnackBar.postMsg("未找到可用站源，自动换源已取消", type = SnackBar.MessageType.WARNING)
        }
    }

    private fun completeFlagSwitch(detail: Vod) {
        detail.subEpisode = detail.currentFlag.episodes.getPage(_state.value.detail.currentTabIndex).toMutableList()
        controller.doWithHistory { it.copy(vodFlag = detail.currentFlag.flag) }
        GlobalAppState.chooseVod.value = detail.copy()

        _state.update { it.copy(detail = detail, isLoading = false, isBuffering = false) }
        SnackBar.postMsg("切换至线路[${detail.currentFlag.flag}]", type = SnackBar.MessageType.INFO)

        scope.launch {
            delay(500)
            playAfterFlagSwitch(detail)
        }
    }

    private suspend fun playAfterFlagSwitch(detail: Vod) {
        val history = controller.history.value
        val findEp = if (history != null) {
            detail.findAndSetEpByName(history, currentEpisodeIndex)
        } else {
            log.warn("自动切换线路时历史记录为空，使用第一个剧集")
            null
        }

        val episodeToPlay = findEp ?: detail.subEpisode.firstOrNull()
        if (episodeToPlay != null) {
            startPlay(detail, episodeToPlay)
        } else {
            log.error("切换线路后无可用剧集")
            SnackBar.postMsg("切换线路失败：无可用剧集", type = SnackBar.MessageType.ERROR)
        }
    }


    /**
     * 切换剧集选择对话框的显示状态
     */
    fun clickShowEp() {
        _state.update {
            it.copy(
                showEpChooserDialog = !_state.value.showEpChooserDialog,
                isLoading = false,
                isBuffering = false
            )
        }
    }


    /**
     * 切换视频播放线路
     */
    fun chooseFlag(detail: Vod, selectedFlag: Flag) {
        scope.launch {
            val oldNumber = currentSelectedEpNumber
            val newEpisodes = selectedFlag.episodes
            val newEp = findEpisodeByNumber(newEpisodes, oldNumber)

            currentSelectedEpNumber = newEp?.number ?: 1
            log.debug("chooseFlag -- 切换线路，新的线路标识: {}, 剧集编号{}", selectedFlag.flag, newEp?.number)

            _currentFlagName.value = selectedFlag.flag.toString()
            _state.update { it.copy(isLoading = true, isBuffering = false) }

            try {
                executeFlagSwitch(detail, selectedFlag, newEp)
            } catch (e: TimeoutCancellationException) {
                handleFlagSwitchTimeout(e)
            } catch (e: Exception) {
                handleFlagSwitchError(e)
            }
        }
    }

    private suspend fun executeFlagSwitch(detail: Vod, selectedFlag: Flag, newEp: Episode?) {
        val endedDeferred = launchEndedTaskWithTimeout(scope)

        withTimeout(7000) {
            detail.updateFlagActivationStates(selectedFlag)

            val updatedDetail = detail.buildUpdatedDetail(selectedFlag, newEp)

            controller.doWithHistory { it.copy(vodFlag = detail.currentFlag.flag) }

            endedDeferred.await()

            playEpisodeAfterFlagSwitch(updatedDetail, newEp)

            _state.update { model ->
                model.copy(
                    detail = updatedDetail,
                    isLoading = false,
                    isBuffering = false
                )
            }
        }
    }

    private fun findEpisodeByNumber(episodes: List<Episode>, number: Int): Episode? {
        return episodes.find { it.number == number } ?: episodes.firstOrNull()
    }

    private fun launchEndedTaskWithTimeout(scope: CoroutineScope): Deferred<Unit> {
        return scope.async<Unit> {
            try {
                withTimeout(PlayerStrategyConfig.DETAIL_HISTORY_QUERY_TIMEOUT_MS) {
                    lifecycleManager.ended()
                }
            } catch (e: TimeoutCancellationException) {
                SnackBar.postMsg("关闭媒体超时！请稍后切换线路重试...", type = SnackBar.MessageType.ERROR)
                log.error("关闭媒体超时,等待播放器缓存完成...")
                throw e
            }
        }
    }

    private suspend fun playEpisodeAfterFlagSwitch(updatedDetail: Vod, newEp: Episode?) {
        val history = controller.history.value

        if (history != null) {
            val findEp = updatedDetail.findAndSetEpByName(history, currentSelectedEpNumber)
            log.debug("切换线路，新的剧集数据: {}", findEp)

            if (findEp != null) {
                startPlay(updatedDetail, findEp)
                return
            }
        }

        // 如果没有历史记录或找不到对应剧集，使用传入的新剧集
        if (newEp != null) {
            startPlay(updatedDetail, newEp)
        }
    }

    private fun handleFlagSwitchTimeout(e: TimeoutCancellationException) {
        log.error("切换线路超时(7秒)，切换失败", e)
        SnackBar.postMsg("切换线路超时，切换失败！请等待播放器缓存完毕!", type = SnackBar.MessageType.ERROR)
        _state.update { it.copy(isLoading = false, isBuffering = false) }
    }

    private fun handleFlagSwitchError(e: Exception) {
        log.error("切换线路失败", e)
        SnackBar.postMsg("切换线路失败: ${e.message}", type = SnackBar.MessageType.ERROR)
        _state.update { it.copy(isLoading = false, isBuffering = false) }
    }


    /**
     * 清晰度选择
     */
    fun chooseLevel(url: Url?, playUrl: String?) {
        _state.update { it.copy(isLoading = true, isBuffering = false) }

        scope.launch {
            try {
                log.debug("切换清晰度,当前播放链接: {}", url)

                if (handleDownloadLinkCheck(url)) {
                    return@launch
                }

                if (playUrl != null) {
                    executePlaybackByPlayerType(playUrl)
                }

                updatePlayUrlState(url, playUrl)
            } catch (e: Exception) {
                log.error("切换清晰度时发生错误", e)
                SnackBar.postMsg("切换清晰度失败: ${e.message}", type = SnackBar.MessageType.ERROR)
            }
        }.invokeOnCompletion {
            _state.update { it.copy(isLoading = false, isBuffering = false) }
        }
    }

    private fun handleDownloadLinkCheck(url: Url?): Boolean {
        val isDownloadLink = Utils.isDownloadLink(url.toString())

        if (isDownloadLink) {
            log.warn("切换清晰度失败！当前播放链接是下载链接！")
            SnackBar.postMsg("切换清晰度失败！当前播放链接是下载链接！", type = SnackBar.MessageType.WARNING)
            return true
        }

        return false
    }

    /**
     * 根据播放器类型执行播放（用于清晰度切换）
     */
    private fun executePlaybackByPlayerType(playUrl: String) {
        val currentEp = _state.value.currentEp
        val episodeName = currentEp?.name ?: "未知剧集"

        // 创建临时Episode对象
        val tempEpisode = Episode.create(episodeName, playUrl)
        tempEpisode.number = currentEp?.number ?: -1

        // 创建策略并执行播放
        val strategy = PlayerStrategyFactory.createStrategy(
            playerType = vmPlayerType.first(),
            controller = controller,
            lifecycleManager = lifecycleManager,
            viewModelScope = scope
        )

        log.debug("使用播放器策略: {}", strategy.getStrategyName())

        // 创建临时Result对象
        val tempResult = Result().apply {
            url = com.corner.catvodcore.bean.Url().apply { add(playUrl) }
        }

        scope.launch {
            try {
                strategy.play(
                    result = tempResult,
                    episode = tempEpisode,
                    onPlayStarted = {
                        _state.update { it.copy(isBuffering = false) }
                    },
                    onError = { error ->
                        handleError(error)
                        _state.update { it.copy(isBuffering = false) }
                    }
                )
            } catch (e: Exception) {
                handleError("播放执行失败: ${e.message}", e)
                _state.update { it.copy(isBuffering = false) }
            }
        }
    }

    private fun updatePlayUrlState(url: Url?, playUrl: String?) {
        _state.update {
            it.copy(
                currentPlayUrl = playUrl ?: "",
                currentUrl = url,
            )
        }
    }


    /**
     * 隐藏剧集选择对话框
     */
    fun showEpChooser() {
        _state.update {
            it.copy(showEpChooserDialog = !it.showEpChooserDialog)
        }
    }

    /**
     * 根据传入的索引切换到对应的剧集分组
     */
    fun chooseEpBatch(index: Int) {
        val detail = state.value.detail
        val currentGlobalActiveEpisodeUrl = _state.value.currentEp?.url
        log.debug("批量选择剧集，当前全局激活剧集url: {}", currentGlobalActiveEpisodeUrl)

        // 使用EpisodeManager处理批量选择逻辑
        val updatedDetail = episodeManager.chooseEpisodeBatch(detail, index, currentGlobalActiveEpisodeUrl)

        _state.update { it.copy(detail = updatedDetail, isLoading = false, isBuffering = false) }
    }

    /**
     * 选择指定剧集进行播放操作
     */
    fun chooseEp(episode: Episode, openUri: (String) -> Unit) {
        log.debug("切换剧集: {}", episode)
        currentSelectedEpNumber = episode.number

        scope.launch {
            val currentDetail = _state.value.detail

            // 使用EpisodeManager处理剧集选择逻辑
            episodeManager.chooseEpisode(
                episode = episode,
                detail = currentDetail,
                playerTypeId = vmPlayerType.first(),
                lifecycleManager = lifecycleManager,
                onOpenUri = openUri,
                onPlayEpisode = { updatedDetail, selectedEp ->
                    updateCurrentEpisodeState(updatedDetail, selectedEp)
                    startPlay(_state.value.detail, selectedEp)
                }
            )
        }
    }

    private fun updateCurrentEpisodeState(updatedDetail: Vod, episode: Episode) {
        _state.update { model ->
            var newModel = model.copy(currentEp = episode, detail = updatedDetail)

            if (model.currentEp?.name != episode.name) {
                controller.doWithHistory { it.copy(position = 0L) }
            }

            controller.doWithHistory {
                it.copy(episodeUrl = episode.url, vodRemarks = episode.name)
            }

            newModel
        }
    }

    /**
     * 设置当前播放的 URL（用于DLNA）
     */
    fun setPlayUrl(string: String) {
        _state.update { it.copy(isLoading = true) }
        log.debug("<DLNA> 开始播放")

        // 如果已经在投屏中，先结束当前投屏
        if (_state.value.isDLNA) {
            log.debug("<DLNA> 检测到正在投屏，先结束当前投屏")
            endDLNASession()
        }

        // 创建临时Episode和Result对象用于策略模式
        val tempEpisode = Episode.create("LumenTV-DLNA", string)
        val tempResult = Result().apply {
            url = com.corner.catvodcore.bean.Url().apply { add(string) }
        }

        // 对于外部播放器和Web播放器，直接使用对应的启动方式
        when (vmPlayerType.first()) {
            PlayerType.Outie.id, PlayerType.Web.id -> {
                val strategy = PlayerStrategyFactory.createStrategy(
                    playerType = vmPlayerType.first(),
                )
                scope.launch {
                    strategy.play(
                        result = tempResult,
                        episode = tempEpisode,
                        onPlayStarted = {
                            _state.update {
                                it.copy(currentPlayUrl = string, isDLNA = true, isLoading = false)
                            }
                        },
                        onError = { error ->
                            handleError(error)
                            _state.update {
                                it.copy(isLoading = false)
                            }
                        }
                    )
                }
                return
            }
        }

        // 内部播放器：使用InniePlayerStrategy
        val strategy = PlayerStrategyFactory.createStrategy(
            playerType = PlayerType.Innie.id,
            controller = controller,
            lifecycleManager = lifecycleManager,
            viewModelScope = scope
        )

        log.debug("<DLNA> 使用播放器策略: {}", strategy.getStrategyName())

        scope.launch {
            try {
                strategy.play(
                    result = tempResult,
                    episode = tempEpisode,
                    onPlayStarted = {
                        _state.update {
                            it.copy(currentPlayUrl = string, isDLNA = true, isLoading = false)
                        }
                    },
                    onError = { error ->
                        handleError(error)
                        _state.update { it.copy(isLoading = false, isDLNA = false) }
                    }
                )
            } catch (e: Exception) {
                handleError("DLNA播放执行失败: ${e.message}", e)
                _state.update { it.copy(isLoading = false, isDLNA = false) }
            }
        }
    }

    /**
     * 结束DLNA投屏会话
     * 仅对内部播放器需要释放资源
     */
    fun endDLNASession() {
        if (vmPlayerType.first() == PlayerType.Innie.id) {
            log.debug("<DLNA> 结束投屏，释放内部播放器资源")
            scope.launch {
                try {
                    // 停止播放并释放资源
                    if (lifecycleManager.canTransitionTo(Ended)) {
                        lifecycleManager.ended()
                    }
                    // 重置投屏状态
                    _state.update { it.copy(isDLNA = false, currentPlayUrl = "") }
                } catch (e: Exception) {
                    log.error("<DLNA> 结束投屏失败", e)
                }
            }
        } else {
            // 外部播放器和Web播放器只需重置状态
            _state.update { it.copy(isDLNA = false, currentPlayUrl = "") }
        }
    }
}