package cam.sam.minicam

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.MacAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ScrollView
import android.widget.TextView
import android.widget.VideoView
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

class MainActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scanResults = linkedMapOf<String, ScanResult>()
    private var bluetoothGatt: BluetoothGatt? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var pendingManualWifiSsid: String? = null

    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private lateinit var devicesContainer: LinearLayout
    private lateinit var serialInput: EditText
    private lateinit var ssidInput: EditText
    private lateinit var wifiMacInput: EditText
    private lateinit var btMacInput: EditText
    private lateinit var previewView: VideoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
        refreshPermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        pendingManualWifiSsid?.let {
            pendingManualWifiSsid = null
            appendLog(getString(R.string.manual_wifi_resumed, it))
            probeCameraHttp()
        }
    }

    override fun onDestroy() {
        stopBleScan()
        bluetoothGatt?.close()
        networkCallback?.let {
            getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it)
        }
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshPermissionStatus()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_ADD_WIFI_NETWORK) return
        if (resultCode == RESULT_OK) {
            appendLog(getString(R.string.hidden_wifi_saved))
            pendingManualWifiSsid?.let {
                pendingManualWifiSsid = null
                appendLog(getString(R.string.manual_wifi_resumed, it))
                probeCameraHttp()
            }
        } else {
            appendLog(getString(R.string.hidden_wifi_canceled))
        }
    }

    private fun createContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(40), dp(20), dp(24))
            setBackgroundColor(0xFFF7F7F2.toInt())
        }

        root.addView(label(R.string.app_name, 34f, 0xFF252525.toInt(), Gravity.CENTER))
        root.addView(label(R.string.app_summary, 16f, 0xFF555555.toInt(), Gravity.CENTER).apply {
            setPadding(0, dp(12), 0, dp(18))
        })

        statusView = label(0, 16f, 0xFF333333.toInt(), Gravity.START)
        root.addView(statusView)
        root.addView(actionButton(R.string.request_permissions) { requestMissingPermissions() })
        root.addView(actionButton(R.string.scan_devices) { startBleScan() })

        devicesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = matchWrap().apply { topMargin = dp(8) }
        }
        root.addView(devicesContainer)

        serialInput = input(R.string.camera_serial_number, "")
        ssidInput = input(R.string.camera_wifi_ssid, "")
        wifiMacInput = input(R.string.camera_wifi_mac, "")
        btMacInput = input(R.string.camera_bt_mac, "")
        serialInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                cameraWifiCredentials(s.toString())?.let { ssidInput.setText(it.ssid) }
            }
        })
        wifiMacInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (btMacInput.text.isBlank()) wifiMacToBtMac(s.toString())?.let { btMacInput.setText(it) }
            }
        })
        root.addView(serialInput)
        root.addView(ssidInput)
        root.addView(wifiMacInput)
        root.addView(btMacInput)
        root.addView(actionButton(R.string.connect_ble_by_bt_mac) { connectBleByBtMac() })
        root.addView(actionButton(R.string.connect_camera_wifi) { showCameraWifiDialog() })
        root.addView(actionButton(R.string.probe_camera) { probeCameraHttp() })
        root.addView(actionButton(R.string.open_rtsp_preview) { openRtspPreview() })

        previewView = VideoView(this).apply {
            visibility = View.GONE
            setMediaController(MediaController(this@MainActivity))
            setOnErrorListener { _: MediaPlayer, what: Int, extra: Int ->
                appendLog(getString(R.string.rtsp_preview_failed, what, extra))
                true
            }
            layoutParams = matchWrap().apply {
                height = dp(260)
                topMargin = dp(12)
            }
        }
        root.addView(previewView)

        logView = label(0, 14f, 0xFF333333.toInt(), Gravity.START).apply {
            setPadding(0, dp(16), 0, 0)
        }
        root.addView(logView)

        return ScrollView(this).apply { addView(root) }
    }

    private fun requestMissingPermissions() {
        val missing = requiredRuntimePermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            refreshPermissionStatus()
            appendLog(getString(R.string.permissions_ready))
        } else {
            requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    private fun startBleScan() {
        if (!hasRequiredPermissions()) {
            requestMissingPermissions()
            return
        }
        val adapter = getSystemService(android.bluetooth.BluetoothManager::class.java).adapter
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || scanner == null || !adapter.isEnabled) {
            appendLog(getString(R.string.bluetooth_unavailable))
            return
        }

        scanResults.clear()
        devicesContainer.removeAllViews()
        appendLog(getString(R.string.scan_started))
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, scanCallback)
        mainHandler.postDelayed({ stopBleScan() }, SCAN_MS)
    }

    private fun stopBleScan() {
        if (hasBluetoothScanPermission()) {
            getSystemService(android.bluetooth.BluetoothManager::class.java)
                .adapter
                ?.bluetoothLeScanner
                ?.stopScan(scanCallback)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            addScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::addScanResult)
        }

        override fun onScanFailed(errorCode: Int) {
            appendLog(getString(R.string.scan_failed, errorCode))
        }
    }

    private fun addScanResult(result: ScanResult) {
        val device = result.device ?: return
        val address = device.address ?: return
        if (scanResults.containsKey(address)) return
        scanResults[address] = result

        val name = bluetoothName(device)
        val row = Button(this).apply {
            text = getString(R.string.device_row, name, address, result.rssi)
            textSize = 14f
            setAllCaps(false)
            setOnClickListener { connectBle(device) }
            layoutParams = matchWrap().apply { topMargin = dp(6) }
        }
        devicesContainer.addView(row)
        appendLog(getString(R.string.device_found, name, address, result.rssi))
    }

    private fun connectBle(device: BluetoothDevice) {
        if (!hasBluetoothConnectPermission()) {
            requestMissingPermissions()
            return
        }
        bluetoothGatt?.close()
        appendLog(getString(R.string.ble_connecting, bluetoothName(device)))
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private fun connectBleByBtMac() {
        if (!hasBluetoothConnectPermission()) {
            requestMissingPermissions()
            return
        }
        val address = btMacInput.text.toString().trim().uppercase(Locale.US)
        if (!android.bluetooth.BluetoothAdapter.checkBluetoothAddress(address)) {
            appendLog(getString(R.string.bt_mac_invalid))
            return
        }
        val adapter = getSystemService(android.bluetooth.BluetoothManager::class.java).adapter
        if (adapter == null || !adapter.isEnabled) {
            appendLog(getString(R.string.bluetooth_unavailable))
            return
        }
        connectBle(adapter.getRemoteDevice(address))
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                appendLog(getString(R.string.ble_connected))
                if (hasBluetoothConnectPermission()) gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                appendLog(getString(R.string.ble_disconnected, status))
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val services = gatt.services.joinToString("\n") { service ->
                val chars = service.characteristics.joinToString(", ") { it.uuid.toString() }
                "${service.uuid}\n  $chars"
            }
            appendLog(getString(R.string.ble_services, status, services.ifBlank { "-" }))
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            appendLog(getString(R.string.ble_notify, characteristic.uuid, characteristic.value.size))
        }
    }

    private fun cameraWifiInput(): CameraWifiInput? {
        val serial = serialInput.text.toString()
        val credentials = cameraWifiCredentials(serial)
        if (serial.isNotBlank() && credentials == null) {
            appendLog(getString(R.string.serial_invalid))
            return null
        }
        val ssid = credentials?.ssid ?: ssidInput.text.toString().trim()
        val password = credentials?.password.orEmpty()
        val wifiMac = wifiMacInput.text.toString().trim()
        if (ssid.isEmpty()) {
            appendLog(getString(R.string.ssid_required))
            return null
        }
        val bssid = if (wifiMac.isBlank()) null else runCatching {
            MacAddress.fromString(wifiMac)
        }.getOrElse {
            appendLog(getString(R.string.wifi_mac_invalid))
            return null
        }
        return CameraWifiInput(ssid, password, bssid)
    }

    private fun showCameraWifiDialog() {
        val input = cameraWifiInput() ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.manual_wifi_title)
            .setMessage(getString(R.string.manual_wifi_message, input.ssid, input.password.ifBlank { "-" }))
            .setPositiveButton(R.string.open_wifi_panel) { _, _ ->
                requestCameraWifiNetwork(input.ssid, input.password, null, false)
            }
            .setNegativeButton(R.string.copy_wifi_password) { _, _ -> copyWifiPassword(input.password) }
            .setNeutralButton(R.string.auto_wifi_request) { _, _ ->
                addHiddenWifi(input)
            }
            .show()
    }

    private fun addHiddenWifi(input: CameraWifiInput) {
        val suggestion = WifiNetworkSuggestion.Builder()
            .setSsid(input.ssid)
            .setIsHiddenSsid(true)
            .apply {
                if (input.password.isNotBlank()) setWpa2Passphrase(input.password)
            }
            .build()
        copyWifiPassword(input.password)
        pendingManualWifiSsid = input.ssid
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(
                Settings.EXTRA_WIFI_NETWORK_LIST,
                arrayListOf(suggestion)
            )
            startActivityForResult(intent, REQUEST_ADD_WIFI_NETWORK)
        } else {
            val status = getSystemService(WifiManager::class.java).addNetworkSuggestions(listOf(suggestion))
            appendLog(getString(R.string.hidden_wifi_suggestion_result, status))
            startActivity(Intent(Settings.Panel.ACTION_WIFI))
        }
    }

    private fun copyWifiPassword(password: String) {
        if (password.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.camera_wifi_password), password))
        appendLog(getString(R.string.wifi_password_copied))
    }

    private fun requestCameraWifiNetwork(
        ssid: String,
        password: String,
        bssid: MacAddress?,
        isBssidFallback: Boolean
    ) {
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .apply {
                if (bssid != null) setBssid(bssid)
                if (password.isNotEmpty()) setWpa2Passphrase(password)
                setIsHiddenSsid(true)
            }
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        val connectivity = getSystemService(ConnectivityManager::class.java)
        networkCallback?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                connectivity.bindProcessToNetwork(network)
                appendLog(getString(R.string.camera_wifi_connected, ssid))
                probeCameraHttp()
            }

            override fun onUnavailable() {
                if (bssid != null && !isBssidFallback) {
                    appendLog(getString(R.string.camera_wifi_retry_without_bssid))
                    mainHandler.post { requestCameraWifiNetwork(ssid, password, null, true) }
                } else {
                    appendLog(getString(R.string.camera_wifi_unavailable))
                }
            }

            override fun onLost(network: Network) {
                connectivity.bindProcessToNetwork(null)
                appendLog(getString(R.string.camera_wifi_lost))
            }
        }
        connectivity.requestNetwork(request, networkCallback!!, WIFI_REQUEST_TIMEOUT_MS)
        appendLog(
            getString(
                if (bssid == null) R.string.camera_wifi_requested_without_bssid
                else R.string.camera_wifi_requested,
                ssid
            )
        )
    }

    private fun cameraWifiCredentials(serial: String): CameraWifiCredentials? {
        val normalized = serial.filter { it.isLetterOrDigit() }.uppercase(Locale.US)
        if (normalized.length <= PASSWORD_LENGTH) return null
        val color = when (normalized.substring(2, 3)) {
            "B" -> "PK"
            "C" -> "GN"
            "D" -> "BL"
            else -> "GY"
        }
        return CameraWifiCredentials(
            ssid = "Canon $color ${normalized.takeLast(LAST_WIFI_SSID_LENGTH)}",
            password = md5(normalized).takeLast(PASSWORD_LENGTH)
        )
    }

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun wifiMacToBtMac(value: String): String? {
        val parts = value.trim().split(":")
        if (parts.size != 6) return null
        val bytes = parts.map { it.toIntOrNull(16) ?: return null }
        if (bytes.any { it !in 0..255 }) return null
        return bytes
            .dropLast(1)
            .plus((bytes.last() + 1) and 0xff)
            .joinToString(":") { "%02X".format(it) }
    }

    private fun probeCameraHttp() {
        appendLog(currentWifiState())
        appendLog(getString(R.string.http_probe_started, CAMERA_HTTP_URL))
        Thread {
            val result = runCatching {
                val connection = URL(CAMERA_HTTP_URL).openConnection() as HttpURLConnection
                connection.connectTimeout = 2500
                connection.readTimeout = 2500
                connection.requestMethod = "GET"
                "${connection.responseCode} ${connection.responseMessage}"
            }.getOrElse { it.message ?: it.javaClass.simpleName }
            appendLog(getString(R.string.http_probe_result, result))
        }.start()
    }

    private fun currentWifiState(): String {
        val info = getSystemService(WifiManager::class.java).connectionInfo
        val ssid = info?.ssid?.trim('"') ?: "-"
        val ip = info?.ipAddress?.let(::formatIpAddress) ?: "-"
        return getString(R.string.current_wifi_state, ssid, ip)
    }

    private fun formatIpAddress(value: Int): String =
        listOf(
            value and 0xff,
            value shr 8 and 0xff,
            value shr 16 and 0xff,
            value shr 24 and 0xff
        ).joinToString(".")

    private fun openRtspPreview() {
        previewView.visibility = View.VISIBLE
        previewView.setVideoURI(Uri.parse(CAMERA_RTSP_URL))
        previewView.start()
        appendLog(getString(R.string.rtsp_preview_started, CAMERA_RTSP_URL))
    }

    private fun refreshPermissionStatus() {
        val missing = requiredRuntimePermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        statusView.text = if (missing.isEmpty()) {
            getString(R.string.permissions_ready)
        } else {
            getString(R.string.permissions_missing, missing.size)
        }
    }

    private fun hasRequiredPermissions(): Boolean =
        requiredRuntimePermissions().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun requiredRuntimePermissions(): List<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.NEARBY_WIFI_DEVICES
            permissions += Manifest.permission.READ_MEDIA_IMAGES
            permissions += Manifest.permission.READ_MEDIA_VIDEO
        }
        return permissions
    }

    private fun hasBluetoothScanPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

    private fun hasBluetoothConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun bluetoothName(device: BluetoothDevice): String =
        if (hasBluetoothConnectPermission()) device.name ?: getString(R.string.unknown_device)
        else getString(R.string.unknown_device)

    private fun appendLog(message: String) {
        mainHandler.post {
            logView.text = listOf(logView.text.toString(), message)
                .filter { it.isNotBlank() }
                .takeLast(30)
                .joinToString("\n")
        }
    }

    private fun label(textRes: Int, size: Float, color: Int, gravityValue: Int): TextView =
        TextView(this).apply {
            if (textRes != 0) text = getString(textRes)
            textSize = size
            gravity = gravityValue
            setTextColor(color)
            layoutParams = matchWrap()
        }

    private fun input(hintRes: Int, value: String): EditText =
        EditText(this).apply {
            hint = getString(hintRes)
            setText(value)
            textSize = 16f
            setSingleLine(true)
            layoutParams = matchWrap().apply { topMargin = dp(10) }
        }

    private fun actionButton(labelRes: Int, action: () -> Unit): Button =
        Button(this).apply {
            text = getString(labelRes)
            textSize = 17f
            setAllCaps(false)
            setOnClickListener { action() }
            layoutParams = matchWrap().apply { topMargin = dp(10) }
        }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQUEST_PERMISSIONS = 1001
        const val REQUEST_ADD_WIFI_NETWORK = 1002
        const val SCAN_MS = 12_000L
        const val PASSWORD_LENGTH = 8
        const val LAST_WIFI_SSID_LENGTH = 4
        const val WIFI_REQUEST_TIMEOUT_MS = 30_000
        const val CAMERA_HTTP_URL = "http://192.168.42.1/"
        const val CAMERA_RTSP_URL = "rtsp://192.168.42.1/live"
    }

    private data class CameraWifiCredentials(val ssid: String, val password: String)
    private data class CameraWifiInput(val ssid: String, val password: String, val bssid: MacAddress?)
}
