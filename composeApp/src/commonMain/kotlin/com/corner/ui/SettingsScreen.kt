package com.corner.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.WindowScope
import com.corner.service.player.PlayerType
import com.corner.catvodcore.config.ApiConfig
import com.corner.catvodcore.enum.ConfigType
import com.corner.catvodcore.viewmodel.SiteViewModel
import com.corner.util.AppVersion
import com.corner.util.io.Paths
import com.corner.catvodcore.viewmodel.GlobalAppState.hideProgress
import com.corner.catvodcore.viewmodel.GlobalAppState.showProgress
import com.corner.database.Db
import com.corner.database.entity.Config
import com.corner.init.Init.Companion.initConfig
import com.corner.ui.nav.vm.SettingViewModel
import com.corner.ui.player.vlcj.VlcJInit
import com.corner.ui.scene.*
import com.corner.util.getSetting
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import lumentv_compose.composeapp.generated.resources.Res
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.net.URI
import androidx.compose.runtime.collectAsState
import com.corner.util.net.Http
import com.corner.catvodcore.viewmodel.GlobalAppState
import com.corner.util.settings.SettingEnable
import com.corner.util.settings.SettingStore
import com.corner.util.settings.SettingType
import com.corner.util.settings.getPlayerSetting
import com.corner.util.settings.parseAsSettingEnable
import com.corner.util.m3u8.M3U8FilterConfig
import com.github.catvod.bean.Doh
import lumentv_compose.composeapp.generated.resources.LumenTV_icon_svg
import org.slf4j.LoggerFactory
import kotlin.math.roundToInt

private val log = LoggerFactory.getLogger("SettingsScreen")

enum class SettingsCategory(
    val title: String,
    val icon: ImageVector
) {
    GENERAL("常规", Icons.Filled.Settings),
    VOD("点播", Icons.Filled.LiveTv),
    PLAYER("播放器", Icons.Filled.PlayCircle),
    NETWORK("网络", Icons.Filled.SystemUpdate),
    PLAYWRIGHT("自动化", Icons.Filled.Web),
    ADVANCED("高级", Icons.Filled.Code),
    ABOUT("关于", Icons.Filled.Info),
}

