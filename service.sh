#!/system/bin/sh

exec >> /data/local/tmp/lockscreen-powermenu-blocker.log 2>&1

exec su -lp 1000 -c "CLASSPATH=/system/usr/share/lockscreen-powermenu-blocker/BlockerService.jar app_process /system/bin BlockerService"
