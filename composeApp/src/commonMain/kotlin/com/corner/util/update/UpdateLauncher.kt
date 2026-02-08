package com.corner.util.update

import com.corner.catvodcore.util.Paths
import com.corner.util.OperatingSystem
import com.corner.util.UserDataDirProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
                    val zipTempFile = File(updaterDir.toFile(), "${updaterName}.zip.tmp")

                    // 下载ZIP文件
                    withContext(Dispatchers.IO) {
                        val fileOutputStream = zipTempFile.outputStream()
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

                    // 解压ZIP文件
                    extractUpdaterFromZip(zipTempFile, targetFile)

                    // 删除临时ZIP文件
                    zipTempFile.delete()

                    // 设置执行权限 (Linux/macOS)
                    if (UserDataDirProvider.currentOs != OperatingSystem.Windows) {
                        targetFile.setExecutable(true)
                    }

                    client.close()
                    log.info("Updater extracted to: $targetFile")
                } catch (e: Exception) {
                    log.error("Failed to download and extract updater", e)
                }
            }

            return targetFile
        }

        private fun extractUpdaterFromZip(zipFile: File, targetFile: File) {
            val zipInputStream = java.util.zip.ZipInputStream(zipFile.inputStream())
            try {
                var entry = zipInputStream.nextEntry
                while (entry != null) {
                    // 查找与期望的更新程序名称匹配的条目
                    if (entry.name.endsWith(targetFile.name)) {
                        // 创建目标目录
                        targetFile.parentFile.mkdirs()

                        // 提取文件
                        val fileOutputStream = targetFile.outputStream()
                        try {
                            zipInputStream.copyTo(fileOutputStream)
                        } finally {
                            fileOutputStream.close()
                        }

                        log.info("Extracted updater file: ${targetFile.absolutePath}")
                        break
                    }
                    entry = zipInputStream.nextEntry
                }

                if (!targetFile.exists()) {
                    throw Exception("Updater file not found in ZIP: ${targetFile.name}")
                }
            } finally {
                zipInputStream.close()
            }
        }


        private suspend fun getUpdaterUrlFromActions(): String? {
            val platformIdentifier = PlatformDetector.getPlatformIdentifier()

            return when (platformIdentifier) {
                "macos-latest-amd64" ->
                    "https://nightly.link/clevebitr/LumenTV-Compose/actions/runs/20949175551/updater-binary-macos-latest-amd64.zip"

                "macos-latest-arm64" ->
                    "https://nightly.link/clevebitr/LumenTV-Compose/actions/runs/20949175551/updater-binary-macos-latest-arm64.zip"

                "ubuntu-latest-amd64" ->
                    "https://nightly.link/clevebitr/LumenTV-Compose/actions/runs/20949175551/updater-binary-ubuntu-latest-amd64.zip"

                "ubuntu-latest-arm64" ->
                    "https://nightly.link/clevebitr/LumenTV-Compose/actions/runs/20949175551/updater-binary-ubuntu-latest-arm64.zip"

                "windows-latest-amd64" ->
                    "https://nightly.link/clevebitr/LumenTV-Compose/actions/runs/20949175551/updater-binary-windows-latest-amd64.zip"

                else -> {
                    log.warn("Unsupported platform: $platformIdentifier, falling back to ubuntu-latest-amd64")
                    "https://nightly.link/clevebitr/LumenTV-Compose/actions/runs/20949175551/updater-binary-ubuntu-latest-amd64.zip"
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
