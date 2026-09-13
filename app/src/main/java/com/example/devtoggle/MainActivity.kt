package com.example.devtoggle

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import rikka.shizuku.Shizuku

/**
 * Toggles Settings.Global.DEVELOPMENT_SETTINGS_ENABLED using a Shizuku
 * UserService — a separate process Shizuku spawns running as the shell
 * (adb) UID, which actually holds WRITE_SECURE_SETTINGS. Calling
 * Settings.Global directly from this Activity's own process does NOT work,
 * even with Shizuku's permission granted — Shizuku permission only lets us
 * bind that privileged process, it doesn't hand our own process the
 * permission.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var switchDev: SwitchMaterial
    private lateinit var btnRequest: Button

    private val permissionCode = 1001
    private var userService: IUserService? = null

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName("com.example.devtoggle", UserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("privileged")
        .debuggable(false)
        .version(1)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            userService = IUserService.Stub.asInterface(binder)
            log("UserService connected.")
            refreshUiState()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            userService = null
            log("UserService disconnected.")
        }
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == permissionCode) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                log("Shizuku permission granted.")
                bindUserService()
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
        userService = null
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
        try {
            Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
        } catch (e: Exception) {
        }
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
            bindUserService()
            return
        }
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            log("User previously denied permission permanently.")
            return
        }
        Shizuku.requestPermission(permissionCode)
    }

    private fun bindUserService() {
        try {
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
        } catch (e: Exception) {
            log("Failed to bind UserService: ${e.message}")
        }
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
        if (granted && userService == null) {
            bindUserService()
        }

        val ready = granted && userService != null
        tvStatus.text = when {
            !granted -> "Shizuku: running, permission needed"
            !ready -> "Shizuku: permitted, connecting service..."
            else -> "Shizuku: connected & permitted"
        }
        switchDev.isEnabled = ready

        if (ready) {
            val current = try {
                userService?.developmentSettingsEnabled ?: false
            } catch (e: Exception) {
                log("Failed to read setting: ${e.message}")
                false
            }
            switchDev.setOnCheckedChangeListener(null)
            switchDev.isChecked = current
            switchDev.setOnCheckedChangeListener { buttonView, isChecked ->
                if (buttonView.isPressed) setDevelopmentSettingsEnabled(isChecked)
            }
        }
    }

    private fun setDevelopmentSettingsEnabled(enabled: Boolean) {
        try {
            val service = userService
            if (service == null) {
                log("UserService not connected yet.")
                return
            }
            service.setDevelopmentSettingsEnabled(enabled)
            log("Set development_settings_enabled = ${if (enabled) 1 else 0}")
            DevToggleWidgetProvider.updateAllWidgets(this)
        } catch (e: Exception) {
            log("Error: ${e.message}")
            refreshUiState()
        }
    }

    private fun log(message: String) {
        tvLog.append("$message\n")
    }
}
