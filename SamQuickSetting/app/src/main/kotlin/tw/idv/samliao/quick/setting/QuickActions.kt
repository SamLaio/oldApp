package tw.idv.samliao.quick.setting

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.widget.Toast
import java.util.Locale

object QuickActions {
    const val ACTION_SHOW_PANEL = "tw.idv.samliao.quick.setting.SHOW_PANEL"
    private const val ACTION_TETHER_SETTINGS = "android.settings.TETHER_SETTINGS"
    private const val PREFS = "quick_actions"
    private const val KEY_TORCH_ON = "torch_on"

    fun run(context: Context, id: String): Boolean {
        DebugLog.write(context, "run action=$id")
        val result = runCatching {
            when (id) {
                "brightness" -> toggleBrightness(context)
                "volume" -> cycleVolume(context).let { true }
                "ringer" -> toggleRinger(context)
                "timeout" -> cycleScreenTimeout(context)
                "rotation" -> toggleAutoRotate(context)
                "sync" -> toggleAutoSync().let { true }
                "torch" -> toggleTorch(context)
                "wifi" -> openWifi(context).let { true }
                "bluetooth" -> openBluetooth(context).let { true }
                "location" -> openLocation(context).let { true }
                "hotspot" -> openHotspot(context).let { true }
                "nfc" -> openNfc(context).let { true }
                "storage" -> Toast.makeText(context, storageInfo(context), Toast.LENGTH_LONG).show().let { true }
                "battery" -> Toast.makeText(context, batteryStatus(context), Toast.LENGTH_LONG).show().let { true }
                else -> false
            }
        }.getOrElse {
            DebugLog.write(context, "run action=$id error=${it.javaClass.simpleName}:${it.message}")
            false
        }
        DebugLog.write(context, "run action=$id result=$result")
        return result
    }

