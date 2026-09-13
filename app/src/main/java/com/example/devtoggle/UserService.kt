package com.example.devtoggle

/**
 * Instantiated by Shizuku in a SEPARATE process running as the shell (adb)
 * UID — not our app's normal UID. That process already holds
 * WRITE_SECURE_SETTINGS (it's the same identity behind
 * `adb shell settings put global ...`), so commands run from here succeed
 * where the identical call from MainActivity's own process fails.
 *
 * This process is not a full Android app context, so rather than reflecting
 * into framework internals for a ContentResolver, we just shell out to the
 * `settings` command-line tool via ProcessBuilder — it inherits this
 * process's shell UID and permissions, and is the same tool ADB itself uses.
 */
class UserService : IUserService.Stub() {

    override fun setDevelopmentSettingsEnabled(enabled: Boolean) {
        val value = if (enabled) "1" else "0"
        runCommand(arrayOf("settings", "put", "global", "development_settings_enabled", value))
    }

    override fun getDevelopmentSettingsEnabled(): Boolean {
        val result = runCommand(arrayOf("settings", "get", "global", "development_settings_enabled"))
        return result.trim() == "1"
    }

    override fun destroy() {
        System.exit(0)
    }

    private fun runCommand(command: Array<String>): String {
        return try {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
}
