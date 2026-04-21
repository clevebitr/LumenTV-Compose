package com.corner.init

import com.corner.util.settings.SettingStore
import com.corner.util.io.Paths
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.Configurator
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration
import org.slf4j.LoggerFactory
import java.io.PrintStream

private val log = LoggerFactory.getLogger("Console")

/**
 * TV 日志配置器 - 基于 Log4j2
 *
 * 功能：
 * 1. 控制台输出（彩色格式）
 * 2. 文件滚动输出（50MB/文件，保留5天）
 * 3. 动态日志级别（从 SettingStore 读取）
 * 4. 抑制第三方库噪音（jupnp、jetty）
 */
class TVLogConfigurator {

    companion object {
        fun configure() {
            println("Log Config: Initializing Log4j2...")

            val builder = ConfigurationBuilderFactory.newConfigurationBuilder()
            builder.setStatusLevel(Level.WARN)
            builder.setConfigurationName("TVLogConfig")

            // 添加控制台 Appender（修复：直接传入创建的 appender）
            builder.add(createConsoleAppender(builder))

            // 添加文件 Appender（修复：直接传入创建的 appender）
            builder.add(createFileAppender(builder))

            // 配置 Root Logger
            val rootLoggerBuilder = builder.newRootLogger(Level.INFO)
            rootLoggerBuilder.add(builder.newAppenderRef("Console"))
            rootLoggerBuilder.add(builder.newAppenderRef("RollingFile"))
            builder.add(rootLoggerBuilder)

            // 抑制第三方库日志
            val jupnpLogger = builder.newLogger("org.jupnp", Level.OFF)
            builder.add(jupnpLogger)

            val jettyLogger = builder.newLogger("org.eclipse.jetty", Level.OFF)
            builder.add(jettyLogger)
            
            // 抑制 Netty 的连接断开噪音
            val nettyLogger = builder.newLogger("io.netty", Level.WARN)
            builder.add(nettyLogger)
            
            // 抑制 Ktor Application 的 I/O 操作失败日志
            val ktorAppLogger = builder.newLogger("io.ktor.server.application.Application", Level.WARN)
            builder.add(ktorAppLogger)

            // 应用配置
            val ctx = LogManager.getContext(false) as LoggerContext
            ctx.start(builder.build())

            // 设置动态日志级别
            setDynamicLogLevel()

            println("Log4j2 configured successfully.")
        }

        private fun createConsoleAppender(builder: ConfigurationBuilder<BuiltConfiguration>): org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder {
            val pattern = "%d{HH:mm:ss.SSS} %highlight{%-5level}{FATAL=red bold, ERROR=red, WARN=yellow bold, INFO=green, DEBUG=cyan, TRACE=blue} | %-15.15thread | %20.20logger{0} | %msg%n"

            return builder.newAppender("Console", "CONSOLE")
                .addAttribute("target", "SYSTEM_OUT")
                .add(
                    builder.newLayout("PatternLayout")
                        .addAttribute("pattern", pattern)
                        .addAttribute("disableAnsi", "false")
                        .addAttribute("noConsoleNoAnsi", "false")
                        .addAttribute("charset", "UTF-8")
                )
        }

        private fun createFileAppender(builder: ConfigurationBuilder<BuiltConfiguration>): org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder {
            val logPath = Paths.logPath().toString()
            val fileName = "$logPath/LumenTV.log"
            // 按日期和大小滚动：TV_2024-01-15_1.log.gz
            val filePattern = "$logPath/LumenTV_%d{yyyy-MM-dd}_%i.log.gz"

            val appender = builder.newAppender("RollingFile", "RollingFile")
                .addAttribute("fileName", fileName)
                .addAttribute("filePattern", filePattern)
                .addAttribute("append", true)
                .addAttribute("immediateFlush", "true")  // 立即刷新，避免日志丢失

            val layout = builder.newLayout("PatternLayout")
                .addAttribute("pattern", "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] [%logger{0}] %msg%n")
                .addAttribute("charset", "UTF-8")
            appender.add(layout)

            val policies = builder.newComponent("Policies")

            policies.addComponent(
                builder.newComponent("TimeBasedTriggeringPolicy")
                    .addAttribute("interval", "1")
                    .addAttribute("modulate", true)  // 在午夜时滚动
            )

            policies.addComponent(
                builder.newComponent("SizeBasedTriggeringPolicy")
                    .addAttribute("size", "50 MB")
            )
            appender.addComponent(policies)

            // 添加 RolloverStrategy - 最多保留30个文件（约30天）
            val strategy = builder.newComponent("DefaultRolloverStrategy")
                .addAttribute("max", "30")  // 最多保留30个归档文件
                .addAttribute("fileIndex", "min")  // 从最小索引开始删除
                .addAttribute("compressionLevel", "9")  // 最高压缩级别
            appender.addComponent(strategy)

            return appender
        }

        private fun setDynamicLogLevel() {
            try {
                val logLevelStr = SettingStore.getSettingItem("log")
                val level = Level.valueOf(logLevelStr.uppercase())
                Configurator.setRootLevel(level)
                println("Dynamic log level set to: $level")
            } catch (e: Exception) {
                println("Failed to set dynamic log level, using default INFO. Error: ${e.message}")
            }
        }

        /**
         * 创建自定义 PrintStream，将 System.out 重定向到日志
         */
        fun createMyPrintStream(printStream: PrintStream): PrintStream {
            return object : PrintStream(printStream) {
                override fun print(string: String) {
                    synchronized(this) {
                        log.info(string)
                    }
                }
            }
        }
    }
}