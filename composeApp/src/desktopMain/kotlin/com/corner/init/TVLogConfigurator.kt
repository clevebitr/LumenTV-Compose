package com.corner.init

import com.corner.bean.SettingStore
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

            // 应用配置
            val ctx = LogManager.getContext(false) as LoggerContext
            ctx.start(builder.build())

            // 设置动态日志级别
            setDynamicLogLevel()

            println("Log4j2 configured successfully.")
        }

        // 修复1：修改返回类型为 AppenderComponentBuilder
        private fun createConsoleAppender(builder: ConfigurationBuilder<BuiltConfiguration>): org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder {
            return builder.newAppender("Console", "CONSOLE")
                .addAttribute("target", "SYSTEM_OUT")
                .add(
                    builder.newLayout("PatternLayout")
                        .addAttribute("pattern", "%d{HH:mm:ss.SSS} %-5level [*%-15.15thread] *%-15.15logger{0} -> %msg%n")
                        .addAttribute("charset", "UTF-8")
                )
        }

        // 修复2：修改返回类型为 AppenderComponentBuilder
        private fun createFileAppender(builder: ConfigurationBuilder<BuiltConfiguration>): org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder {
            val logPath = Paths.logPath().toString()
            val fileName = "$logPath/TV.log"
            val filePattern = "$logPath/TV_%d{yyyy-MM-dd}.log"

            val appender = builder.newAppender("RollingFile", "RollingFile")
                .addAttribute("fileName", fileName)
                .addAttribute("filePattern", filePattern)
                .addAttribute("append", true)

            // 添加 Layout
            val layout = builder.newLayout("PatternLayout")
                .addAttribute("pattern", "%d %-5level [%thread] %logger{0}: %msg%n")
                .addAttribute("charset", "UTF-8")
            appender.add(layout)

            // 添加 Policies
            val policies = builder.newComponent("Policies")
            policies.addComponent(
                builder.newComponent("SizeBasedTriggeringPolicy")
                    .addAttribute("size", "50MB")
            )
            policies.addComponent(
                builder.newComponent("TimeBasedTriggeringPolicy")
                    .addAttribute("interval", "1")
                    .addAttribute("modulate", true)
            )
            appender.addComponent(policies)

            // 添加 RolloverStrategy
            val strategy = builder.newComponent("DefaultRolloverStrategy")
                .addAttribute("max", "5")
                .addAttribute("fileIndex", "min")
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