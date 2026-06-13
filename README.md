<p align="left"> <img src="https://api.sadiq.workers.dev/app/github/repo/lockscreen-powermenu-blocker/views" alt="" /> </p>

# lockscreen-powermenu-blocker

A Magisk module to disable the power menu while the screen is locked on Android devices.

## Overview

`lockscreen-powermenu-blocker` runs a small Java daemon through Android's internal `app_process` runtime. While the device is locked, it disables Android's power-button long-press policy so the power menu cannot appear from the lockscreen.

This also covers occluded lockscreen states, such as opening the camera from the lockscreen: the device is still locked, so the power menu remains blocked even though a camera activity is visible.

## Features

- Blocks power menu access when the screen is locked
- Handles lockscreen camera and other keyguard-occluded states
- Uses internal Java APIs; no `getevent` input polling
- Event-driven with a low-rate safety refresh for minimal CPU usage
- Restores the user's original power-button behavior after unlock
- Easy to install and uninstall

## How It Works

Magisk starts `BlockerService.jar` with `app_process` at boot. The Java daemon binds to Android framework services directly:

- `WindowManager` checks whether keyguard is active.
- `KeyguardManager` confirms device-locked state, including lockscreen-camera cases.
- `Settings.Global` temporarily sets power-button long-press behavior to `0` while locked.

The service reacts to screen and unlock broadcasts, then performs a safety refresh every 5 seconds in case a framework transition was missed. It does not monitor raw input events and does not run a high-frequency shell loop.

Before changing any power-button setting, the daemon saves the original value into private backup keys. After the device is unlocked, those values are restored.

## Installation

1. **Download the ZIP:**  
   Get the latest release from the [Releases page](https://github.com/sadiq-bd/lockscreen-powermenu-blocker/releases).
2. **Flash in Magisk:**  
   - Open the Magisk app.
   - Go to Modules.
   - Tap "Install from storage" and select the ZIP file.
3. **Reboot your device.**

## Local Build

Requirements:

- JDK 17 or newer
- Android SDK with platform and build-tools installed
- `ANDROID_HOME` or `ANDROID_SDK_ROOT` set, unless the SDK is at `~/android-sdk`

Build the module locally:

```sh
./build.sh
```

Outputs:

- `system/usr/share/lockscreen-powermenu-blocker/BlockerService.jar`
- `build/lockscreen-powermenu-blocker.zip`

Clean generated outputs:

```sh
./build.sh clean
```

## Uninstallation

- Remove the module from the Magisk app and reboot your device.

## Compatibility

- Android devices with Magisk installed
- AOSP-based ROMs that honor `Settings.Global` power-button policy keys
- Requires Java framework access through `app_process`
- No hardcoded input device paths and no `getevent` dependency

## Technical Details

- `service.sh` only launches the Java daemon with `app_process`.
- `BlockerService` uses internal Binder/framework APIs through reflection where needed.
- While locked, it disables:
  - `power_button_long_press`
  - `power_button_very_long_press`
  - `long_press_power_behavior` for older/custom ROM fallback
- On unlock, it restores the previously saved values.

## Source

See [`BlockerService.java`](BlockerService.java) for the main implementation.

## Credits

- [sadiq-bd](https://github.com/sadiq-bd)

## License

[MIT](LICENSE)

---

**Contributions and feedback are welcome!**
