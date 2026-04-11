package com.corner.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corner.util.thisLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.plus

abstract class BaseViewModel(dispatcher: CoroutineDispatcher = Dispatchers.Default):ViewModel() {
    /**
     * 使用viewModelScope确保协程随ViewModel生命周期自动取消
     * 避免内存泄漏和资源浪费
     */
    val scope: CoroutineScope by lazy {
        viewModelScope + dispatcher
    }
    val log: org.slf4j.Logger = thisLogger()
}