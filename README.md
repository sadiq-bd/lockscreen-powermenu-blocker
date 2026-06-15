<p align="left"> <img src="https://api.sadiq.workers.dev/app/github/repo/lockscreen-powermenu-blocker/views" alt="" /> </p>

# lockscreen-powermenu-blocker

A high-performance, native C Magisk module to disable the power menu while the screen is locked on Android devices.

## Overview

`lockscreen-powermenu-blocker` is a lightweight native daemon that prevents access to the power menu when your device is locked. By intercepting low-level Linux hardware events, it enhances device security by ensuring no one can power off, restart, or access other power menu options from the lockscreen.

Originally written as a shell script, the module has been completely rewritten in native C to ensure **zero disk I/O**, negligible battery impact, and lightning-fast interception.

## Features

- **True Native Performance:** Written entirely in C, utilizing standard Linux input structures.
- **Battery Friendly:** Uses a low-overhead `poll()` event loop instead of CPU-heavy shell polling.
- **Zero Disk Thrashing:** State transitions are handled in RAM, completely eliminating the need for temporary files.
- **Universal Compatibility:** Dynamically scans `/dev/input/` to detect the correct power key event node for your specific device.

## How It Works

The module runs as a background native daemon. It attaches a non-blocking `poll()` listener directly to your device's hardware power button event node. When the power button is held down, it quickly checks the system's keyguard state. If the device is locked and the hold duration reaches the threshold (210ms), the daemon instantly triggers a screen-off event, intercepting and blocking the power menu from appearing.

## Installation

1. **Download the ZIP:** Get the latest release from the [Releases page](https://github.com/sadiq-bd/lockscreen-powermenu-blocker/releases).
2. **Flash in Magisk:** - Open the Magisk app.
   - Go to Modules.
   - Tap "Install from storage" and select the ZIP file.
3. **Reboot your device.**

## Uninstallation

- Remove the module from the Magisk app and reboot your device.

## Compatibility

- Android devices with Magisk/KernelSU installed.
- Android 10+ (utilizes `dumpsys activity keyguard` for modern AOSP state tracking).
- Works universally across most OEM ROMs (Samsung, Pixel, Xiaomi, etc.) without hardcoding hardware paths.

## Technical Details

- **Hardware Interception:** Automatically scans up to 32 `/dev/input/event*` nodes and queries capabilities via `ioctl` to find the exact node broadcasting `KEY_POWER`.
- **Event Loop:** Replaces the heavy `getevent` shell utility with a direct C file descriptor read, completely eliminating sub-process overhead.
- **State Evaluation:** Uses `dumpsys activity keyguard` to determine the lock state, avoiding the massive memory overhead of standard window dumps. 

## Source

See [`src/powerblockerd.c`](src/powerblockerd.c) for the main implementation.

## Credits

- [sadiq-bd](https://github.com/sadiq-bd)

## License

[MIT](LICENSE)

---

**Contributions and feedback are welcome!**
