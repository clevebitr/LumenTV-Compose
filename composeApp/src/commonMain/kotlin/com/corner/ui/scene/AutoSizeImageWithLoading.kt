package com.corner.ui.scene

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import com.seiko.imageloader.ImageLoader
import com.seiko.imageloader.LocalImageLoader
import com.seiko.imageloader.model.ImageEvent
import com.seiko.imageloader.model.ImageResult
import com.seiko.imageloader.ui.AutoSizeBox

/**
 * 支持自定义加载指示器的 AutoSizeImage 组件
 * 
 * @param url 图片URL
 * @param contentDescription 内容描述
 * @param modifier 修饰符
 * @param alignment 对齐方式
 * @param contentScale 缩放模式
 * @param alpha 透明度
 * @param colorFilter 颜色过滤器
 * @param imageLoader 图片加载器
 * @param placeholderPainter 占位符绘制器（加载中显示）
 * @param errorPainter 错误绘制器（加载失败显示）
 * @param loadingIndicator 自定义加载指示器（仅在加载中显示）
 * @param isOnlyPostFirstEvent 是否只发布首次事件
 */
@Composable
fun AutoSizeImageWithLoading(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    imageLoader: ImageLoader = LocalImageLoader.current,
    placeholderPainter: (@Composable () -> Painter)? = null,
    errorPainter: (@Composable () -> Painter)? = null,
    loadingIndicator: @Composable (() -> Unit)? = null,
    isOnlyPostFirstEvent: Boolean = true,
) {
    var isLoading by remember(url) { mutableStateOf(true) }
    
    Box(modifier = modifier) {
        AutoSizeBox(
            url = url,
            imageLoader = imageLoader,
            contentAlignment = alignment,
            isOnlyPostFirstEvent = isOnlyPostFirstEvent
        ) { action ->
            when (action) {
                is ImageEvent -> {
                    // 开始加载，显示占位符或加载指示器
                    isLoading = true
                    placeholderPainter?.invoke()?.let { painter ->
                        Image(
                            painter = painter,
                            contentDescription = contentDescription,
                            modifier = Modifier.fillMaxSize(),
                            alignment = alignment,
                            contentScale = contentScale,
                            alpha = alpha,
                            colorFilter = colorFilter
                        )
                    }
                }
                is ImageResult.OfBitmap, is ImageResult.OfImage, is ImageResult.OfPainter -> {
                    // 加载成功，显示图片
                    isLoading = false
                    val painter = when (action) {
                        is ImageResult.OfPainter -> action.painter
                        else -> null
                    }
                    painter?.let {
                        Image(
                            painter = it,
                            contentDescription = contentDescription,
                            modifier = Modifier.fillMaxSize(),
                            alignment = alignment,
                            contentScale = contentScale,
                            alpha = alpha,
                            colorFilter = colorFilter
                        )
                    }
                }
                is ImageResult.OfError, is ImageResult.OfSource -> {
                    // 加载失败，显示错误图片
                    isLoading = false
                    errorPainter?.invoke()?.let { painter ->
                        Image(
                            painter = painter,
                            contentDescription = contentDescription,
                            modifier = Modifier.fillMaxSize(),
                            alignment = alignment,
                            contentScale = contentScale,
                            alpha = alpha,
                            colorFilter = colorFilter
                        )
                    }
                }
            }
        }
        
        // 自定义加载指示器 - 仅在加载中显示
        if (isLoading && loadingIndicator != null) {
            loadingIndicator()
        }
    }
}

/**
 * 支持自定义加载指示器的 AutoSizeImage 组件（使用资源ID）
 */
@Composable
fun AutoSizeImageWithLoading(
    resId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    imageLoader: ImageLoader = LocalImageLoader.current,
    placeholderPainter: (@Composable () -> Painter)? = null,
    errorPainter: (@Composable () -> Painter)? = null,
    loadingIndicator: @Composable (() -> Unit)? = null,
    isOnlyPostFirstEvent: Boolean = true,
) {
    var isLoading by remember(resId) { mutableStateOf(true) }
    
    Box(modifier = modifier) {
        AutoSizeBox(
            resId = resId,
            imageLoader = imageLoader,
            contentAlignment = alignment,
            isOnlyPostFirstEvent = isOnlyPostFirstEvent
        ) { action ->
            when (action) {
                is ImageEvent -> {
                    isLoading = true
                    placeholderPainter?.invoke()?.let { painter ->
                        Image(
                            painter = painter,
                            contentDescription = contentDescription,
                            modifier = Modifier.fillMaxSize(),
                            alignment = alignment,
                            contentScale = contentScale,
                            alpha = alpha,
                            colorFilter = colorFilter
                        )
                    }
                }
                is ImageResult.OfBitmap, is ImageResult.OfImage, is ImageResult.OfPainter -> {
                    isLoading = false
                    val painter = when (action) {
                        is ImageResult.OfPainter -> action.painter
                        else -> null
                    }
                    painter?.let {
                        Image(
                            painter = it,
                            contentDescription = contentDescription,
                            modifier = Modifier.fillMaxSize(),
                            alignment = alignment,
                            contentScale = contentScale,
                            alpha = alpha,
                            colorFilter = colorFilter
                        )
                    }
                }
                is ImageResult.OfError, is ImageResult.OfSource -> {
                    isLoading = false
                    errorPainter?.invoke()?.let { painter ->
                        Image(
                            painter = painter,
                            contentDescription = contentDescription,
                            modifier = Modifier.fillMaxSize(),
                            alignment = alignment,
                            contentScale = contentScale,
                            alpha = alpha,
                            colorFilter = colorFilter
                        )
                    }
                }
            }
        }
        
        if (isLoading && loadingIndicator != null) {
            loadingIndicator()
        }
    }
}
