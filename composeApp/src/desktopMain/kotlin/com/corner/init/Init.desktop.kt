package com.corner.init

import com.corner.util.io.Paths
import com.corner.util.net.KtorHeaderUrlFetcher
import com.seiko.imageloader.ImageLoader
import com.seiko.imageloader.component.*
import com.seiko.imageloader.intercept.bitmapMemoryCacheConfig
import com.seiko.imageloader.intercept.imageMemoryCacheConfig
import com.seiko.imageloader.intercept.painterMemoryCacheConfig
import com.seiko.imageloader.util.defaultFileSystem
import okio.Path.Companion.toOkioPath

actual fun initPlatformSpecify() {
}

fun generateImageLoader(): ImageLoader {
    return ImageLoader {
        components {
            add(KtorHeaderUrlFetcher.CustomUrlFetcher)
            setupBase64Components()
            setupSvgComponents()
            setupSkiaComponents(4)
            setupCommonComponents(defaultFileSystem)
        }
        interceptor {
            // cache 32MB bitmap
            bitmapMemoryCacheConfig {
                maxSize(32 * 1024 * 1024) // 32MB
            }
            // cache 50 image
            imageMemoryCacheConfig {
                maxSize(50)
            }
            // cache 50 painter
            painterMemoryCacheConfig {
                maxSize(50)
            }

            diskCacheConfig {
                directory(Paths.picCache().toOkioPath())
                maxSizeBytes(512L * 1024 * 1024) // 512MB
            }
        }
    }
}
