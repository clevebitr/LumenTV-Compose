package com.corner.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.corner.util.system.SysVerUtil

/**
 * from  acfun-multiplatform-client
 */
@Composable
fun FpsMonitor(modifier: Modifier, settingVersion: Int = 0, fpsMonitorEnabled: Boolean?=false) {
    
    // 如果未启用，不渲染任何内容
    fpsMonitorEnabled?.let { if (!it) return }
    
    // 启用了才执行下面的逻辑
    var fpsCount by remember { mutableStateOf(0) }
    var fps by remember { mutableStateOf(0) }
    var lastUpdate by remember { mutableStateOf(0L) }
    val platformName = remember {
        SysVerUtil.currentOs.name
    }
    
    Text(
        text = "$platformName\nFPS: $fps",
        modifier = modifier,
        color = Color.Green,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.body1
    )
    
    // 使用 settingVersion 作为 key，确保设置变化时重新启动计时器
    LaunchedEffect(settingVersion) {
        fpsCount = 0
        fps = 0
        lastUpdate = 0L
        while (true) {
            withFrameMillis { ms ->
                fpsCount++
                if (fpsCount == 5) {
                    if (lastUpdate > 0) {
                        fps = (5000 / (ms - lastUpdate)).toInt()
                    }
                    lastUpdate = ms
                    fpsCount = 0
                }
            }
        }
    }
}