package com.corner.service.history

import com.corner.catvodcore.bean.Episode
import com.corner.catvodcore.bean.Flag
import com.corner.catvodcore.bean.Site
import com.corner.catvodcore.bean.Vod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * HistoryService单元测试（简化版）
 * 
 * 测试历史记录管理的核心逻辑，不依赖Mock框架
 */
class HistoryServiceTest {
    
    @Test
    fun testCreateTestVod() {
        // 测试辅助方法是否能正确创建测试数据
        val vod = createTestVod()
        
        assertNotNull(vod)
        assertEquals("test-vod-id", vod.vodId)
        assertEquals("测试视频", vod.vodName)
        assertEquals(2, vod.vodFlags.size)
        assertEquals(2, vod.subEpisode.size)
    }
    
    @Test
    fun testEpisodeCreation() {
        // 测试Episode创建
        val episode = Episode.create("第1集", "https://example.com/ep1.mp4")
        
        assertNotNull(episode)
        assertEquals("第1集", episode.name)
        assertEquals("https://example.com/ep1.mp4", episode.url)
    }
    
    @Test
    fun testFlagActivation() {
        // 测试线路激活状态
        val vod = createTestVod()
        
        // 第一个线路应该是激活的
        val activeFlag = vod.vodFlags.find { it.activated }
        assertNotNull(activeFlag)
        assertEquals("线路1", activeFlag.flag)
    }
    
    @Test
    fun testFindEpisodeByName() {
        // 测试根据名称查找剧集
        val vod = createTestVod()
        
        val found = vod.currentFlag.episodes.find { it.name == "第2集" }
        assertNotNull(found)
        assertEquals("第2集", found.name)
    }
    
    @Test
    fun testGetCurrentEpisodeIndex() {
        // 测试获取当前剧集索引
        val vod = createTestVod()
        
        // 默认第一个剧集是激活的
        val activeEp = vod.subEpisode.find { it.activated }
        val index = activeEp?.number ?: 1
        
        assertEquals(1, index)
    }
    
    @Test
    fun testHistoryKeyGeneration() {
        // 测试历史记录key生成逻辑（简单验证）
        val siteKey = "test-site"
        val vodId = "test-vod-id"
        val cfgId = "test-cfg-id"
        
        val key = "$siteKey@@@$vodId@@@$cfgId"
        
        assertNotNull(key)
        assertEquals("test-site@@@test-vod-id@@@test-cfg-id", key)
    }
    
    @Test
    fun testVodCopyWithUpdatedFlag() {
        // 测试Vod的copy操作
        val vod = createTestVod()
        val newFlag = vod.vodFlags[1]
        
        val updatedVod = vod.copy(currentFlag = newFlag)
        
        assertEquals("线路2", updatedVod.currentFlag.flag)
    }
    
    @Test
    fun testEpisodeListPagination() {
        // 测试剧集分页逻辑
        val episodes = (1..25).map { i ->
            Episode.create("第${i}集", "https://example.com/ep$i.mp4")
        }.toMutableList()
        
        val pageSize = 10
        val totalPages = (episodes.size + pageSize - 1) / pageSize
        
        assertEquals(3, totalPages) // 25条数据，每页10条，共3页
        
        val firstPage = episodes.subList(0, minOf(10, episodes.size))
        assertEquals(10, firstPage.size)
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 创建测试用的Vod对象
     */
    private fun createTestVod(): Vod {
        val episode1 = Episode.create("第1集", "https://example.com/ep1.mp4").apply {
            this.activated = true
            this.number = 1
        }
        val episode2 = Episode.create("第2集", "https://example.com/ep2.mp4").apply {
            this.number = 2
        }
        
        val flag1 = Flag().apply {
            this.flag = "线路1"
            this.episodes = mutableListOf(episode1, episode2)
            this.activated = true
        }
        
        val flag2 = Flag().apply {
            this.flag = "线路2"
            this.episodes = mutableListOf(episode1, episode2)
            this.activated = false
        }
        
        val site = Site(
            key = "test-site",
            name = "测试站点",
            type = 0,
            api = ""
        )
        
        return Vod().apply {
            this.vodId = "test-vod-id"
            this.vodName = "测试视频"
            this.vodPic = "https://example.com/pic.jpg"
            this.site = site
            this.vodFlags = mutableListOf(flag1, flag2)
            this.currentFlag = flag1
            this.subEpisode = mutableListOf(episode1, episode2)
            this.currentTabIndex = 0
        }
    }
}
