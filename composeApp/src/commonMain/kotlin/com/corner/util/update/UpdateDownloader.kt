package com.corner.util.update

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class UpdateDownloader {
    companion object {
        private val log = LoggerFactory.getLogger(UpdateDownloader::class.java)
        fun downloadUpdate(
            url: String,
            destination: File,
            client: HttpClient = com.corner.util.network.KtorClient.client
        ): Flow<DownloadProgress> = flow {
            emit(DownloadProgress.Starting)

            val tempFile = File(destination.parent, "${destination.name}.tmp")

            // 1. 使用 prepareGet 确保真正的流式下载 (Streaming)
            client.prepareGet(url).execute { httpResponse ->
                val contentLength = httpResponse.contentLength() ?: 0L // Ktor 扩展方法获取长度
                log.info("Starting download. Length: $contentLength")

                val channel: ByteReadChannel = httpResponse.bodyAsChannel()
                var totalBytesRead = 0L
                var lastEmitTime = 0L // 初始为0确保第一次尽快更新
                var lastEmitProgress = -1

                // 创建文件输出流
                Files.newOutputStream(tempFile.toPath()).use { output ->
                    val buffer = ByteArray(8192)

                    // 2. 更加健壮的循环读取方式
                    while (!channel.isClosedForRead) {
                        // 读取数据
                        val bytesRead = channel.readAvailable(buffer)

                        // 如果返回 -1，代表流结束 (EOF)，必须跳出循环
                        if (bytesRead < 0) break

                        if (bytesRead > 0) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead

                            val currentTime = System.currentTimeMillis()

                            // 3. 进度更新逻辑优化
                            // 限制更新频率为 500ms，或者这是第一次读取
                            if (currentTime - lastEmitTime > 500 || lastEmitTime == 0L) {
                                val progress = if (contentLength > 0) {
                                    (totalBytesRead * 100 / contentLength).toInt()
                                } else {
                                    0 // 未知大小时，进度保持 0 或由 UI 处理 indeterminate 状态
                                }

                                // 只有进度数字变化，或者对于未知大小的文件（为了更新已读字节数）才发射
                                if (progress != lastEmitProgress || contentLength == 0L) {
                                    emit(DownloadProgress.Downloading(progress, totalBytesRead, contentLength))
                                    lastEmitProgress = progress
                                    lastEmitTime = currentTime
                                }
                            }
                        }
                    }
                }

                // 4. 确保循环结束后发送最终进度 (即使 Content-Length 未知)
                // 如果是未知大小，此时我们假设 totalBytesRead 就是总大小
                val finalLength = if (contentLength > 0) contentLength else totalBytesRead
                emit(DownloadProgress.Downloading(100, totalBytesRead, finalLength))

                log.info("Download stream finished. Renaming file...")
            }

            // 移动文件
            Files.move(
                tempFile.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )

            log.info("Download completed: ${destination.absolutePath}")
            emit(DownloadProgress.Completed(destination))

        }.catch { e ->
            log.error("Download failed", e)
            emit(DownloadProgress.Failed(e.message ?: "Unknown error"))
        }.flowOn(Dispatchers.IO)


        suspend fun downloadUpdateSync(
            url: String,
            destination: File,
            client: HttpClient = com.corner.util.network.KtorClient.client
        ): Result<File> = withContext(Dispatchers.IO) {
            try {
                val response: HttpResponse = client.get(url)
                val tempFile = File(destination.parent, "${destination.name}.tmp")

                val channel: ByteReadChannel = response.body()
                Files.newOutputStream(tempFile.toPath()).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (!channel.isClosedForRead) {
                        bytesRead = channel.readAvailable(buffer)
                        if (bytesRead > 0) {
                            output.write(buffer, 0, bytesRead)
                        } else if (bytesRead == -1) {
                            break
                        }
                    }
                }

                Files.move(
                    tempFile.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )

                Result.success(destination)
            } catch (e: Exception) {
                log.error("Download failed", e)
                Result.failure(e)
            }
        }
    }
}

sealed class DownloadProgress {
    object Starting : DownloadProgress()
    data class Downloading(
        val progress: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : DownloadProgress()

    data class Completed(val file: File) : DownloadProgress()
    data class Failed(val error: String) : DownloadProgress()
}
