# DevToggle

A minimal Android app with a single switch to toggle
`Settings.Global.DEVELOPMENT_SETTINGS_ENABLED` (Developer Options on/off),
using [Shizuku](https://shizuku.rikka.app/) instead of root.

## How it works

- Writing this setting requires the `WRITE_SECURE_SETTINGS` permission,
  which normal apps cannot hold.
- Shizuku runs as the `adb`/shell identity, which already has that
  permission. The Shizuku app on your phone grants a slice of its
  privileges to apps you approve, over a binder IPC — no root needed.
- This app asks Shizuku for permission, then calls
  `Settings.Global.putInt(...)` — which succeeds because the call is
  executed with the shell-level grant Shizuku provided.

## Requirements on your phone

1. Install the **Shizuku** app (Play Store or F-Droid).
2. Start the Shizuku service — either via:
   - Wireless debugging (Android 11+): pair once in Developer Options →
     Wireless debugging, then start Shizuku from its app using the
     "Start via Wireless debugging" option, or
   - `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`
     from a PC once.
3. Install this app's APK, open it, tap **Request Shizuku Permission**,
   approve it in the Shizuku popup.
4. The switch becomes usable and reflects/writes the real setting.

## Build & deploy — all via Termux + GitHub, no local Android SDK

You already have `gh` authenticated in Termux. From the folder containing
this project:

```bash
cd devtoggle

git init
git add .
git commit -m "Initial commit: DevToggle app"

# Create a new GitHub repo and push (adjust name/visibility as you like)
gh repo create devtoggle --public --source=. --remote=origin --push
```

That's it — pushing to `main` triggers `.github/workflows/build.yml`,
which builds a debug APK entirely on GitHub's servers (Ubuntu runner,
Android SDK + Gradle installed there, not on your phone).

### Getting the APK afterwards

1. Go to your repo on GitHub → **Actions** tab → the latest "Build APK" run.
2. Under **Artifacts**, download `DevToggle-debug-apk` (a zip containing
   `app-debug.apk`).
3. Transfer/download that to your phone and install it (allow "install
   unknown apps" for whichever browser/file manager you use).

You can also trigger a build manually anytime from the Actions tab via
"Run workflow" (this is enabled by the `workflow_dispatch` trigger).

## Notes

- This builds a **debug** APK (self-signed automatically by Gradle's
  default debug keystore) — fine for personal use, not for Play Store.
- minSdk is 26 (Android 8.0+), matching Shizuku's realistic userbase.
- No secrets, signing keys, or tokens are needed in the repo for this
  workflow — it only reads your public source and builds it.
