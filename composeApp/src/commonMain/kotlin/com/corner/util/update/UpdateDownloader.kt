package com.corner.util.update

import com.corner.util.net.KtorClient
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
            client: HttpClient = KtorClient.client
        ): Flow<DownloadProgress> = flow {
            emit(DownloadProgress.Starting)

            val tempFile = File(destination.parent, "${destination.name}.tmp")

            client.prepareGet(url).execute { httpResponse ->
                val contentLength = httpResponse.contentLength() ?: 0L
                log.info("Starting download. Length: $contentLength")

                val channel: ByteReadChannel = httpResponse.bodyAsChannel()
                var totalBytesRead = 0L
                var lastEmitTime = 0L
                var lastEmitProgress = -1

                Files.newOutputStream(tempFile.toPath()).use { output ->
                    val buffer = ByteArray(8192)
                    while (!channel.isClosedForRead) {
                        val bytesRead = channel.readAvailable(buffer)

                        if (bytesRead < 0) break

                        if (bytesRead > 0) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead

                            val currentTime = System.currentTimeMillis()

                            if (currentTime - lastEmitTime > 500 || lastEmitTime == 0L) {
                                val progress = if (contentLength > 0) {
                                    (totalBytesRead * 100 / contentLength).toInt()
                                } else {
                                    0
                                }

                                if (progress != lastEmitProgress || contentLength == 0L) {
                                    emit(DownloadProgress.Downloading(progress, totalBytesRead, contentLength))
                                    lastEmitProgress = progress
                                    lastEmitTime = currentTime
                                }
                            }
                        }
                    }
                }

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
            client: HttpClient = KtorClient.client
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
