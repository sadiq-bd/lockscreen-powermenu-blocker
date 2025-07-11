<p align="left"> <img src="https://api.sadiq.us.to/app/github/repo/lockscreen-powermenu-blocker/views?nocache=true" alt="" /> </p>

# lockscreen-powermenu-blocker

A Magisk module to disable the power menu while the screen is locked on Android devices.

## Overview

`lockscreen-powermenu-blocker` is a shell-based Magisk module that prevents access to the power menu when your device is locked. This enhances device security by ensuring no one can power off, restart, or access other power menu options from the lockscreen.

## Features

- Blocks power menu access when the screen is locked
- Universal and efficient: dynamically detects the correct input device for the power key
- Minimal CPU usage
- Easy to install and uninstall

## How It Works

The module uses a background shell script to monitor the power key event. If the power button is held down while the device is locked, the script quickly turns the screen off, preventing the power menu from being shown.

## Installation

1. **Download the ZIP:**  
   Get the latest release from the [Releases page](https://github.com/sadiq-bd/lockscreen-powermenu-blocker/releases).
2. **Flash in Magisk:**  
   - Open the Magisk app.
   - Go to Modules.
   - Tap "Install from storage" and select the ZIP file.
3. **Reboot your device.**

## Uninstallation

- Remove the module from the Magisk app and reboot your device.

## Compatibility

- Android devices with Magisk installed
- No hardcoded event device: works universally across most Android devices
- Requires access to `/system/bin/sh` and the `getevent` utility

## Technical Details

- The script runs as a background service to monitor power button events.
- It dynamically locates the input device responsible for power key events.
- When the power key is pressed while the device is locked, the script triggers a screen-off event, blocking the power menu.

## Source

See [`system/bin/lockscreen-powermenu-blocker.sh`](system/bin/lockscreen-powermenu-blocker.sh) for the main implementation.

## Credits

- [sadiq-bd](https://github.com/sadiq-bd)

## License

[MIT](LICENSE)

---

**Contributions and feedback are welcome!**
