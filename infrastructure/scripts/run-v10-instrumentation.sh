#!/usr/bin/env bash
# One shell: android-emulator-runner runs separate script lines in separate shells.
set -uo pipefail
# Record host pressure without environment values, command arguments or user data.
# A vanished emulator is distinct from an assertion failure in the app.
mkdir -p build/v10-runner-diagnostics
monitor_pid=""
if [[ "$OSTYPE" == linux* ]]; then
  (
    while true; do
      date -u
      free -m
      ps -eo pid,comm,rss --sort=-rss | awk 'NR <= 15'
      cat /sys/fs/cgroup/memory.events 2>/dev/null || true
      sleep 5
    done
  ) > build/v10-runner-diagnostics/host-resources.log 2>&1 &
  monitor_pid=$!
fi
finish_diagnostics() {
  if [[ -n "$monitor_pid" ]]; then
    kill "$monitor_pid" 2>/dev/null || true
    wait "$monitor_pid" 2>/dev/null || true
    sudo -n dmesg --ctime 2>/dev/null | grep -Ei 'out of memory|oom|killed process|segfault' \
      > build/v10-runner-diagnostics/kernel-events.log || true
    cat build/v10-runner-diagnostics/kernel-events.log
    python3 infrastructure/scripts/read-emulator-crash.py \
      > build/v10-runner-diagnostics/native-exceptions.log 2>&1 || true
    cat build/v10-runner-diagnostics/native-exceptions.log
  fi
}
trap finish_diagnostics EXIT
./gradlew :apps:android:app:connectedDebugAndroidTest --max-workers=2 --no-daemon \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8" --console=plain
instrumentation_status=$?
# UTP otherwise uninstalls the tested APK, deleting its private screenshots.
set -e
mkdir -p build/v10-device-evidence
adb exec-out run-as org.companerodeescuela tar -C files -cf - v10-evidence > build/v10-evidence.tar
tar -xf build/v10-evidence.tar -C build/v10-device-evidence
test "$(find build/v10-device-evidence/v10-evidence -name '*.png' | wc -l)" -ge 19
exit "$instrumentation_status"
