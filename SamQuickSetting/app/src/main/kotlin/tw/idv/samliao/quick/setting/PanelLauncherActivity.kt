package tw.idv.samliao.quick.setting

import android.app.Activity
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle

class PanelLauncherActivity : Activity() {
    private val ssidPermissionCode = 2001
    private val prefsName = "panel_launcher"
    private val ssidPermissionAsked = "ssid_permission_asked"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        if (
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            !prefs.getBoolean(ssidPermissionAsked, false)
        ) {
            prefs.edit().putBoolean(ssidPermissionAsked, true).apply()
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), ssidPermissionCode)
            return
        }
        openPanel()
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        openPanel()
        finish()
    }

    private fun openPanel() {
        if (!QuickActions.canDrawOverlays(this)) {
            QuickActions.requestOverlayPermission(this)
            return
        }
        startService(Intent(this, FloatingPanelService::class.java))
    }
}
