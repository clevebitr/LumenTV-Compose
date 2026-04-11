package com.corner.util.update

import com.corner.util.OperatingSystem
import com.corner.util.UserDataDirProvider

object PlatformDetector {
    fun getPlatformIdentifier(): String {
        val os = UserDataDirProvider.currentOs
        val arch = System.getProperty("os.arch").lowercase()

        return when (os) {
            OperatingSystem.Windows -> {
                when {
                    arch.contains("64") || arch.contains("x86_64") || arch.contains("amd64") -> "windows-latest-amd64"
                    arch.contains("arm") -> "windows-latest-arm64"
                    else -> "windows-latest-amd64" // 默认 amd64
                }
            }
            OperatingSystem.Linux -> {
                when {
                    arch.contains("64") || arch.contains("x86_64") || arch.contains("amd64") -> "ubuntu-latest-amd64"
                    arch.contains("arm") -> "ubuntu-latest-arm64"
                    else -> "ubuntu-latest-amd64" // 默认 amd64
                }
            }
            OperatingSystem.MacOS -> {
                when {
                    arch.contains("aarch64") || arch.contains("arm") -> "macos-latest-arm64"
                    else -> "macos-latest-amd64" // 包含 x86_64 和其他架构
                }
            }
            else -> "ubuntu-latest-amd64" // 默认值
        }
    }

    fun getUpdaterFileName(): String {
        return when (UserDataDirProvider.currentOs) {
            OperatingSystem.Windows -> "updater.exe"
            OperatingSystem.Linux, OperatingSystem.MacOS -> "updater"
            else -> "updater"
        }
    }
}
