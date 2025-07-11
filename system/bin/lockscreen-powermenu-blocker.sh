#!/system/bin/sh
keydown_file="/data/local/tmp/power_key_down_time"

is_device_locked() {
  dumpsys window | grep "mDreamingLockscreen=true" >/dev/null
  status=$?
  return $status
}

powerKeyState() {
  getevent -l | while read -r line; do
    if echo "$line" | grep -q "KEY_POWER.*DOWN"; then
      date +%s%3N > "$keydown_file"
    elif echo "$line" | grep -q "KEY_POWER.*UP"; then
      rm -f "$keydown_file"
    fi
  done
}

powerKeyState &

while true; do

  if [ -f "$keydown_file" ]; then
    keydown=$(cat "$keydown_file")
    now=$(date +%s%3N)
    duration=$((now - keydown))
    if [ "$duration" -ge 210 ] && is_device_locked; then
      input keyevent 26
      rm -f "$keydown_file"
    fi
  fi

  sleep 0.05
done