    fun openWifi(context: Context) = openSettings(context, Settings.ACTION_WIFI_SETTINGS)
    fun openBluetooth(context: Context) = openSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS)
    fun openLocation(context: Context) = openSettings(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    fun openHotspot(context: Context) = openSettings(context, ACTION_TETHER_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
    fun openNfc(context: Context) = openSettings(context, Settings.ACTION_NFC_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)

    fun status(context: Context, id: String): String =
        runCatching {
            when (id) {
                "brightness" -> brightnessStatus(context)
                "volume" -> volumeStatus(context)
                "ringer" -> ringerStatus(context)
                "timeout" -> timeoutStatus(context)
                "rotation" -> onOff(Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1)
                "sync" -> onOff(ContentResolver.getMasterSyncAutomatically())
                "torch" -> onOff(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_TORCH_ON, false))
                "wifi" -> if (isWifiActive(context)) "目前使用 Wi-Fi，點選開設定頁" else "點選開 Wi-Fi 設定"
                "bluetooth" -> "點選開 Bluetooth 設定"
                "location" -> locationStatus(context)
                "hotspot" -> "點選開 Wi-Fi 熱點設定"
                "nfc" -> nfcStatus(context)
                "storage" -> storageInfo(context).replace("\n", " / ")
                "battery" -> batteryStatus(context)
                else -> ""
            }
        }.getOrDefault("點選開啟")

    fun panelStatus(context: Context, id: String): String =
        runCatching {
            when (id) {
                "brightness" -> brightnessStatus(context).replace("，約 ", " ")
                "volume" -> "${volumePercent(context)}%"
                "ringer" -> ringerStatus(context)
                "timeout" -> timeoutStatus(context)
                "rotation" -> onOffShort(Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1)
                "sync" -> onOffShort(ContentResolver.getMasterSyncAutomatically())
                "torch" -> onOffShort(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_TORCH_ON, false))
                "wifi" -> wifiSsid(context)
                "bluetooth" -> onOffShort(isBluetoothOn(context))
                "location" -> locationStatusShort(context)
                "hotspot" -> "設定頁"
                "nfc" -> nfcStatusShort(context)
                "storage" -> storageFreePercent(context)
                "battery" -> "${batteryPercent(context)}%"
                else -> ""
            }
        }.getOrDefault("")

    fun panelColor(context: Context, id: String): Int =
        runCatching {
            if (id == "battery") return@runCatching batteryLineColor(context)
            val enabled = when (id) {
                "rotation" -> Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
                "sync" -> ContentResolver.getMasterSyncAutomatically()
                "torch" -> context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_TORCH_ON, false)
                "brightness" -> isAutoBrightness(context)
                "wifi" -> isWifiActive(context)
                "bluetooth" -> isBluetoothOn(context)
                "location" -> Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF) != Settings.Secure.LOCATION_MODE_OFF
                "nfc" -> isNfcOn(context)
                else -> null
            }
            when (enabled) {
                true -> 0xFF7CB342.toInt()
                false -> 0xFF78909C.toInt()
                null -> 0xFF8EA2AD.toInt()
            }
        }.getOrDefault(0xFF8EA2AD.toInt())

    fun panelIcon(context: Context, id: String): Int =
        runCatching {
            when (id) {
                "brightness" -> brightnessIcon(context)
                "volume" -> volumeIcon(context)
                "ringer" -> when (context.getSystemService(AudioManager::class.java).ringerMode) {
                    AudioManager.RINGER_MODE_NORMAL -> R.drawable.ic_sound
                    AudioManager.RINGER_MODE_VIBRATE -> R.drawable.ic_vibro
                    else -> R.drawable.ic_silent
                }
                "timeout" -> R.drawable.ic_screen_timeout
                "rotation" -> R.drawable.ic_auto_rotation
                "sync" -> R.drawable.ic_autosync
                "torch" -> R.drawable.ic_flashlight
                "wifi" -> R.drawable.ic_wifi
                "bluetooth" -> R.drawable.ic_bluetooth
                "location" -> R.drawable.ic_gps
                "hotspot" -> R.drawable.ic_hotspot
                "nfc" -> R.drawable.ic_nfc
                "storage" -> R.drawable.ic_memory
                "battery" -> R.drawable.ic_battery
                else -> 0
            }
        }.getOrDefault(0)

    fun cycleVolume(context: Context) {
        val max = volumeMax(context)
        val current = volumeIndex(context)
        val steps = intArrayOf(0, max / 4, max / 2, max * 3 / 4, max).distinct().toIntArray()
        val next = steps.firstOrNull { it > current } ?: steps[0]
        val actual = setVolumeIndex(context, next)
        DebugLog.write(context, "volume cycle max=$max current=$current target=$next actual=$actual")
    }

    fun volumeMax(context: Context): Int =
        context.getSystemService(AudioManager::class.java).getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    fun volumeIndex(context: Context): Int =
        context.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_MUSIC)

    fun volumePercent(context: Context): Int =
        volumePercent(context, volumeIndex(context))

    fun volumePercent(context: Context, index: Int): Int =
        (index.coerceIn(0, volumeMax(context)) * 100 / volumeMax(context)).coerceIn(0, 100)

    fun setVolumeIndex(context: Context, index: Int): Int {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = volumeMax(context)
        val target = index.coerceIn(0, max)
        if (audio.isVolumeFixed) {
            DebugLog.write(context, "volume fixed target=$target max=$max")
            return volumeIndex(context)
        }
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        val actual = volumeIndex(context)
        DebugLog.write(context, "volume set target=$target max=$max actual=$actual percent=${volumePercent(context, actual)}")
        return actual
    }

    fun toggleRinger(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)
        val before = audio.ringerMode
        val target = if (before == AudioManager.RINGER_MODE_NORMAL) {
            AudioManager.RINGER_MODE_VIBRATE
        } else {
            AudioManager.RINGER_MODE_NORMAL
        }
        audio.ringerMode = target
        if (target == AudioManager.RINGER_MODE_NORMAL) {
            audio.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
        }
        val actual = audio.ringerMode
        DebugLog.write(context, "ringer toggle before=$before target=$target actual=$actual")
        return actual == target
    }

    fun toggleAutoSync() {
        ContentResolver.setMasterSyncAutomatically(!ContentResolver.getMasterSyncAutomatically())
    }

    fun toggleBrightness(context: Context): Boolean {
        if (!canWriteSettings(context)) return false
        val resolver = context.contentResolver
        val mode = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
        if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 64)
        } else {
            val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            val next = intArrayOf(64, 128, 192, 255).firstOrNull { it > current }
            if (next == null) {
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
            } else {
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, next)
            }
        }
        return true
    }

    fun brightnessPercent(context: Context): Int =
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) * 100 / 255

    fun setBrightnessPercent(context: Context, percent: Int): Boolean {
        if (!canWriteSettings(context)) return false
        val value = (percent.coerceIn(1, 100) * 255 / 100).coerceIn(1, 255)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
        DebugLog.write(context, "brightness slider request=$percent value=$value actual=${brightnessPercent(context)}")
        return true
    }

    fun cycleScreenTimeout(context: Context): Boolean {
        if (!canWriteSettings(context)) return false
        val values = intArrayOf(15_000, 30_000, 60_000, 120_000)
        val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, values[1])
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, values.firstOrNull { it > current } ?: values[0])
        return true
    }

    fun toggleAutoRotate(context: Context): Boolean {
        if (!canWriteSettings(context)) return false
        val resolver = context.contentResolver
        val current = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
        Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, if (current == 0) 1 else 0)
        return true
    }

    fun toggleTorch(context: Context): Boolean {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return false
        val camera = context.getSystemService(CameraManager::class.java)
        val id = camera.cameraIdList.firstOrNull {
            camera.getCameraCharacteristics(it)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val enabled = !prefs.getBoolean(KEY_TORCH_ON, false)
        camera.setTorchMode(id, enabled)
        prefs.edit().putBoolean(KEY_TORCH_ON, enabled).apply()
        return true
    }

    fun launchApp(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        DebugLog.write(context, "launch app package=$packageName")
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun storageInfo(context: Context): String {
        val stats = StatFs(Environment.getDataDirectory().path)
        val total = stats.blockCountLong * stats.blockSizeLong
        val free = stats.availableBlocksLong * stats.blockSizeLong
        val usedPercent = if (total == 0L) 0 else ((total - free) * 100 / total).toInt()
        return context.getString(R.string.storage_info, formatBytes(free), formatBytes(total), usedPercent)
    }

    private fun storageFreePercent(context: Context): String {
        val stats = StatFs(Environment.getDataDirectory().path)
        val total = stats.blockCountLong * stats.blockSizeLong
        val free = stats.availableBlocksLong * stats.blockSizeLong
        val freePercent = if (total == 0L) 0 else (free * 100 / total).toInt()
        return "可用 $freePercent%"
    }

    private fun batteryIntent(context: Context): Intent? =
        context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun batteryPercent(context: Context): Int {
        val intent = batteryIntent(context) ?: return 0
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else 0
    }

    private fun batteryPlugged(context: Context): Int =
        batteryIntent(context)?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

    private fun batteryStatus(context: Context): String {
        val plugged = batteryPlugged(context)
        val charging = when {
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "無線充電中"
            plugged != 0 -> "有線充電中"
            else -> "未充電"
        }
        return "電量 ${batteryPercent(context)}% / $charging"
    }

    private fun batteryLineColor(context: Context): Int {
        val plugged = batteryPlugged(context)
        return when {
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> 0xFFFBC02D.toInt()
            plugged != 0 -> 0xFF7CB342.toInt()
            else -> 0xFF78909C.toInt()
        }
    }

    private fun brightnessStatus(context: Context): String {
        val resolver = context.contentResolver
        val value = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 0) * 100 / 255
        return "${if (isAutoBrightness(context)) "自動" else "手動"}，約 $value%"
    }

    private fun brightnessIcon(context: Context): Int {
        val resolver = context.contentResolver
        val mode = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
        if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) return R.drawable.ic_brightness_auto
        val value = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        return when {
            value < 64 -> R.drawable.ic_brightness_dim
            value < 160 -> R.drawable.ic_brightness_low
            else -> R.drawable.ic_brightness_high
        }
    }

    private fun volumeStatus(context: Context): String {
        val audio = context.getSystemService(AudioManager::class.java)
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return "媒體音量 ${current * 100 / max}%"
    }

    private fun volumeIcon(context: Context): Int {
        val percent = volumePercent(context)
        return when {
            percent == 0 -> R.drawable.ic_volume_min
            percent >= 70 -> R.drawable.ic_volume_max
            else -> R.drawable.ic_volume_control
        }
    }

    private fun ringerStatus(context: Context): String =
        when (context.getSystemService(AudioManager::class.java).ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> "鈴聲"
            AudioManager.RINGER_MODE_VIBRATE -> "震動"
            else -> "靜音"
        }

    private fun timeoutStatus(context: Context): String {
        val seconds = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 0) / 1000
        return if (seconds >= 60) "${seconds / 60} 分鐘" else "$seconds 秒"
    }

    private fun locationStatus(context: Context): String {
        val mode = Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF)
        return if (mode == Settings.Secure.LOCATION_MODE_OFF) "關閉，點選開設定頁" else "開啟，點選開設定頁"
    }

    private fun locationStatusShort(context: Context): String {
        val mode = Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF)
        return onOffShort(mode != Settings.Secure.LOCATION_MODE_OFF)
    }

    private fun isWifiActive(context: Context): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return false
        return connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    private fun wifiSsid(context: Context): String {
        if (!isWifiActive(context)) return "Wi-Fi"
        val ssid = runCatching {
            context.getSystemService(WifiManager::class.java).connectionInfo?.ssid
        }.getOrNull()?.trim('"')
        return ssid?.takeUnless { it.isBlank() || it == "<unknown ssid>" || it == "0x" } ?: "Wi-Fi"
    }

    private fun isAutoBrightness(context: Context): Boolean =
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

    private fun isBluetoothOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, "bluetooth_on", 0) != 0

    private fun isNfcOn(context: Context): Boolean =
        NfcAdapter.getDefaultAdapter(context)?.isEnabled == true

    private fun nfcStatus(context: Context): String =
        if (NfcAdapter.getDefaultAdapter(context) == null) "此裝置不支援 NFC" else "NFC ${onOff(isNfcOn(context))}"

    private fun nfcStatusShort(context: Context): String =
        if (NfcAdapter.getDefaultAdapter(context) == null) "未支援" else onOffShort(isNfcOn(context))

    private fun onOff(enabled: Boolean): String = if (enabled) "開啟" else "關閉"

    private fun onOffShort(enabled: Boolean): String = if (enabled) "開" else "關"

    fun requestOverlayPermission(activity: android.app.Activity) {
        activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}")))
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    private fun canWriteSettings(context: Context): Boolean {
        if (Settings.System.canWrite(context)) return true
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return false
    }

    private fun openSettings(context: Context, action: String, fallbackAction: String? = null) {
        DebugLog.write(context, "open settings action=$action fallback=$fallbackAction")
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            if (fallbackAction != null) {
                runCatching { context.startActivity(Intent(fallbackAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        var value = bytes.toDouble()
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit])
    }
}
