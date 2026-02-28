package com.corner.dlna

import org.jupnp.DefaultUpnpServiceConfiguration
import org.jupnp.UpnpServiceImpl
import org.jupnp.model.meta.LocalDevice
import org.jupnp.protocol.ProtocolFactory
import org.jupnp.registry.Registry

/**
 * DLNA服务
 */
class TVMUpnpService: UpnpServiceImpl(DefaultUpnpServiceConfiguration()) {
    private var localDevice: LocalDevice? = null

    /**
     * 创建注册中心
     */
    override fun createRegistry(protocolFactory: ProtocolFactory?): Registry {
        val registry = super.createRegistry(protocolFactory)
        localDevice = TVMDevice()
        registry.addDevice(localDevice)
        return registry
    }

    /**
     * 发送设备上线消息
     */
    fun sendAlive() {
        getProtocolFactory().createSendingNotificationAlive(localDevice).run()
    }
}