@Composable
fun WindowScope.SettingScene(vm: SettingViewModel, config: M3U8FilterConfig, onClickBack: () -> Unit) {
    val model = vm.state.collectAsState()
    val isDarkTheme by GlobalAppState.isDarkTheme.collectAsState()
    val filterConfig = remember { mutableStateOf(SettingStore.getM3U8FilterConfig()) }
    val isAdFilterEnabled by remember { mutableStateOf(SettingStore.isAdFilterEnabled()) }
    var adFilterChecked by remember { mutableStateOf(isAdFilterEnabled) }
    var showRestartDialog by remember { mutableStateOf(false) }
    val updateCheckState by vm.updateCheckState.collectAsState()

    var selectedCategory by remember { mutableStateOf(SettingsCategory.GENERAL) }

    DisposableEffect("setting") {
        vm.sync()
        onDispose {
            log.info("设置已保存：{}", model.value.settingList.joinToString(", "))
            SettingStore.write()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxSize()) {
            WindowDraggableArea(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                ControlBar(
                    leading = {
                        BackRow(modifier = Modifier.align(Alignment.Start), { onClickBack() }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "设置",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.15.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        FilledTonalButton(
                            onClick = {
                                Desktop.getDesktop().open(Paths.logPath())
                            },
                            modifier = Modifier.padding(end = 8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp),
                            elevation = ButtonDefaults.filledTonalButtonElevation(
                                defaultElevation = 2.dp,
                                pressedElevation = 4.dp
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = "日志目录",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    "日志目录",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                        // 数据目录按钮
                        FilledTonalButton(
                            onClick = { Desktop.getDesktop().open(Paths.userDataRoot()) },
                            modifier = Modifier.padding(end = 16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp),
                            elevation = ButtonDefaults.filledTonalButtonElevation(
                                defaultElevation = 2.dp,
                                pressedElevation = 4.dp
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "数据目录",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "数据目录",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Row(Modifier.fillMaxSize()) {
                SettingsNavigationRail(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it },
                    modifier = Modifier.width(90.dp)
                        .fillMaxHeight()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)
                )

                VerticalDivider(
                    modifier = Modifier.fillMaxHeight(),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                AnimatedContent(
                    targetState = selectedCategory,
                    transitionSpec = {
                        slideInHorizontally { fullWidth -> fullWidth } togetherWith
                                slideOutHorizontally { fullWidth -> -fullWidth }
                    },
                    label = "settings_category_transition"
                ) { category ->
                    when (category) {
                        SettingsCategory.GENERAL -> GeneralSettingsContent(
                            vm = vm,
                            model = model,
                            isDarkTheme = isDarkTheme,
                            adFilterChecked = adFilterChecked,
                            onAdFilterChange = {
                                adFilterChecked = it
                                SettingStore.setAdFilterEnabled(it)
                                vm.sync()
                            },
                            filterConfig = filterConfig,
                            showRestartDialog = { showRestartDialog = true },
                            modifier = Modifier.fillMaxSize()
                        )

                        SettingsCategory.VOD -> VodSettingsContent(
                            vm = vm,
                            model = model,
                            modifier = Modifier.fillMaxSize()
                        )

                        SettingsCategory.PLAYER -> PlayerSettingsContent(
                            vm = vm,
                            model = model,
                            modifier = Modifier.fillMaxSize()
                        )

                        SettingsCategory.NETWORK -> NetworkSettingsContent(
                            vm = vm,
                            model = model,
                            updateCheckState = updateCheckState,
                            modifier = Modifier.fillMaxSize()
                        )

                        SettingsCategory.PLAYWRIGHT -> PlaywrightSettingsContent(
                            modifier = Modifier.fillMaxSize()
                        )

                        SettingsCategory.ADVANCED -> AdvancedSettingsContent(
                            vm = vm,
                            model = model,
                            modifier = Modifier.fillMaxSize()
                        )

                        SettingsCategory.ABOUT -> AboutSettingsContent(
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }



    if (showRestartDialog) {
        SnackBar.postMsg("重启生效", type = SnackBar.MessageType.INFO)
        showRestartDialog = false
    }
}

@Composable
fun SettingsNavigationRail(
    selectedCategory: SettingsCategory,
    onCategorySelected: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        SettingsCategory.entries.forEach { category ->
            NavigationRailItem(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                icon = {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = category.title
                    )
                },
                label = {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }
    }
}

@Composable
fun GeneralSettingsContent(
    vm: SettingViewModel,
    model: State<com.corner.ui.nav.data.SettingScreenState>,
    isDarkTheme: Boolean,
    adFilterChecked: Boolean,
    onAdFilterChange: (Boolean) -> Unit,
    filterConfig: MutableState<M3U8FilterConfig>,
    showRestartDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingCard(
                title = "主题设置",
                icon = Icons.Default.Palette
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isDarkTheme) "当前主题：暗色模式" else "当前主题：亮色模式",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = {
                            GlobalAppState.isDarkTheme.value = it
                            try {
                                SettingStore.setValue(SettingType.THEME, if (it) "dark" else "light")
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }

        item {
            SettingCard(
                title = "广告过滤设置",
                icon = Icons.Default.Block
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (adFilterChecked) "广告过滤：开启" else "广告过滤：关闭",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = adFilterChecked,
                        onCheckedChange = onAdFilterChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }

                if (adFilterChecked) {
                    var tsNameLenExtend: Int by remember { mutableStateOf(filterConfig.value.tsNameLenExtend) }
                    var theExtinfBenchmarkN: Int by remember { mutableStateOf(filterConfig.value.theExtinfBenchmarkN) }
                    var violentFilterModeFlag by remember { mutableStateOf(filterConfig.value.violentFilterModeFlag) }

                    LaunchedEffect(filterConfig.value.tsNameLenExtend) {
                        tsNameLenExtend = filterConfig.value.tsNameLenExtend
                    }
                    LaunchedEffect(filterConfig.value.theExtinfBenchmarkN) {
                        theExtinfBenchmarkN = filterConfig.value.theExtinfBenchmarkN
                    }
                    LaunchedEffect(filterConfig.value.violentFilterModeFlag) {
                        violentFilterModeFlag = filterConfig.value.violentFilterModeFlag
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("TS 前缀长度容错值")
                            Text(
                                text = "$tsNameLenExtend (默认: 1)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Slider(
                            value = tsNameLenExtend.toFloat(),
                            onValueChange = { newValue ->
                                val newInt = newValue.roundToInt()
                                val clampedValue = newInt.coerceIn(0, 5)
                                tsNameLenExtend = clampedValue
                                filterConfig.value = filterConfig.value.copy(tsNameLenExtend = clampedValue)
                                SettingStore.setM3U8FilterConfig(filterConfig.value)
                                showRestartDialog()
                            },
                            valueRange = 0f..5f,
                            steps = 4
                        )
                        Text(
                            text = "用于匹配TS文件名的前缀长度容错。当TS文件名与预期模式不完全匹配时，允许的前缀长度偏差值。设为0表示严格匹配，增大可提高容错能力。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("EXTINF 基准值")
                            Text(
                                text = "${theExtinfBenchmarkN.toInt()} (默认: 5)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Slider(
                            value = theExtinfBenchmarkN.toFloat(),
                            onValueChange = { newValue ->
                                val newInt = newValue.roundToInt()
                                val clampedValue = newInt.coerceIn(1, 10)
                                theExtinfBenchmarkN = newInt
                                filterConfig.value = filterConfig.value.copy(theExtinfBenchmarkN = clampedValue)
                                SettingStore.setM3U8FilterConfig(filterConfig.value)
                                showRestartDialog()
                            },
                            valueRange = 1f..10f,
                            steps = 8
                        )
                        Text(
                            text = "相同描述行阈值：用于判断是否进入广告段。若连续相同的 #EXTINF 行数超过此值，将触发广告过滤逻辑。默认值通常为 3~5。若正常内容被误判为广告，可调大；若广告漏过，可调小。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("暴力拆解模式")
                            Text(
                                text = if (violentFilterModeFlag) "开启" else "关闭",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = violentFilterModeFlag,
                            onCheckedChange = {
                                violentFilterModeFlag = it
                                filterConfig.value.violentFilterModeFlag = it
                                SettingStore.setM3U8FilterConfig(filterConfig.value)
                                showRestartDialog()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                        Text(
                            text = "暴力过滤模式：开启后将直接移除所有 #EXT-X-DISCONTINUITY 行（常用于广告插入点）。适用于复杂广告场景，但可能导致正常内容丢失（如节目切换）。仅在普通模式无法过滤广告时启用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        item {
            var showConfirmDialog by remember { mutableStateOf(false) }

            Button(
                onClick = { showConfirmDialog = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("重置所有设置", style = MaterialTheme.typography.labelLarge)
            }

            if (showConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showConfirmDialog = false },
                    title = { Text("确认重置") },
                    text = { Text("您确定要重置所有设置吗？此操作无法撤销。") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                SettingStore.reset()
                                vm.sync()
                                GlobalAppState.isDarkTheme.value =
                                    SettingStore.getSettingItem(SettingType.THEME) == "dark"
                                SnackBar.postMsg("重置设置,重启生效", type = SnackBar.MessageType.INFO)
                                showConfirmDialog = false
                            }
                        ) {
                            Text("确认")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showConfirmDialog = false }
                        ) {
                            Text("取消")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun VodSettingsContent(
    vm: SettingViewModel,
    model: State<com.corner.ui.nav.data.SettingScreenState>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingCard(title = "点播源配置", icon = Icons.Default.LiveTv) {
                val focusRequester = remember { FocusRequester() }
                val isExpand = remember { mutableStateOf(false) }
                val setting = derivedStateOf { model.value.settingList.getSetting(SettingType.VOD) }
                val vodConfigList = derivedStateOf { model.value.dbConfigList }

                var textValue by remember { mutableStateOf(setting.value?.value ?: "") }

                LaunchedEffect(setting.value?.value) {
                    setting.value?.value?.let {
                        if (textValue != it) textValue = it
                    }
                }

                LaunchedEffect(isExpand.value) {
                    if (isExpand.value) {
                        vm.getConfigAll()
                        focusRequester.requestFocus()
                        delay(100)
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxSize()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = textValue,
                                onValueChange = { newValue ->
                                    textValue = newValue
                                    SettingStore.setValue(SettingType.VOD, newValue)
                                    vm.sync()
                                },
                                label = { Text("输入点播源地址") },
                                singleLine = true,
                                modifier = Modifier
                                    .focusRequester(focusRequester)
                                    .weight(1f)
                                    .onFocusEvent { isExpand.value = it.isFocused },
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Done,
                                    keyboardType = KeyboardType.Uri
                                ),
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    Row {
                                        if (textValue.isNotEmpty()) {
                                            IconButton(
                                                onClick = {
                                                    textValue = ""
                                                    SettingStore.setValue(SettingType.VOD, "")
                                                    vm.sync()
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    "清空",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                                try {
                                                    val text = clipboard.getData(DataFlavor.stringFlavor) as? String
                                                    text?.let {
                                                        textValue = it
                                                        SettingStore.setValue(SettingType.VOD, it)
                                                        vm.sync()
                                                    }
                                                } catch (e: Exception) {
                                                    log.error("粘贴失败: ${e.message}")
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.ContentPaste,
                                                "粘贴",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            )

                            Button(
                                onClick = { setConfig(textValue) },
                                modifier = Modifier.height(60.dp).padding(top = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("确定")
                            }

                            DropdownMenu(
                                isExpand.value,
                                { isExpand.value = false },
                                modifier = Modifier.fillMaxWidth(0.8f),
                                properties = PopupProperties(focusable = false)
                            ) {
                                vodConfigList.value.forEach {
                                    DropdownMenuItem(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = { Text(it.url ?: "") },
                                        onClick = {
                                            setConfig(it.url)
                                            isExpand.value = false
                                        },
                                        trailingIcon = {
                                            IconButton(onClick = {
                                                vm.deleteHistoryById(it)
                                            }) {
                                                Icon(Icons.Default.Close, "delete the config")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "需要配置点播源才能获取到视频内容\n" +
                                " \n" +
                                "格式：\n" +
                                "file://C:\\\\json\\\\config.json \n" +
                                "或\n" +
                                "http://example.com/config.json \n",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

// 单选按钮行组件
@Composable
fun RadioButtonRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary
            )
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun PlayerSettingsContent(
    vm: SettingViewModel,
    model: State<com.corner.ui.nav.data.SettingScreenState>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingCard(
                title = "播放器设置",
                icon = Icons.Default.PlayCircle
            ) {
                val playerSetting = derivedStateOf {
                    val arr = model.value.settingList.getSetting(SettingType.PLAYER)
                        ?.value?.getPlayerSetting()?.toMutableList()
                        ?: mutableListOf(PlayerType.Innie.id, "")

                    if (listOf("true", "false").contains(arr[0])) {
                        if (arr[0].toBoolean()) {
                            arr[0] = PlayerType.Innie.id
                        } else {
                            arr[0] = PlayerType.Outie.id
                        }
                        SettingStore.setValue(SettingType.PLAYER, "${arr.first()}#${arr[1]}")
                    }
                    arr
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PlayerType.entries.forEach { type ->
                            AssistChip(
                                onClick = {
                                    SettingStore.setValue(
                                        SettingType.PLAYER,
                                        "${type.id}#${playerSetting.value[1]}"
                                    )
                                    when (type.id) {
                                        PlayerType.Innie.id -> SnackBar.postMsg(
                                            "使用内置播放器",
                                            type = SnackBar.MessageType.INFO
                                        )

                                        PlayerType.Outie.id -> {
                                            if (playerSetting.value[1].isBlank()) {
                                                SnackBar.postMsg(
                                                    "已切换到外部播放器，请配置播放器路径",
                                                    type = SnackBar.MessageType.WARNING
                                                )
                                            } else {
                                                SnackBar.postMsg("使用外部播放器", type = SnackBar.MessageType.INFO)
                                            }
                                        }

                                        PlayerType.Web.id -> SnackBar.postMsg(
                                            "使用浏览器播放器",
                                            type = SnackBar.MessageType.INFO
                                        )
                                    }
                                    vm.sync()
                                },
                                label = { Text(type.display) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (playerSetting.value.first() == type.id) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    labelColor = if (playerSetting.value.first() == type.id) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                ),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    var isPathValid by remember { mutableStateOf(true) }
                    var showPathWarning by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = playerSetting.value[1],
                        onValueChange = {
                            isPathValid = it.isNotBlank()
                            SettingStore.setValue(SettingType.PLAYER, "${playerSetting.value.first()}#$it")
                            SiteViewModel.viewModelScope.launch {
                                if (playerSetting.value.first() == PlayerType.Innie.id) {
                                    if (File(it).exists()) {
                                        VlcJInit.init(true)
                                    }
                                }
                            }
                            vm.sync()
                            SnackBar.postMsg("播放器路径更新为：$it", type = SnackBar.MessageType.INFO)
                            showPathWarning = false
                        },
                        label = { Text("播放器路径") },
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = playerSetting.value.first() == PlayerType.Outie.id,
                        isError = !isPathValid || showPathWarning,
                        supportingText = {
                            if (!isPathValid || showPathWarning) {
                                Text("请输入外置播放器路径！", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )

                    if (playerSetting.value.first() == PlayerType.Outie.id) {
                        Button(
                            onClick = {
                                if (playerSetting.value[1].isBlank()) {
                                    showPathWarning = true
                                    SnackBar.postMsg("请先配置外部播放器路径！", type = SnackBar.MessageType.ERROR)
                                } else {
                                    val file = File(playerSetting.value[1])
                                    if (file.exists() && file.canExecute()) {
                                        SnackBar.postMsg("播放器路径有效", type = SnackBar.MessageType.INFO)
                                    } else {
                                        SnackBar.postMsg(
                                            "播放器路径无效或不可执行",
                                            type = SnackBar.MessageType.ERROR
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = playerSetting.value.first() == PlayerType.Outie.id
                        ) {
                            Text("验证播放器路径")
                        }
                    }

                    Text(
                        text = "播放器可配置为内部播放器、外部播放器或浏览器播放器;如果选择外部播放器,需要配置外置播放器路径才能播放视频",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        item {
            SettingCard(
                title = "迷你进度条",
                icon = Icons.Default.Info
            ) {
                val miniProgressBarEnabled = remember {
                    mutableStateOf(SettingStore.getSettingItem(SettingType.MINI_PROGRESS_BAR).toBoolean())
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (miniProgressBarEnabled.value) "迷你进度条：开启" else "迷你进度条：关闭",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "在播放页面底部常驻显示迷你进度条，方便随时查看播放进度",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Switch(
                            checked = miniProgressBarEnabled.value,
                            onCheckedChange = { enabled ->
                                miniProgressBarEnabled.value = enabled
                                SettingStore.setValue(SettingType.MINI_PROGRESS_BAR, enabled.toString())
                                vm.sync()
                                SnackBar.postMsg(
                                    if (enabled) "迷你进度条已开启" else "迷你进度条已关闭",
                                    type = SnackBar.MessageType.INFO
                                )
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NetworkSettingsContent(
    vm: SettingViewModel,
    model: State<com.corner.ui.nav.data.SettingScreenState>,
    updateCheckState: com.corner.ui.nav.vm.UpdateCheckState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingCard(
                title = "更新检查",
                icon = Icons.Default.SystemUpdate
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { vm.checkForUpdate() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !updateCheckState.isChecking,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        if (updateCheckState.isChecking) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text("检查中...")
                            }
                        } else {
                            Text("手动检查更新")
                        }
                    }

                    if (updateCheckState.hasUpdate && updateCheckState.latestVersion != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "发现新版本: ${updateCheckState.latestVersion}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Button(
                                onClick = {
                                    SnackBar.postMsg(
                                        "发现新版本 ${updateCheckState.latestVersion}，请重启应用进行更新",
                                        type = SnackBar.MessageType.INFO
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("立即更新")
                            }
                        }
                    }

                    if (updateCheckState.error != null) {
                        Text(
                            text = "检查失败: ${updateCheckState.error}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        item {
            SettingCard(
                title = "代理设置",
                icon = Icons.Default.Security
            ) {
                val proxySetting = derivedStateOf {
                    model.value.settingList.getSetting(SettingType.PROXY)
                        ?.value?.parseAsSettingEnable()
                        ?: SettingEnable.default()
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Switch(
                        checked = proxySetting.value.isEnabled,
                        onCheckedChange = { enabled ->
                            SettingStore.setValue(SettingType.PROXY, "$enabled#${proxySetting.value.value}")
                            vm.sync()
                            com.corner.util.net.ProxyManager.clearCache()

                            if (!enabled) {
                                com.corner.util.net.Http.client().dispatcher.executorService.shutdownNow()
                                com.github.catvod.net.OkHttp.clearClient()
                                SnackBar.postMsg("代理已关闭，网络连接将立即生效", type = SnackBar.MessageType.INFO)
                            } else {
                                SnackBar.postMsg(
                                    "代理已开启，部分功能可能需要重启后完全生效",
                                    type = SnackBar.MessageType.INFO
                                )
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    OutlinedTextField(
                        value = proxySetting.value.value,
                        onValueChange = {
                            SettingStore.setValue(SettingType.PROXY, "${proxySetting.value.isEnabled}#$it")
                            vm.sync()
                            com.corner.util.net.ProxyManager.clearCache()
                        },
                        label = { Text("代理地址") },
                        placeholder = { Text("例如: http://127.0.0.1:7890") },
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                        enabled = proxySetting.value.isEnabled,
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (proxySetting.value.isEnabled && proxySetting.value.value.isNotBlank()) {
                        IconButton(
                            onClick = {
                                testProxyConnection(proxySetting.value.value)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "测试代理",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        item {
            SettingCard(
                title = "DNS over HTTPS 设置",
                icon = Icons.Default.Security
            ) {
                val dohEnabled = remember {
                    mutableStateOf(SettingStore.getSettingItem(SettingType.DOH_ENABLED).toBoolean())
                }
                val dohServer = remember {
                    mutableStateOf(SettingStore.getSettingItem(SettingType.DOH_SERVER))
                }
                val dohServers = Doh.defaultDoh().filter { it.name != "System" }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (dohEnabled.value) "DoH：开启" else "DoH：关闭",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = dohEnabled.value,
                            onCheckedChange = { enabled ->
                                dohEnabled.value = enabled
                                SettingStore.setValue(SettingType.DOH_ENABLED, enabled.toString())
                                applyDohSetting(enabled, dohServer.value)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }

                    if (dohEnabled.value) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Text(
                                text = "DoH 服务器",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            dohServers.forEach { server ->
                                RadioButtonRow(
                                    text = server.name,
                                    selected = dohServer.value == server.name,
                                    onClick = {
                                        dohServer.value = server.name
                                        SettingStore.setValue(SettingType.DOH_SERVER, server.name)
                                        applyDohSetting(true, server.name)
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        text = "DNS over HTTPS (DoH) 可以提高DNS查询的安全性和隐私性。开启后，DNS查询将通过HTTPS加密传输。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AdvancedSettingsContent(
    vm: SettingViewModel,
    model: State<com.corner.ui.nav.data.SettingScreenState>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingCard(
                title = "日志级别",
                icon = Icons.AutoMirrored.Filled.ListAlt
            ) {
                val current = derivedStateOf {
                    model.value.settingList.getSetting(SettingType.LOG)?.value ?: logLevel[0]
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        logLevel.forEach { level ->
                            FilterChip(
                                selected = level == current.value,
                                leadingIcon = if (current.value == level) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Done,
                                            contentDescription = "Done icon",
                                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                } else {
                                    null
                                },
                                onClick = {
                                    SettingStore.setValue(SettingType.LOG, level)
                                    vm.sync()
                                    SnackBar.postMsg("重启生效", type = SnackBar.MessageType.INFO)
                                },
                                label = { Text(level) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                    Text(
                        text = "日志级别用于记录应用运行时的信息和错误,默认级别为DEBUG;使用DEBUG级别可能会导致日志文件变大",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        item {
            SettingCard(
                title = "爬虫搜索词设置",
                icon = Icons.Default.Search
            ) {
                val crawlerSearchTerms = remember {
                    mutableStateOf(
                        model.value.settingList.getSetting(SettingType.CRAWLER_SEARCH_TERMS)?.value ?: ""
                    )
                }

                OutlinedTextField(
                    value = crawlerSearchTerms.value,
                    onValueChange = { newValue ->
                        crawlerSearchTerms.value = newValue
                        SettingStore.setValue(SettingType.CRAWLER_SEARCH_TERMS, newValue)
                        vm.sync()
                    },
                    label = { Text("搜索模式搜索词") },
                    placeholder = { Text("请输入搜索词") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    text = "用于爬虫可用性功能的搜索模式搜索词，默认为\"阿甘正传\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        item {
            SettingCard(
                title = "FPS 监控",
                icon = Icons.Default.Info
            ) {
                val fpsMonitorEnabled = remember {
                    mutableStateOf(SettingStore.getSettingItem(SettingType.FPS_MONITOR).toBoolean())
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (fpsMonitorEnabled.value) "FPS 监控：开启" else "FPS 监控：关闭",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "在屏幕左上角显示当前帧率和系统信息，用于性能调试",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Switch(
                            checked = fpsMonitorEnabled.value,
                            onCheckedChange = { enabled ->
                                fpsMonitorEnabled.value = enabled
                                SettingStore.setValue(SettingType.FPS_MONITOR, enabled.toString())
                                vm.sync()
                                SnackBar.postMsg(
                                    if (enabled) "FPS 监控已开启" else "FPS 监控已关闭",
                                    type = SnackBar.MessageType.INFO
                                )
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AboutSettingsContent(
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    LazyColumn(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Logo and App Info Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // App Logo
                    Image(
                        painter = painterResource(Res.drawable.LumenTV_icon_svg),
                        contentDescription = "App Logo",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .border(
                                width = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ),
                        contentScale = ContentScale.Crop
                    )
                    
                    // App Name and Version
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "LumenTV Compose",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Text(
                            text = "版本 ${AppVersion.VERSION_NAME}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                    
                    // App Description
                    Text(
                        text = "一个基于 Kotlin Multiplatform 和 Jetpack Compose 构建的现代化Catvod播放器",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        // Development Team Section
        item {
            SettingCard(
                title = "开发团队",
                icon = Icons.Default.Group
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Developer 1
                    AboutTeamMemberItem(
                        name = "Clevebitr",
                        role = "该版本开发者",
                        link = "https://github.com/clevebitr"
                    )
                    
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                    
                    // Developer 2
                    AboutTeamMemberItem(
                        name = "Greatwallcorner",
                        role = "主要开发者",
                        link = "https://github.com/Greatwallcorner"
                    )
                    
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                    
                    // Contributors
                    AboutTeamMemberItem(
                        name = "贡献者",
                        role = "开源社区",
                        link = "https://github.com/Greatwallcorner/TV-Multiplatform/graphs/contributors"
                    )
                }
            }
        }
        
        // Links Section
        item {
            SettingCard(
                title = "相关链接",
                icon = Icons.AutoMirrored.Filled.OpenInNew
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Telegram Group
                    FilledTonalButton(
                        onClick = { openBrowser("https://t.me/tv_multiplatform") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Telegram 群组",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "加入原项目讨论",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "打开链接",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    // This Version Source Code
                    FilledTonalButton(
                        onClick = { openBrowser("https://github.com/Clevebitr/TV-Multiplatform") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Code,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "该版本源代码",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "查看 Clevebitr 分支",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "打开链接",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    // Original Project Source Code
                    FilledTonalButton(
                        onClick = { openBrowser("https://github.com/Greatwallcorner/TV-Multiplatform") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Code,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "原项目源代码",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "查看 Greatwallcorner 主仓库",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "打开链接",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
        
        // License Section
        item {
            SettingCard(
                title = "开源协议",
                icon = Icons.Default.Security
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "本项目采用 GPL-3.0 开源协议",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FilledTonalButton(
                        onClick = { openBrowser("https://github.com/clevebitr/LumenTV-Compose/blob/main/LICENSE") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text("查看开源协议")
                    }
                }
            }
        }
    }
}

// 设置项卡片组件
@Composable
fun SettingCard(
    title: String,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Divider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                thickness = 1.dp
            )

            content()
        }
    }
}

fun SettingStore.getPlayerSetting(): List<Any> {
    val settingItem = getSettingItem(SettingType.PLAYER.id)
    val split = settingItem.split("#")
    return if (split.size == 1) listOf(false, settingItem) else listOf(split[0].toBoolean(), split[1])
}

private val logLevel = listOf("INFO", "DEBUG")

enum class SideButtonType {
    LEFT, MID, RIGHT
}

@Suppress("unused")
@Composable
fun SideButton(
    choosen: Boolean,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors()
        .copy(disabledContainerColor = MaterialTheme.colorScheme.background),
    type: SideButtonType = SideButtonType.MID,
    text: String,
    onClick: (String) -> Unit
) {
    val textColor = if (choosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
    Text(text = text, modifier = Modifier.clickable { onClick(text) }
        .defaultMinSize(50.dp)
        .drawWithCache {
            val width = size.width + 5
            val height = size.height + 5
            val color = if (choosen) buttonColors.containerColor else buttonColors.disabledContainerColor

            onDrawBehind {
                val rectOffset = when (type) {
                    SideButtonType.LEFT -> Offset(height / 2, 0f)
                    SideButtonType.MID -> Offset.Zero
                    SideButtonType.RIGHT -> Offset.Zero
                }
                if (type == SideButtonType.LEFT) {
                    drawCircle(
                        color = color,
                        radius = height / 2,
                        center = Offset(height / 2, height / 2),
                        style = Fill
                    )
                }
                val rectSize = Size(width - height / 2, height)
                drawRect(
                    color = color,
                    topLeft = rectOffset,
                    size = rectSize,
                    style = Fill,
                )
                if (type == SideButtonType.RIGHT) {
                    drawCircle(
                        color = color,
                        radius = height / 2,
                        center = Offset(size.width - height / 2, height / 2),
                        style = Fill
                    )
                }
            }
        }, textAlign = TextAlign.Center, color = textColor)
}

fun setConfig(textFieldValue: String?) {
    showProgress()
    SiteViewModel.viewModelScope.launch {
        if (textFieldValue == null || textFieldValue == "") {
            SnackBar.postMsg("点播源地址不可为空", type = SnackBar.MessageType.ERROR)
            return@launch
        }
        SettingStore.setValue(SettingType.VOD, textFieldValue)
        val config = Db.Config.find(textFieldValue, ConfigType.SITE.ordinal.toLong()).firstOrNull()
        if (config == null) {
            Db.Config.save(
                Config(
                    type = ConfigType.SITE.ordinal.toLong(),
                    url = textFieldValue
                )
            )
        } else {
            Db.Config.updateUrl(config.id, textFieldValue)
        }

        ApiConfig.api.cfg = Db.Config.find(textFieldValue, ConfigType.SITE.ordinal.toLong()).firstOrNull()
        initConfig(true)
    }.invokeOnCompletion {
        hideProgress()
    }
}

/**
 * 测试代理连接
 */
private fun testProxyConnection(proxyUrl: String) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            val uri = java.net.URI.create(proxyUrl)
            val address = java.net.InetSocketAddress(uri.host, uri.port)
            val socket = java.net.Socket()

            SnackBar.postMsg("正在测试代理连接...", type = SnackBar.MessageType.INFO)

            // 设置超时时间为3秒
            socket.connect(address, 3000)
            socket.close()

            SnackBar.postMsg(
                "代理连接测试成功！\n地址: $proxyUrl",
                type = SnackBar.MessageType.SUCCESS
            )
        } catch (e: Exception) {
            SnackBar.postMsg(
                "代理连接测试失败！\n地址: $proxyUrl\n错误: ${e.message}\n\n请检查：\n1. 代理服务器是否启动\n2. 代理地址是否正确\n3. 防火墙是否阻止连接",
                type = SnackBar.MessageType.ERROR
            )
        }
    }
}

// 关于页面团队成员项
@Composable
fun AboutTeamMemberItem(
    name: String,
    role: String,
    link: String
) {
    ElevatedButton(
        onClick = { openBrowser(link) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 1.dp,
            pressedElevation = 3.dp
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left side: Avatar and text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = role,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            
            // Right side: Link icon
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "打开链接",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

fun openBrowser(url: String) {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI(url))
    }
}

fun applyDohSetting(enabled: Boolean, serverName: String) {
    if (enabled) {
        val doh = Doh.defaultDoh().find { it.name == serverName }
        doh?.let {
            Http.setDoh(it) // Apply the DoH setting to Http
            SnackBar.postMsg("已启用DoH: $serverName", type = SnackBar.MessageType.INFO)
        }
    } else {
        // 禁用DoH，重置为系统默认DNS
        resetDohSetting()
        SnackBar.postMsg("已禁用DoH", type = SnackBar.MessageType.INFO)
    }
}

fun resetDohSetting() {
    // 通过反射或添加方法来重置DoH设置
    Http.resetDoh()
}

/**
 * Playwright 浏览器设置页面
 */
@Composable
fun PlaywrightSettingsContent(
    modifier: Modifier = Modifier
) {
    var isBrowserAvailable by remember { mutableStateOf(false) }
    var browserPath by remember { mutableStateOf("") }
    var cacheDir by remember { mutableStateOf("") }
    var tempDir by remember { mutableStateOf("") }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    
    // 更新浏览器状态的函数
    fun updateBrowserStatus() {
        isBrowserAvailable = com.corner.util.playwright.PlaywrightBrowserManager.isBrowserAvailable()
        browserPath = com.corner.util.playwright.PlaywrightBrowserManager.getBrowserExecutablePath()
        cacheDir = com.corner.util.playwright.PlaywrightBrowserManager.getBrowserCacheDir()
        tempDir = com.corner.util.playwright.PlaywrightBrowserManager.getTempDir()
    }
    
    // 加载浏览器状态
    LaunchedEffect(Unit) {
        updateBrowserStatus()
    }
    
    fun startDownload() {
        showDownloadDialog = false
        isDownloading = true
        downloadProgress = 0f
        
        scope.launch {
            val result = com.corner.util.playwright.PlaywrightBrowserManager.ensureBrowserDownloaded {
                progress ->
                downloadProgress = progress.toFloat()
            }
            
            isDownloading = false
            if (result.isSuccess) {
                SnackBar.postMsg("浏览器下载成功", type = SnackBar.MessageType.SUCCESS)
                updateBrowserStatus()
            } else {
                SnackBar.postMsg("浏览器下载失败: ${result.exceptionOrNull()?.message}", type = SnackBar.MessageType.ERROR)
            }
        }
    }
    
    fun clearCache() {
        scope.launch {
            val success = com.corner.util.playwright.PlaywrightBrowserManager.clearBrowserCache()
            if (success) {
                SnackBar.postMsg("缓存已清除", type = SnackBar.MessageType.SUCCESS)
                updateBrowserStatus()
            } else {
                SnackBar.postMsg("清除缓存失败", type = SnackBar.MessageType.ERROR)
            }
            showClearCacheDialog = false
        }
    }
    
    LazyColumn(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingCard(
                title = "Playwright 浏览器状态",
                icon = Icons.Default.Web
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 状态指示器
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "浏览器状态",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Badge(
                            containerColor = if (isBrowserAvailable) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = if (isBrowserAvailable) "已安装" else "未安装",
                                color = if (isBrowserAvailable)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    
                    HorizontalDivider()
                    
                    // 浏览器路径
                    if (isBrowserAvailable) {
                        InfoRow(label = "浏览器路径", value = browserPath)
                        InfoRow(label = "缓存目录", value = cacheDir)
                        InfoRow(label = "临时目录", value = tempDir)
                    } else {
                        Text(
                            text = "Playwright 浏览器尚未安装。某些爬虫需要浏览器才能正常工作。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 操作按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!isBrowserAvailable) {
                            Button(
                                onClick = { showDownloadDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("下载浏览器")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showDownloadDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("重新下载")
                            }
                            
                            OutlinedButton(
                                onClick = { showClearCacheDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("清除缓存")
                            }
                        }
                    }
                }
            }
        }
        
        item {
            SettingCard(
                title = "使用说明",
                icon = Icons.Default.Info
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "• Playwright 是一个浏览器自动化工具，用于访问需要 JavaScript 渲染的网站",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• 首次使用时需要下载 Chromium 浏览器（约 150MB）",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• 如果浏览器损坏或需要更新，可以使用“重新下载”功能",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• 清除缓存会删除所有下载的浏览器文件，下次使用时需要重新下载",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
    
    // 下载确认对话框
    if (showDownloadDialog && !isDownloading) {
        PlaywrightDownloadDialog(
            spiderName = "Playwright 浏览器",
            onConfirm = { startDownload() },
            onCancel = { showDownloadDialog = false }
        )
    }
    
    // 下载进度对话框
    if (isDownloading) {
        PlaywrightDownloadDialog(
            spiderName = "Playwright 浏览器",
            onConfirm = {},
            onCancel = { /* 暂不支持中断下载 */ },
            isDownloading = true,
            downloadProgress = downloadProgress
        )
    }
    
    // 清除缓存确认对话框
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("确认清除缓存") },
            text = {
                Text("此操作将删除所有 Playwright 浏览器文件。\n\n" +
                     "删除后，使用相关爬虫时需要重新下载浏览器。\n\n" +
                     "确定要继续吗？")
            },
            confirmButton = {
                Button(
                    onClick = { clearCache() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("确认清除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearCacheDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}