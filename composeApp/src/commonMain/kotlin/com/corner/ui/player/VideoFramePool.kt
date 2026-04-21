package com.corner.ui.player

import com.seiko.imageloader.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import org.slf4j.LoggerFactory
import java.util.concurrent.LinkedBlockingDeque

private val log = LoggerFactory.getLogger("BitmapPool")

/**
 * Bitmap 对象池
 * 
 * 负责管理 Bitmap 对象的复用和回收
 * 实现 AutoCloseable 接口，确保资源能够被正确清理
 */
class BitmapPool(private var maxPoolSize: Int = 3) : AutoCloseable {
    private val pool = LinkedBlockingDeque<Bitmap>()

    @Volatile
    private var createdCount = 0
    
    @Volatile
    private var isClosed = false

    fun setMaxSize(size: Int) {
        maxPoolSize = size
        log.info("BitmapPool 最大容量更新为: $maxPoolSize")
    }

    fun acquire(width: Int, height: Int): Bitmap {
        synchronized(this) {
            pool.iterator().let { iterator ->
                while (iterator.hasNext()) {
                    val bitmap = iterator.next()
                    // 增加有效性检查
                    if (!bitmap.isClosed && bitmap.width == width && bitmap.height == height) {
                        iterator.remove()
                        return bitmap
                    }
                }
            }

            createdCount++
            return Bitmap().apply {
                try {
                    allocPixels(ImageInfo.makeN32(width, height, ColorAlphaType.PREMUL))
                } catch (e: Exception) {
                    log.error("创建 Bitmap 失败: ${width}x$height", e)
                    throw e
                }
            }
        }
    }

    fun release(bitmap: Bitmap) {
        // 增加有效性检查
        if (bitmap.isClosed) {
            return
        }

        synchronized(this) {
            if (!bitmap.isClosed && pool.size < maxPoolSize) {
                // 检查是否已经存在于池中，避免重复添加
                if (!pool.contains(bitmap)) {
                    if (!pool.offerFirst(bitmap)) {
                        bitmap.close()
                    }
                }
            } else {
                bitmap.close()
            }
        }
    }
    /**
     * 清空池中所有 Bitmap 并释放资源
     */
    fun clear() {
        if (isClosed) return
        
        synchronized(this) {
            pool.forEach { 
                if (!it.isClosed) it.close() 
            }
            pool.clear()
            log.debug("BitmapPool 已清空，共清理 ${pool.size} 个 Bitmap")
        }
    }
    
    /**
     * 关闭 BitmapPool，释放所有资源
     * 实现 AutoCloseable 接口，支持 use 块自动清理
     */
    override fun close() {
        if (isClosed) {
            log.debug("BitmapPool 已关闭，跳过重复关闭")
            return
        }
        
        synchronized(this) {
            if (isClosed) return
            isClosed = true
            
            try {
                log.debug("=====开始关闭 BitmapPool=====")
                clear()
                log.debug("=====BitmapPool 已关闭=====")
            } catch (e: Exception) {
                log.error("关闭 BitmapPool 时出错", e)
            }
        }
    }
}
