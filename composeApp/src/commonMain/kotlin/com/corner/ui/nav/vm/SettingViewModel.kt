package com.corner.ui.nav.vm

import com.corner.bean.SettingStore
import com.corner.database.Db
import com.corner.database.entity.Config
import com.corner.ui.nav.BaseViewModel
import com.corner.ui.nav.data.SettingScreenState
import com.corner.ui.scene.SnackBar
import com.corner.util.update.UpdateManager
import com.corner.util.update.UpdateResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class SettingViewModel : BaseViewModel() {
    private val _state = MutableStateFlow(SettingScreenState())
    val state: StateFlow<SettingScreenState> = _state

    private val _updateCheckState = MutableStateFlow(UpdateCheckState())
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState

    fun sync() {
        _state.update { it.copy(settingList = SettingStore.getSettingList(), version = _state.value.version + 1) }
    }

    fun getConfigAll() {
        scope.launch {
            val flow = Db.Config.getAll().firstOrNull() ?: emptyList()
            _state.update { it.copy(dbConfigList = flow.toMutableList()) }
        }
    }

    fun deleteHistoryById(config: Config) {
        scope.launch {
            try {
                Db.Config.deleteById(config.id)
                _state.update { currentState ->
                    currentState.copy(
                        dbConfigList = currentState.dbConfigList
                            .filterNot { it.id == config.id }
                            .toMutableList() // 确保返回可变列表
                    )
                }
            } catch (e: Exception) {
                log.error("删除失败: ${e.message}")
            }
        }
    }

    fun checkForUpdate() {
        scope.launch {
            _updateCheckState.update { it.copy(isChecking = true, error = null) }
            try {
                when (val result = UpdateManager.checkForUpdate()) {
                    is UpdateResult.Available -> {
                        _updateCheckState.update {
                            it.copy(
                                isChecking = false,
                                hasUpdate = true,
                                latestVersion = result.latestVersion
                            )
                        }
                    }

                    UpdateResult.NoUpdate -> {
                        _updateCheckState.update {
                            it.copy(
                                isChecking = false,
                                hasUpdate = false,
                                latestVersion = null
                            )
                        }
                        scope.launch {
                            SnackBar.postMsg("已是最新版本", type = SnackBar.MessageType.INFO)
                        }
                    }

                    is UpdateResult.Error -> {
                        _updateCheckState.update {
                            it.copy(
                                isChecking = false,
                                hasUpdate = false,
                                latestVersion = null,
                                error = result.message
                            )
                        }
                        scope.launch {
                            SnackBar.postMsg("检查更新失败: ${result.message}", type = SnackBar.MessageType.ERROR)
                        }
                    }
                }
            } catch (e: Exception) {
                _updateCheckState.update {
                    it.copy(
                        isChecking = false,
                        hasUpdate = false,
                        latestVersion = null,
                        error = e.message
                    )
                }
                scope.launch {
                    SnackBar.postMsg("检查更新时发生错误: ${e.message}", type = SnackBar.MessageType.ERROR)
                }
            }
        }
    }
}

data class UpdateCheckState(
    val isChecking: Boolean = false,
    val hasUpdate: Boolean = false,
    val latestVersion: String? = null,
    val error: String? = null
)