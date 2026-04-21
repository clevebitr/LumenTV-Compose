package com.corner.util.playwright

import com.corner.ui.scene.SnackBar
import com.corner.util.core.thisLogger
import com.corner.util.io.Paths
import com.corner.util.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.SystemUtils
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

/**
 * Playwright 浏览器管理器
 * 负责自动下载和配置 Playwright 所需的 Chromium 浏览器
 */
object PlaywrightBrowserManager {
    private val log = thisLogger()

    private val browsersDir = Paths.playwrightBrowsers()

    /**
     * 获取浏览器可执行文件路径
     * @return 浏览器可执行文件的绝对路径
     */
    fun getBrowserExecutablePath(): String {
        val browserPath = when {
            SystemUtils.IS_OS_WINDOWS -> {
                browsersDir.resolve("chrome-win").resolve("chrome.exe")
            }
            SystemUtils.IS_OS_MAC -> {
                browsersDir.resolve("chrome-mac").resolve("Chromium.app")
                    .resolve("Contents").resolve("MacOS").resolve("Chromium")
            }
            SystemUtils.IS_OS_LINUX -> {
                browsersDir.resolve("chrome-linux").resolve("chrome")
            }
            else -> throw RuntimeException("不支持的操作系统")
        }
        
        return browserPath.absolutePath
    }

    /**
     * 检查浏览器是否存在且可用
     */
    fun isBrowserAvailable(): Boolean {
        val browserPath = File(getBrowserExecutablePath())
        return browserPath.exists()
    }

    /**
     * 确保浏览器已下载，如果不存在则自动下载
     * @param onProgress 进度回调 (0.0 - 1.0)
     * @return 浏览器可执行文件路径
     */
    suspend fun ensureBrowserDownloaded(onProgress: ((Double) -> Unit)? = null): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (isBrowserAvailable()) {
                    log.info("Playwright 浏览器已存在: ${getBrowserExecutablePath()}")
                    onProgress?.invoke(1.0)
                    return@withContext Result.success(getBrowserExecutablePath())
                }

                log.info("开始下载 Playwright 浏览器...")
                SnackBar.postMsg("正在下载 Playwright 浏览器，请稍候...", type = SnackBar.MessageType.INFO)

                val downloadResult = downloadBrowser(onProgress)
                
                if (downloadResult.isFailure) {
                    val error = downloadResult.exceptionOrNull()?.message ?: "未知错误"
                    log.error("浏览器下载失败: $error")
                    SnackBar.postMsg("浏览器下载失败: $error", type = SnackBar.MessageType.ERROR)
                    return@withContext Result.failure(downloadResult.exceptionOrNull() ?: Exception(error))
                }

