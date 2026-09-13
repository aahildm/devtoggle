package com.example.devtoggle

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import rikka.shizuku.Shizuku

/**
 * Simple toggle for Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
 * performed via Shizuku (which runs as the shell/adb UID and already
 * holds WRITE_SECURE_SETTINGS). No root required.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var switchDev: SwitchMaterial
    private lateinit var btnRequest: Button

    private val permissionCode = 1001

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == permissionCode) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                log("Shizuku permission granted.")
                refreshUiState()
            } else {
                log("Shizuku permission denied.")
            }
        }
    }

    private val binderListener = Shizuku.OnBinderReceivedListener {
        log("Shizuku binder received.")
        refreshUiState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        log("Shizuku binder died — is the Shizuku app/service running?")
        tvStatus.text = "Shizuku: not running"
        switchDev.isEnabled = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvShizukuStatus)
        tvLog = findViewById(R.id.tvLog)
        switchDev = findViewById(R.id.switchDevSettings)
        btnRequest = findViewById(R.id.btnRequestPermission)

        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)

        btnRequest.setOnClickListener { requestShizukuPermission() }

        switchDev.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                setDevelopmentSettingsEnabled(isChecked)
            }
        }

        refreshUiState()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    private fun requestShizukuPermission() {
        if (!Shizuku.pingBinder()) {
            log("Shizuku service not available. Open the Shizuku app and start the service first.")
            return
        }
        if (Shizuku.isPreV11()) {
            log("Shizuku version too old (pre-v11 API unsupported).")
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            log("Already granted.")
            refreshUiState()
            return
        }
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            log("User previously denied permission permanently.")
            return
        }
        Shizuku.requestPermission(permissionCode)
    }

    @SuppressLint("SetTextI18n")
    private fun refreshUiState() {
        val running = Shizuku.pingBinder()
        if (!running) {
            tvStatus.text = "Shizuku: not running"
            switchDev.isEnabled = false
            return
        }

        val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        tvStatus.text = if (granted) "Shizuku: connected & permitted" else "Shizuku: running, permission needed"
        switchDev.isEnabled = granted

        if (granted) {
            val current = try {
                Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
            } catch (e: Exception) {
                log("Failed to read setting: ${e.message}")
                0
            }
            switchDev.setOnCheckedChangeListener(null)
            switchDev.isChecked = current == 1
            switchDev.setOnCheckedChangeListener { buttonView, isChecked ->
                if (buttonView.isPressed) setDevelopmentSettingsEnabled(isChecked)
            }
        }
    }

    private fun setDevelopmentSettingsEnabled(enabled: Boolean) {
        try {
            // This call requires WRITE_SECURE_SETTINGS. Shizuku having granted us
            // that permission (it's held by the shell/adb identity) is what makes
            // this call succeed instead of throwing SecurityException.
            Settings.Global.putInt(
                contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                if (enabled) 1 else 0
            )
            log("Set development_settings_enabled = ${if (enabled) 1 else 0}")
        } catch (e: SecurityException) {
            log("SecurityException: ${e.message}. Permission not actually held.")
            refreshUiState()
        } catch (e: Exception) {
            log("Error: ${e.message}")
        }
    }

    private fun log(message: String) {
        tvLog.append("$message\n")
    }
}
