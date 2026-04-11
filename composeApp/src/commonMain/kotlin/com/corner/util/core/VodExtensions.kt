package com.corner.util.core

import com.corner.catvodcore.bean.Episode
import com.corner.catvodcore.bean.Vod

/**
 * 在 Vod 的所有线路中查找指定编号的剧集
 * @param number 剧集编号
 * @return 找到的 Episode，若未找到则返回 null
 */
fun Vod.findEpisodeByNumber(number: Int): Episode? {
    return vodFlags.flatMap { it.episodes }.find { it.number == number }
}

/**
 * 检查 Vod 是否为空（无 ID 或无线路）
 */
fun Vod.isEmpty(): Boolean {
    return org.apache.commons.lang3.StringUtils.isBlank(vodId) || vodFlags.isEmpty()
}

/**
 * 获取 Vod 当前激活的剧集
 */
fun Vod.getActivatedEpisode(): Episode? {
    return subEpisode.find { it.activated }
}
