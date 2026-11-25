package com.inspiredandroid.kai.inference

import com.inspiredandroid.kai.getAppFilesDirectory
import java.io.File

actual fun getModelStorageDirectory(): String = getAppFilesDirectory() + "/litert_models"

actual fun getModelCacheDirectory(): String = System.getProperty("java.io.tmpdir") ?: getAppFilesDirectory()

private val operatingSystemBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
private val extendedOperatingSystemBean = operatingSystemBean as? com.sun.management.OperatingSystemMXBean

actual fun getAvailableMemoryBytes(): Long =
    desktopAvailableMemoryBytes(extendedOperatingSystemBean?.freeMemorySize)

actual fun getTotalMemoryBytes(): Long =
    desktopTotalMemoryBytes(extendedOperatingSystemBean?.totalMemorySize)

actual fun getAvailableDiskSpaceBytes(path: String): Long {
    var dir = File(path)
    while (!dir.exists()) {
        dir = dir.parentFile ?: return 0L
    }
    return dir.usableSpace
}

actual fun startDownloadNotificationService() {
    // No foreground service needed on desktop
}

actual fun stopDownloadNotificationService() {
    // No foreground service needed on desktop
}

actual fun updateDownloadNotificationProgress(percent: Int) {
    // No notification on desktop
}
