package com.corner.ui.player

/**
 * 媒体播放控制接口
 * 
 * 负责播放器的核心播放控制功能
 * 遵循单一职责原则（SRP），只包含与播放控制相关的方法
 */
interface MediaPlayer {
    /**
     * 加载媒体URL（同步）
     */
    fun load(url: String): MediaPlayer
    
    /**
     * 加载媒体URL（异步，带超时）
     */
    suspend fun loadURL(url: String, timeoutMillis: Long = 10000): MediaPlayer
    
    /**
     * 开始播放
     */
    fun play()
    
    /**
     * 播放指定URL
     */
    fun play(url: String)
    
    /**
     * 暂停播放
     */
    fun pause()
    
    /**
     * 停止播放（同步）
     */
    fun stop()
    
    /**
     * 停止播放（异步）
     */
    suspend fun stopAsync()
    
    /**
     * 跳转到指定位置（毫秒）
     */
    fun seekTo(timestamp: Long)
    
    /**
     * 设置音量（0.0 - 1.5）
     */
    fun setVolume(value: Float)
    
    /**
     * 音量增加
     */
    fun volumeUp()
    
    /**
     * 音量减少
     */
    fun volumeDown()
    
    /**
     * 切换静音状态
     */
    fun toggleSound()
    
    /**
     * 设置播放速度
     */
    fun speed(rate: Float)
    
    /**
     * 停止快进
     */
    fun stopForward()
    
    /**
     * 快进
     */
    fun fastForward()
    
    /**
     * 切换播放/暂停状态
     */
    fun togglePlayStatus()
    
    /**
     * 快退（默认15秒）
     */
    fun backward(time: String = "15s")
    
    /**
     * 快进（默认15秒）
     */
    fun forward(time: String = "15s")
    
    /**
     * 设置视频宽高比
     */
    fun setAspectRatio(aspectRatio: String)
    
    /**
     * 获取当前视频宽高比
     */
    fun getAspectRatio(): String
    
    /**
     * 检查播放器实例是否就绪
     */
    fun isPlayerInstanceReady(): Boolean
}
