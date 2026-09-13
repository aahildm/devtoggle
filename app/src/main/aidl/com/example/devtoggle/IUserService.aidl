// IUserService.aidl
package com.example.devtoggle;

interface IUserService {
    /**
     * Runs in a process with shell (adb) UID via Shizuku, which holds
     * WRITE_SECURE_SETTINGS — so this call succeeds where a normal app call fails.
     */
    void setDevelopmentSettingsEnabled(boolean enabled);

    /** Reads back the current value using the same privileged process. */
    boolean getDevelopmentSettingsEnabled();

    void destroy() = 16777114; // Shizuku's special destroy transaction code
}
