package com.corner.init

import com.corner.util.settings.SettingStore
import com.corner.util.settings.SettingType
import com.corner.util.core.Constants
import com.corner.util.system.SysVerUtil
import com.corner.util.trimBlankChar
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import uk.co.caprica.vlcj.factory.discovery.provider.DiscoveryDirectoryProvider
import java.io.File


class CustomDirectoryDiscovery:DiscoveryDirectoryProvider {
    private val log = LoggerFactory.getLogger("CustomDirectory")
    override fun priority(): Int {
        return 99999
    }


    override fun directories(): Array<String> {
        val arrayOf = mutableListOf<String>()
        System.getProperty(Constants.RES_PATH_KEY)?.run {
            log.debug("resPath: $this")
            arrayOf.add(this.trimBlankChar() + "/lib")
        }
        val debugPath = File(System.getProperty("user.dir")).resolve("src/desktopMain/appResources/${SysVerUtil.getOsArchName()}/lib")
        if(StringUtils.isNotBlank(debugPath.toString())){
            arrayOf.add(debugPath.toString())
        }
        val playerPath = SettingStore.getSettingItem(SettingType.PLAYER.id).split("#")
        if(playerPath.size > 1 && StringUtils.isNotBlank(playerPath[1])){
            val path = playerPath[1].trimBlankChar()
            if(!File(path).exists()) return arrayOf.toTypedArray()
            arrayOf.add(File(path).parent)
        }
        log.info("自定义vlc播放器路径：$arrayOf")
        return arrayOf.toTypedArray()
    }

    override fun supported(): Boolean {
        return true
    }
}