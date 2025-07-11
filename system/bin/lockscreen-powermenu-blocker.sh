#!/system/bin/sh
keydown_file="/data/local/tmp/power_key_down_time"

find_power_key_device() {
  for dev in /dev/input/event*; do
    if getevent -il "$dev" 2>/dev/null | grep -q "KEY_POWER"; then
      echo "$dev"
      return 0
    fi
  done
  return 1
}

is_device_locked() {
  dumpsys window | grep "mDreamingLockscreen=true" >/dev/null
  return $?
}

POWER_EVENT_DEVICE=$(find_power_key_device)
[ -z "$POWER_EVENT_DEVICE" ] && exit 1

powerKeyState() {
  getevent -l "$POWER_EVENT_DEVICE" | while read -r line; do
    case "$line" in
      *KEY_POWER*DOWN*)
        date +%s%3N > "$keydown_file"
        ;;
      *KEY_POWER*UP*)
        rm -f "$keydown_file"
        ;;
    esac
  done
}

powerKeyState &

while true; do
  if [ -f "$keydown_file" ]; then
    keydown=$(cat "$keydown_file")
    now=$(date +%s%3N)
    duration=$((now - keydown))
    if [ "$duration" -ge 210 ]; then
      if is_device_locked; then
        input keyevent 26
        rm -f "$keydown_file"
      fi
    fi
  fi
  sleep 0.05
done
