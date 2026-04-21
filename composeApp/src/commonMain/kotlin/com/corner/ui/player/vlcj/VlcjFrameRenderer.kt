package com.corner.ui.player.vlcj

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.corner.ui.player.BitmapPool
import com.corner.util.thisLogger
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.roundToInt

/**
 * VLCJ 帧渲染器
 * 
 * 职责：
 * - 管理视频帧的渲染回调
 * - 管理 Bitmap 池和帧缓冲
 * - 提供 Compose ImageBitmap 状态
 * 
 * 遵循单一职责原则（SRP），只负责帧渲染相关逻辑
 * 实现 AutoCloseable 接口，确保资源能够被正确清理
 */
class VlcjFrameRenderer(
    private val bitmapPool: BitmapPool = BitmapPool(3)
) : AutoCloseable {
    private val log = thisLogger()
    
    // 帧数据
    private var byteArray: ByteArray? = null
    private var info: ImageInfo? = null
    
    // Compose UI 状态
    val imageBitmapState: MutableState<ImageBitmap?> = mutableStateOf(null)
    
    // 当前和待释放的 Bitmap
    private var currentBitmap: Bitmap? = null
    private val pendingRelease = ConcurrentLinkedQueue<Bitmap>()
    
    @Volatile
    private var isReleased = false
    
    /**
     * 检查渲染器是否已释放
     */
    fun isReleased(): Boolean = isReleased
    
    /**
     * 创建 VLCJ 视频表面回调
     * 
     * @return CallbackVideoSurface 实例，需要设置到 MediaPlayer 的视频表面
     */
    fun createVideoSurface(): CallbackVideoSurface {
        return CallbackVideoSurface(
            object : BufferFormatCallback {
                private var lastPoolSize = -1
                private var lastWidth = -1
                private var lastHeight = -1
                
                /**
                 * 根据分辨率估算帧率
                 */
                private fun estimateFrameRate(width: Int, height: Int): Int {
                    val pixels = width * height
                    return when {
                        pixels >= 3_000_000 -> 60 // 高分辨率推高帧率（如2K/4K）
                        pixels >= 1_000_000 -> 30 // 主流1080p
                        else -> 24                // 标清或低码率
                    }
                }
                
                /**
                 * 根据分辨率和帧率动态调整 BitmapPool 大小
                 */
                private fun adjustBitmapPoolSize(width: Int, height: Int) {
                    if (width == lastWidth && height == lastHeight) return
                    
                    val resolutionFactor = (width * height) / 1_000_000f
                    val frameRate = estimateFrameRate(width, height)
                    val poolSize = (frameRate * resolutionFactor).roundToInt().coerceIn(2, 12)
                    
                    if (poolSize != lastPoolSize) {
                        bitmapPool.setMaxSize(poolSize)
                        log.info("根据 ${frameRate}fps @ ${width}x$height，调整 BitmapPool 大小为 $poolSize")
                        lastPoolSize = poolSize
                    }
                    
                    lastWidth = width
                    lastHeight = height
                }
                
                override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                    info = ImageInfo.makeN32(sourceWidth, sourceHeight, ColorAlphaType.OPAQUE)
                    adjustBitmapPoolSize(width = sourceWidth, height = sourceHeight)
                    return RV32BufferFormat(sourceWidth, sourceHeight)
                }
                
                override fun newFormatSize(bufferWidth: Int, bufferHeight: Int, displayWidth: Int, displayHeight: Int) {
                    // 不需要处理
                }
                
                override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {
                    byteArray = ByteArray(buffers[0].limit())
                }
            },
            object : RenderCallback {
                override fun lock(mediaPlayer: MediaPlayer?) {
                    // 不需要处理
                }
                
                override fun display(
                    mediaPlayer: MediaPlayer,
                    nativeBuffers: Array<out ByteBuffer>,
                    bufferFormat: BufferFormat,
                    displayWidth: Int,
                    displayHeight: Int
                ) {
                    // 增加状态检查
                    if (isReleased) return
                    
                    val width = bufferFormat.width
                    val height = bufferFormat.height
                    val byteBuffer = nativeBuffers[0]
                    
                    // 增加缓冲区有效性检查
                    if (byteBuffer.limit() <= 0) return
                    
                    try {
                        byteBuffer.get(byteArray)
                        byteBuffer.rewind()
                        
                        // 从池中获取 Bitmap（复用或新建）
                        val bmp = bitmapPool.acquire(width, height)
                        bmp.installPixels(byteArray)
                        
                        // 将旧 Bitmap 加入待释放队列
                        currentBitmap?.let {
                            pendingRelease.add(it)
                        }
                        
                        // 释放待回收的 Bitmap
                        releasePendingBitmaps()
                        
                        // 更新当前 Bitmap 和 UI 状态
                        currentBitmap = bmp
                        imageBitmapState.value = bmp.asComposeImageBitmap()
                    } catch (e: Exception) {
                        log.error("渲染帧时发生错误", e)
                        // 确保在出错时清理资源
                        currentBitmap?.let {
                            if (!it.isClosed) it.close()
                            currentBitmap = null
                        }
                    }
                }
                
                override fun unlock(mediaPlayer: MediaPlayer?) {
                    // 不需要处理
                }
            },
            true,
            VideoSurfaceAdapters.getVideoSurfaceAdapter()
        )
    }
    
    /**
     * 释放待回收的 Bitmap
     */
    private fun releasePendingBitmaps() {
        while (pendingRelease.isNotEmpty()) {
            val bitmap = pendingRelease.poll()
            if (!bitmap.isClosed) {
                bitmapPool.release(bitmap)
            }
        }
    }
    
    /**
     * 清理帧渲染资源（用于画质切换等场景）
     */
    fun cleanup() {
        synchronized(this) {
            // 清理待释放的 bitmap
            releasePendingBitmaps()
            
            // 清理当前 bitmap
            currentBitmap?.let { bitmap ->
                if (!bitmap.isClosed) {
                    bitmap.close()
                }
                currentBitmap = null
            }
            
            // 清空图像状态
            imageBitmapState.value = null
            
            log.debug("帧渲染器资源已清理")
        }
    }
    
    /**
     * 释放渲染器所有资源
     * 实现 AutoCloseable 接口，支持 use 块自动清理
     */
    override fun close() {
        release()
    }
    
    /**
     * 释放渲染器所有资源
     */
    fun release() {
        if (isReleased) {
            log.debug("帧渲染器已释放，跳过重复释放")
            return
        }
        
        synchronized(this) {
            if (isReleased) return
            isReleased = true
            
            try {
                log.debug("=====开始释放帧渲染器资源=====")
                
                // 清理 BitmapPool
                bitmapPool.clear()
                log.debug("已清理 BitmapPool 中的所有 Bitmap 实例")
                
                // 清理帧数据
                cleanup()
                
                // 清理引用
                byteArray = null
                info = null
                
                log.debug("=====帧渲染器资源释放成功=====")
            } catch (e: Throwable) {
                log.error("释放帧渲染器资源时出错：", e)
            }
        }
    }
}
