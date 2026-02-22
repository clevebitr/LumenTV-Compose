package com.corner.util.update

import com.corner.catvodcore.util.Paths
import com.corner.util.OperatingSystem
import com.corner.util.UserDataDirProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class UpdateLauncher {
    companion object {
        private val log = LoggerFactory.getLogger(UpdateLauncher::class.java)

        suspend fun launchUpdater(zipFile: File, updaterUrl: String? = null): Boolean {
            return try {
                val userDataDir = Paths.userDataRoot()
                val updaterDir = userDataDir.resolve("updater")

                Files.createDirectories(updaterDir.toPath())

                val updaterFile = downloadUpdater(updaterDir.toPath(), updaterUrl)

                if (!updaterFile.exists()) {
                    log.error("Updater not found: $updaterFile")
                    return false
                }

                val currentDir = getCurrentDirectory()
                val tempDir = System.getProperty("java.io.tmpdir")
                val tempZipFile = File(tempDir, "LumenTV-update.zip")

                val processBuilder = ProcessBuilder()

                when (UserDataDirProvider.currentOs) {
                    OperatingSystem.Windows -> {
                        val tempDir = System.getProperty("java.io.tmpdir")
                        val batchFile = File(tempDir, "update_${System.currentTimeMillis()}.bat")

                        batchFile.writeText(
                            """
                            @echo off
                            echo Stopping main application...
                            taskkill /f /im "javaw.exe" /fi "WINDOWTITLE eq *LumenTV*" 2>nul
                            timeout /t 2 /nobreak >nul
                            echo Starting LumenTV Update...
                            "${updaterFile.absolutePath}" -path "${currentDir}" -file "${tempZipFile.absolutePath}"
                            if %ERRORLEVEL% EQU 0 (
                                echo Update completed successfully!
                            ) else (
                                echo Update failed!
                            )
                            echo Press any key to continue...
                            pause >nul
                            """.trimIndent()
                        )
                        processBuilder.command("cmd", "/c", "start", "cmd", "/k", "\"${batchFile.absolutePath}\"")
                    }

                    OperatingSystem.Linux -> {
                        processBuilder.command(
                            "gnome-terminal",
                            "--",
                            "sudo",
                            updaterFile.absolutePath,
                            "-path",
                            currentDir.toString(),
                            "-file",
                            tempZipFile.absolutePath
                        )
                    }

                    OperatingSystem.MacOS -> {
                        processBuilder.command(
                            "osascript",
                            "-e",
                            "tell application \"Terminal\" to do script \"sudo \\\"${updaterFile.absolutePath}\\\" -path \\\"${currentDir}\\\" -file \\\"${tempZipFile.absolutePath}\\\"\""
                        )
                    }

                    OperatingSystem.Unknown -> {
                        log.error("Unsupported operating system")
                        return false
                    }
                }

                processBuilder.redirectErrorStream(true)
                val process = processBuilder.start()

                log.info("Updater launched successfully")
                log.info("Updater: $updaterFile")
                log.info("Program path: $currentDir")
                log.info("Zip file: ${tempZipFile.absolutePath}")

                true
            } catch (e: Exception) {
                log.error("Failed to launch updater", e)
                false
            }
        }

        private suspend fun downloadUpdater(updaterDir: Path, updaterUrl: String?): File {
            val updaterName = PlatformDetector.getUpdaterFileName()
            val targetFile = updaterDir.resolve(updaterName).toFile()

            if (targetFile.exists()) {
                return targetFile
            }

            val currentDir = getCurrentDirectory()
            val localUpdaterFile = currentDir.resolve(updaterName).toFile()

            if (localUpdaterFile.exists()) {
                try {
                    Files.move(
                        localUpdaterFile.toPath(),
                        targetFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                    log.info("Updater moved from local to: $targetFile")
                    // 设置执行权限
                    setExecutePermission(targetFile)
                    return targetFile
                } catch (e: Exception) {
                    log.error("Failed to move updater from local directory", e)
                }
            }

            val url = updaterUrl ?: getUpdaterUrlFromActions()

            if (url != null) {
                try {
                    val client = HttpClient()
                    val response: HttpResponse = client.get(url)
                    val channel: ByteReadChannel = response.body()

                    // 直接写入二进制文件，不需要ZIP
                    withContext(Dispatchers.IO) {
                        val fileOutputStream = targetFile.outputStream()
                        try {
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (true) {
                                bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                                if (bytesRead <= 0) break
                                fileOutputStream.write(buffer, 0, bytesRead)
                            }
                        } finally {
                            fileOutputStream.close()
                        }
                    }

                    // 设置执行权限
                    setExecutePermission(targetFile)

                    client.close()
                    log.info("Updater downloaded to: $targetFile")
                } catch (e: Exception) {
                    log.error("Failed to download updater", e)
                }
            }

            return targetFile
        }

        // 设置执行权限
        private fun setExecutePermission(file: File) {
            try {
                when (UserDataDirProvider.currentOs) {
                    OperatingSystem.Linux, OperatingSystem.MacOS -> {
                        val process = ProcessBuilder("chmod", "+x", file.absolutePath).start()
                        process.waitFor()
                        if (process.exitValue() == 0) {
                            log.info("Execute permission set for: ${file.absolutePath}")
                        } else {
                            log.warn("Failed to set execute permission for: ${file.absolutePath}")
                        }
                    }
                    OperatingSystem.Windows -> {
                        // Windows不需要额外设置执行权限
                        log.info("Windows file ready: ${file.absolutePath}")
                    }
                    else -> {
                        log.warn("Unknown OS, skipping permission setting")
                    }
                }
            } catch (e: Exception) {
                log.error("Error setting execute permission", e)
            }
        }

        private suspend fun getLatestVersion(): String? {
            return try {
                val client = HttpClient()
                val response = client.get("https://api.github.com/repos/clevebitr/LumenTV-Compose/releases/latest")
                val json = Json.decodeFromString<JsonObject>(response.bodyAsText())
                val tagName = json["tag_name"]?.jsonPrimitive?.content
                client.close()
                tagName ?: "v1.1.3" // fallback版本
            } catch (e: Exception) {
                log.warn("Failed to fetch latest version, using fallback", e)
                "v1.1.3"
            }
        }

        private suspend fun getUpdaterUrlFromActions(): String? {
            val platformIdentifier = PlatformDetector.getPlatformIdentifier()

            // 动态获取最新版本
            val currentVersion = getLatestVersion()

            return when (platformIdentifier) {
                "macos-latest-amd64" ->
                    "https://github.com/clevebitr/LumenTV-Compose/releases/download/$currentVersion/updater-macos-amd64"

                "macos-latest-arm64" ->
                    "https://github.com/clevebitr/LumenTV-Compose/releases/download/$currentVersion/updater-macos-arm64"

                "ubuntu-latest-amd64" ->
                    "https://github.com/clevebitr/LumenTV-Compose/releases/download/$currentVersion/updater-linux-amd64"

                "ubuntu-latest-arm64" ->
                    "https://github.com/clevebitr/LumenTV-Compose/releases/download/$currentVersion/updater-linux-arm64"

                "windows-latest-amd64" ->
                    "https://github.com/clevebitr/LumenTV-Compose/releases/download/$currentVersion/updater-windows-amd64.exe"

                else -> {
                    log.warn("Unsupported platform: $platformIdentifier, falling back to linux-amd64")
                    "https://github.com/clevebitr/LumenTV-Compose/releases/download/$currentVersion/updater-linux-amd64"
                }
            }
        }


        private fun getCurrentDirectory(): Path {
            val jarPath = File(UpdateLauncher::class.java.protectionDomain.codeSource.location.toURI()).toPath()
            return if (jarPath.fileName.toString().endsWith(".jar")) {
                jarPath.parent
            } else {
                jarPath.toAbsolutePath().parent
            }
        }

        fun exitApplication() {
            log.info("Exiting application for update...")
            System.exit(0)
        }
    }
}
