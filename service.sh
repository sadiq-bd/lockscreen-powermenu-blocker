#!/system/bin/sh

LOG_FILE=/data/local/tmp/lockscreen-powermenu-blocker.log

exec su -lp 1000 -c "CLASSPATH=/system/usr/share/lockscreen-powermenu-blocker/BlockerService.jar app_process /system/bin BlockerService" >> "$LOG_FILE" 2>&1 &
