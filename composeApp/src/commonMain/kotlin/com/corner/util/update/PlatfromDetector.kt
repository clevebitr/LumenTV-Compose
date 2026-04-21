package com.corner.util.update

import com.corner.util.system.OperatingSystem
import com.corner.util.system.SysVerUtil

object PlatformDetector {
    fun getPlatformIdentifier(): String {
        val os = SysVerUtil.currentOs
        val arch = SysVerUtil.getArchName()

        return when (os) {
            OperatingSystem.Windows -> {
                when (arch) {
                    "x64" -> "windows-latest-amd64"
                    "arm64" -> "windows-latest-arm64"
                    else -> "windows-latest-amd64" // 默认 amd64
                }
            }
            OperatingSystem.Linux -> {
                when (arch) {
                    "x64" -> "ubuntu-latest-amd64"
                    "arm64" -> "ubuntu-latest-arm64"
                    else -> "ubuntu-latest-amd64" // 默认 amd64
                }
            }
            OperatingSystem.MacOS -> {
                when (arch) {
                    "arm64" -> "macos-latest-arm64"
                    else -> "macos-latest-amd64" // 包含 x86_64 和其他架构
                }
            }
            else -> "ubuntu-latest-amd64" // 默认值
        }
    }

    fun getUpdaterFileName(): String {
        return when (SysVerUtil.currentOs) {
            OperatingSystem.Windows -> "updater.exe"
            OperatingSystem.Linux, OperatingSystem.MacOS -> "updater"
            else -> "updater"
        }
    }
}
