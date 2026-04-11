package com.corner.util.core

import com.corner.catvodcore.bean.Result
import com.corner.catvodcore.bean.isEmpty

/**
 * 检查播放结果是否为空（URL 为空）
 */
fun Result.playResultIsEmpty(): Boolean {
    return url.isEmpty()
}

/**
 * 检查详情结果是否为空
 * 注意：如果 list[0] 本身 isEmpty (无ID或无线路) 但提供了 ID，可能属于 token 验证场景，此处返回 false
 */
fun Result.detailIsEmpty(): Boolean {
    if (list.isEmpty()) return true
    if (list[0].isEmpty()) return false 
    return list[0].vodFlags[0].episodes.isEmpty()
}
