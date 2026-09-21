package com.capcut.capsub.data.model

import kotlin.random.Random
data class DeviceConfig(
    var aid: String = "359289",
    var appName: String = "CapCut",
    var appvr: String = "8.7.0",
    var versionName: String = "8.7.0",
    var versionCode: String = "8.7.0",
    var channel: String = "capcutpc_google",
    var devicePlatform: String = "mac",
    var deviceType: String = "MacBookPro17,4",
    var deviceBrand: String = "MacBookPro17,4",
    var osVersion: String = "15.7.4",
    var deviceId: String = "76471456455646328721",
    var iid: String = "76471456455646328721",
    var region: String = "VN",
    var loc: String = "VN",
    var lan: String = "vi-VN",
    var pf: String = "3",
    var tdid: String = "76471456455646328721"
) {
    /**
     * Tạo mới bộ ID ngẫu nhiên (20 chữ số) để làm mới danh tính thiết bị, tránh bị rate-limit.
     */
    fun randomize(): DeviceConfig {
        val newId = (1000000000000000000L + Random.nextLong(8999999999999999999L)).toString() + "1"
        this.deviceId = newId
        this.iid = newId
        this.tdid = newId
        return this
    }

    fun toQueryMap(includeRegion: Boolean = true): Map<String, String> {
        val map = mutableMapOf(
            "app_name" to appName,
            "device_type" to deviceType,
            "os_version" to osVersion,
            "channel" to channel,
            "version_name" to versionName,
            "device_brand" to deviceBrand,
            "device_id" to deviceId,
            "iid" to iid,
            "version_code" to versionCode,
            "device_platform" to devicePlatform,
            "aid" to aid
        )
        if (includeRegion) {
            map["region"] = region
        }
        return map
    }
}