                val browserPath = downloadResult.getOrNull()
                if (browserPath != null && File(browserPath).exists()) {
                    log.info("Playwright 浏览器下载成功: $browserPath")
                    SnackBar.postMsg("Playwright 浏览器下载完成", type = SnackBar.MessageType.SUCCESS)
                    onProgress?.invoke(1.0)
                    Result.success(browserPath)
                } else {
                    val errorMsg = "浏览器下载完成但文件不存在"
                    log.error(errorMsg)
                    SnackBar.postMsg(errorMsg, type = SnackBar.MessageType.ERROR)
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                log.error("浏览器下载异常", e)
                SnackBar.postMsg("浏览器下载异常: ${e.message}", type = SnackBar.MessageType.ERROR)
                Result.failure(e)
            }
        }
    }

    /**
     * 下载浏览器
     * @param onProgress 进度回调
     * @return 下载结果
     */
    private fun downloadBrowser(onProgress: ((Double) -> Unit)? = null): Result<String> {
        return try {
            val browsersDir = Paths.playwrightBrowsers()
            val tempDir = Paths.playwrightTemp()
            
            // 确保目录存在
            Files.createDirectories(browsersDir.toPath())
            Files.createDirectories(tempDir.toPath())

            val platform = getPlatformName()
            val downloadUrl = getDownloadUrl(platform)
            
            log.info("从 $downloadUrl 下载浏览器...")
            
            // 下载压缩包（带进度）
            val zipFile = tempDir.resolve("browser-$platform.zip")
            downloadFileWithProgress(downloadUrl, zipFile, onProgress)
            
            // 解压
            log.info("解压浏览器文件...")
            onProgress?.invoke(0.8) // 解压阶段 80%
            val extractDir = unzip(zipFile, browsersDir)
            
            // 清理临时文件
            zipFile.delete()
            
            onProgress?.invoke(1.0) // 完成 100%
            log.info("浏览器解压完成: ${extractDir.absolutePath}")
            Result.success(getBrowserExecutablePath())
        } catch (e: Exception) {
            log.error("浏览器下载失败", e)
            Result.failure(e)
        }
    }

    /**
     * 下载文件（带进度）
     */
    private fun downloadFileWithProgress(url: String, destFile: File, onProgress: ((Double) -> Unit)? = null) {
        val response = Http.get(url).execute()
        val body = response.body
        
        if (!response.isSuccessful || body == null) {
            throw RuntimeException("下载失败: HTTP ${response.code}")
        }

        val totalBytes = body.contentLength()
        val inputStream = body.byteStream()
        var totalRead = 0L
        
        destFile.outputStream().use { output ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                
                // 更新进度（0.0 - 0.8，因为解压还需要 20%）
                if (totalBytes > 0 && onProgress != null) {
                    val progress = (totalRead.toDouble() / totalBytes) * 0.8
                    onProgress.invoke(progress)
                }
            }
        }
        log.info("文件下载完成: ${destFile.absolutePath}, 大小: ${totalRead / 1024 / 1024} MB")
    }

    /**
     * 下载文件（旧版本，保留兼容）
     */
    private fun downloadFile(url: String, destFile: File) {
        downloadFileWithProgress(url, destFile, null)
    }

    /**
     * 解压 ZIP 文件
     */
    private fun unzip(zipFile: File, destDir: File): File {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            
            while (entry != null) {
                val outputFile = destDir.resolve(entry.name)
                
                if (entry.isDirectory) {
                    Files.createDirectories(outputFile.toPath())
                } else {
                    // 确保父目录存在
                    outputFile.parentFile?.mkdirs()
                    
                    // 复制文件内容
                    Files.copy(zis, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    
                    // 设置可执行权限（Linux/Mac）
                    if (!SystemUtils.IS_OS_WINDOWS) {
                        outputFile.setExecutable(true)
                    }
                }
                
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        
        return destDir
    }

    /**
     * 获取平台名称
     */
    private fun getPlatformName(): String {
        return when {
            SystemUtils.IS_OS_WINDOWS -> "win64"
            SystemUtils.IS_OS_MAC -> {
                if (SystemUtils.OS_ARCH.contains("aarch64") || SystemUtils.OS_ARCH.contains("arm")) {
                    "mac-arm64"
                } else {
                    "mac"
                }
            }
            SystemUtils.IS_OS_LINUX -> "linux"
            else -> throw RuntimeException("不支持的操作系统: ${SystemUtils.OS_NAME}")
        }
    }

    /**
     * 获取修订版本号
     */
    private fun getRevision(): String {
        return "1194" // 对应 Chrome 141
    }

    /**
     * 获取下载 URL
     */
    private fun getDownloadUrl(platform: String): String {
        val revision = getRevision()
        return when (platform) {
            "win64" -> "https://playwright.azureedge.net/builds/chromium/$revision/chromium-win64.zip"
            "mac" -> "https://playwright.azureedge.net/builds/chromium/$revision/chromium-mac.zip"
            "mac-arm64" -> "https://playwright.azureedge.net/builds/chromium/$revision/chromium-mac-arm64.zip"
            "linux" -> "https://playwright.azureedge.net/builds/chromium/$revision/chromium-linux.zip"
            else -> throw RuntimeException("不支持的平台: $platform")
        }
    }

    /**
     * 获取浏览器缓存目录
     */
    fun getBrowserCacheDir(): String {
        return Paths.playwrightBrowsers().absolutePath
    }

    /**
     * 获取临时文件目录
     */
    fun getTempDir(): String {
        return Paths.playwrightTemp().absolutePath
    }

    /**
     * 清除浏览器缓存
     */
    fun clearBrowserCache(): Boolean {
        return try {
            val browsersDir = Paths.playwrightBrowsers()
            if (browsersDir.exists()) {
                browsersDir.deleteRecursively()
                log.info("浏览器缓存已清除")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            log.error("清除浏览器缓存失败", e)
            false
        }
    }
}
