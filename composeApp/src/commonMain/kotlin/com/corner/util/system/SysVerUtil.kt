package com.corner.util.system

import cn.hutool.system.SystemUtil

object SysVerUtil {
    fun isWin10(): Boolean {
        return SystemUtil.getOsInfo().name.equals("Windows 10")
    }

    /**
     * 获取操作系统类型（推荐）
     */
    fun getOperatingSystem(): OperatingSystem {
        val osInfo = SystemUtil.getOsInfo()
        return when {
            osInfo.isLinux -> OperatingSystem.Linux
            osInfo.isMac -> OperatingSystem.MacOS
            osInfo.isWindows -> OperatingSystem.Windows
            else -> OperatingSystem.Unknown
        }
    }

    /**
     * 获取当前操作系统（便捷属性）
     */
    val currentOs: OperatingSystem get() = getOperatingSystem()

    /**
     * 获取标准化的架构名称
     * @return "x64", "arm64" 等
     */
    fun getArchName(): String {
        val arch = SystemUtil.getOsInfo().arch.lowercase()
        return when {
            "x86" in arch || "x64" in arch || "amd64" in arch -> "x64"
            "arm" in arch || "aarch" in arch -> "arm64"
            else -> "x64" // 默认 x64
        }
    }

    /**
     * 获取 OS-架构 组合名称（用于资源路径等）
     * @return 例如: "windows-x64", "linux-arm64", "macos-aarch64"
     */
    fun getOsArchName(): String {
        val osName = getOperatingSystem()
        val arch = SystemUtil.getOsInfo().arch
        return "${osName.name.lowercase()}-$arch"
    }
}

/**
 * 统一的操作系统枚举
 */
enum class OperatingSystem {
    Linux,
    MacOS,
    Windows,
    Unknown
}