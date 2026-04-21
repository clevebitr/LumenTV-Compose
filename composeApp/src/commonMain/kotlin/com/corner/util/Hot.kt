package com.corner.util

import com.corner.catvodcore.viewmodel.SiteViewModel
import com.corner.util.net.Http
import com.corner.util.json.Jsons
import com.corner.catvodcore.viewmodel.GlobalAppState
import com.corner.ui.scene.SnackBar
import io.ktor.http.*
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromStream
import okhttp3.Headers.Companion.toHeaders
import org.slf4j.LoggerFactory
import java.net.ConnectException

private val log = LoggerFactory.getLogger("Hot")

@Serializable
data class Hot(val data: List<HotData>) {
    companion object {
        /**
         * 获取热搜列表
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun getHotList() {
            SiteViewModel.viewModelScope.launch {
                try {
                    Http.get(
                        "https://api.web.360kan.com/v1/rank?cat=1",
                        headers = mapOf(HttpHeaders.Referrer to "https://www.360kan.com/rank/general").toHeaders()
                    ).execute().use { response ->
                        if (response.isSuccessful) {
                            GlobalAppState.hotList.value = Jsons.decodeFromStream<Hot>(
                                response.body.byteStream()
                            ).data
                        }
                    }
                } catch (e: ConnectException) {
                    log.error("请求热搜失败：网络连接错误", e)
                    SnackBar.postMsg(
                        "热搜加载失败：无法连接到网络\n请检查代理设置或网络连接",
                        type = SnackBar.MessageType.WARNING
                    )
                } catch (e: Exception) {
                    log.error("请求热搜失败", e)
                }
            }
        }
    }
}

@Serializable
data class HotData(val title: String, val comment: String, val upinfo: String, val description: String)