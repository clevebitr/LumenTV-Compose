package com.corner.util.update

import com.corner.catvodcore.util.Paths
import com.corner.util.OperatingSystem
import com.corner.util.UserDataDirProvider
import com.corner.util.network.KtorClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class UpdateManager {
    companion object {
        private val log = LoggerFactory.getLogger(UpdateManager::class.java)
        private const val VERSION_URL = "https://github.com/clevebitr/LumenTV-Compose/releases/latest/download/version.json"
        private const val CURRENT_VERSION = "1.1.4"
        private const val NO_REMIND_FILE = "no_remind_update.txt"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        // 检查是否应该显示更新提示
        private fun shouldShowUpdate(latestVersion: String): Boolean {
            val noRemindFile = Paths.userDataRoot().resolve(NO_REMIND_FILE)
            if (!noRemindFile.exists()) return true

            val ignoredVersion = noRemindFile.readText()
            return ignoredVersion != latestVersion
        }

        // 记录用户选择不再提醒的版本
        fun setNoRemindForVersion(version: String) {
            try {
                val noRemindFile =Paths.userDataRoot().resolve(NO_REMIND_FILE)
                noRemindFile.writeText(version)
            } catch (e: Exception) {
                log.error("Failed to save no remind setting", e)
            }
        }

        suspend fun checkForUpdate(): UpdateResult {
            return withContext(Dispatchers.IO) {
                try {
                    val response = KtorClient.client.get(VERSION_URL)
                    val versionInfo = json.decodeFromString<VersionInfo>(response.body())

                    val hasUpdate = compareVersions(CURRENT_VERSION, versionInfo.version) < 0

                    log.debug("Current version: $CURRENT_VERSION, Latest version: ${versionInfo.version}")

                    if (hasUpdate) {
                        // 检查用户是否选择了不再提醒此版本
                        if (!shouldShowUpdate(versionInfo.version)) {
                            return@withContext UpdateResult.NoUpdate
                        }

                        val platformInfo = getPlatformInfo(versionInfo)
                        if (platformInfo != null) {
                            UpdateResult.Available(
                                versionInfo.version,
                                CURRENT_VERSION,
                                platformInfo.download_url,
                                versionInfo.updater_url
                            )
                        } else {
                            log.warn("No platform info found for current OS")
                            UpdateResult.NoUpdate
                        }
                    } else {
                        // 如果当前没有更新，清除之前存储的不再提醒版本
                        clearNoRemindSetting()
                        UpdateResult.NoUpdate
                    }
                } catch (e: Exception) {
                    log.error("Failed to check for updates {}", e.message)
                    UpdateResult.Error(e.message ?: "Unknown error")
                }
            }
        }

        private fun clearNoRemindSetting() {
            try {
                val noRemindFile = Paths.userDataRoot().resolve(NO_REMIND_FILE)
                if (noRemindFile.exists()) {
                    noRemindFile.delete()
                }
            } catch (e: Exception) {
                log.error("Failed to clear no remind setting", e)
            }
        }

        private fun getPlatformInfo(versionInfo: VersionInfo): PlatformInfo? {
            return when (UserDataDirProvider.currentOs) {
                OperatingSystem.Windows -> versionInfo.windows
                OperatingSystem.Linux -> versionInfo.linux
                OperatingSystem.MacOS -> versionInfo.mac
                OperatingSystem.Unknown -> null
            }
        }

        private fun compareVersions(current: String, latest: String): Int {
            // 清理版本字符串，移除可能的前缀
            val cleanCurrent = cleanVersion(current)
            val cleanLatest = cleanVersion(latest)

            val currentParts = cleanCurrent.split(".").map { it.toIntOrNull() ?: 0 }
            val latestParts = cleanLatest.split(".").map { it.toIntOrNull() ?: 0 }

            val maxLength = maxOf(currentParts.size, latestParts.size)

            for (i in 0 until maxLength) {
                val currentPart = currentParts.getOrElse(i) { 0 }
                val latestPart = latestParts.getOrElse(i) { 0 }

                if (currentPart != latestPart) {
                    return currentPart.compareTo(latestPart)
                }
            }

            return 0
        }

        private fun cleanVersion(version: String): String {
            // 移除常见的版本前缀，如 "v", "V", "ver" 等
            return version.trim().removePrefix("v").removePrefix("V").removePrefix("ver").trimStart('.')
        }
    }
}

sealed class UpdateResult {
    data class Available(
        val latestVersion: String,
        val currentVersion: String,
        val downloadUrl: String,
        val updaterUrl: String? = null
    ) : UpdateResult()

    object NoUpdate : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}
