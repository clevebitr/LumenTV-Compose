import androidx.compose.foundation.DarkDefaultContextMenuRepresentation
import androidx.compose.foundation.LightDefaultContextMenuRepresentation
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cn.hutool.core.util.SystemPropsUtil
import com.corner.RootContent
import com.corner.bean.SettingStore
import com.corner.catvodcore.viewmodel.GlobalAppState
import com.corner.init.Init
import com.corner.init.generateImageLoader
import com.corner.ui.Util
import com.corner.util.SysVerUtil
import com.seiko.imageloader.LocalImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.resources.painterResource
import org.slf4j.LoggerFactory
import lumentv_compose.composeapp.generated.resources.Res
import lumentv_compose.composeapp.generated.resources.LumenTV_icon_png
import java.awt.Dimension


private val log = LoggerFactory.getLogger("main")

fun main() {
    launchErrorCatcher()
    printSystemInfo()
    Runtime.getRuntime().addShutdownHook(Thread {
        log.info("Shutdown started")
        try {
            // 设置超时防止卡死
            runBlocking {
                withTimeout(5000) {
                    //执行清理程序
                    Init.stop()
                }
            }
        } catch (e: Exception) {
            log.error("Shutdown failed", e)
        }
    })
//    System.setProperty("java.net.useSystemProxies", "true");
    application {
        val windowState = rememberWindowState(
            size = Util.getPreferWindowSize(600, 500), position = WindowPosition.Aligned(Alignment.Center)
        )
        GlobalAppState.windowState = windowState

        LaunchedEffect(Unit) {
            launch(Dispatchers.Default) {
                Init.start()
            }
        }

        val transparent = rememberUpdatedState(!SysVerUtil.isWin10())
        val scope = rememberCoroutineScope()

        val contextMenuRepresentation =
            if (isSystemInDarkTheme()) DarkDefaultContextMenuRepresentation else LightDefaultContextMenuRepresentation
        Window(
            onCloseRequest = ::exitApplication, icon = painterResource(Res.drawable.LumenTV_icon_png), title = "LumenTV",
            state = windowState,
            undecorated = true,
            transparent = false,
        ) {
            window.minimumSize = Dimension(700, 600)
            CompositionLocalProvider(
                LocalImageLoader provides remember { generateImageLoader() },
                LocalContextMenuRepresentation provides remember { contextMenuRepresentation },
            ) {
                RootContent(modifier = Modifier.fillMaxSize())
            }

            scope.launch {
                GlobalAppState.closeApp.collect {
                    if (it) {
                        try {
                            // 1. 隐藏窗口
                            window.isVisible = false

                            // 2. 保存设置
                            SettingStore.write()

                            // 3. 等待500ms确保清理完成
                            delay(500)
                        } catch (e: Exception) {
                            log.error("关闭应用异常", e)
                        } finally {
                            // 4. 退出应用
                            exitApplication()
                        }
                    }
                }
            }
        }


    }
}

fun printSystemInfo() {
    val s = StringBuilder("\n")
    val logo = """
            __                                  _______    __
           / /   __  ______ ___  ___  ____     /_  __/ |  / /
          / /   / / / / __ `__ \/ _ \/ __ \     / /  | | / / 
         / /___/ /_/ / / / / / /  __/ / / /    / /   | |/ /  
        /_____/\__,_/_/ /_/ /_/\___/_/ /_/    /_/    |___/  
    """.trimIndent()

    getSystemPropAndAppend("java.version", s)
    getSystemPropAndAppend("java.home", s)
    getSystemPropAndAppend("os.name", s)
    getSystemPropAndAppend("os.arch", s)
    getSystemPropAndAppend("os.version", s)
    getSystemPropAndAppend("user.dir", s)
    getSystemPropAndAppend("user.home", s)

    val yellow_bolo = "\u001b[33;1m"  // 33=黄色, 1=加粗
    val reset = "\u001b[0m"            // 重置颜色

    log.info("{}\n{}\n系统信息：{}{}", yellow_bolo, logo, s.toString(), reset)
}

private fun getSystemPropAndAppend(key: String, s: StringBuilder) {
    val v = SystemPropsUtil.get(key)
    if (v.isNotBlank()) {
        s.append(key).append(":").append(v).append("\n")
    }
}

private fun launchErrorCatcher() {
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        SnackBar.postMsg("未知异常， 请查看日志", type = SnackBar.MessageType.ERROR)
        log.error("未知异常", e)
        Init.stop()
    }
}