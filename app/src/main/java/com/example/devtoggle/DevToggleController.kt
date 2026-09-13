package com.example.devtoggle

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Shared logic for binding to the Shizuku UserService and flipping
 * development_settings_enabled, reusable from the Activity, the Quick
 * Settings tile, and the home screen widget — so all three stay in sync
 * and none of them duplicate the Shizuku binding dance.
 */
object DevToggleController {

    private const val TAG = "DevToggleController"

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName("com.example.devtoggle", UserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("privileged")
        .debuggable(false)
        .version(1)

    fun isShizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Binds, runs [action] with the connected service, then unbinds.
     * Safe to call from any entry point (tile, widget, activity) since each
     * call gets its own short-lived connection rather than sharing state.
     */
    fun withService(context: Context, action: (IUserService) -> Unit, onError: (String) -> Unit = {}) {
        if (!isShizukuReady()) {
            onError("Shizuku not ready (not running or permission not granted)")
            return
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                try {
                    val service = IUserService.Stub.asInterface(binder)
                    action(service)
                } catch (e: Exception) {
                    Log.e(TAG, "Error using UserService", e)
                    onError(e.message ?: "Unknown error")
                } finally {
                    try {
                        Shizuku.unbindUserService(userServiceArgs, this, false)
                    } catch (_: Exception) {
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
            }
        }

        try {
            Shizuku.bindUserService(userServiceArgs, connection)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind UserService", e)
            onError(e.message ?: "Bind failed")
        }
    }

    fun toggle(context: Context, onResult: (newState: Boolean) -> Unit, onError: (String) -> Unit = {}) {
        withService(context, action = { service ->
            val current = service.developmentSettingsEnabled
            val next = !current
            service.setDevelopmentSettingsEnabled(next)
            onResult(next)
        }, onError = onError)
    }

    fun readState(context: Context, onResult: (Boolean) -> Unit, onError: (String) -> Unit = {}) {
        withService(context, action = { service ->
            onResult(service.developmentSettingsEnabled)
        }, onError = onError)
    }
}
