#!/usr/bin/env bash
# One shell: android-emulator-runner runs separate script lines in separate shells.
set -uo pipefail
# Record host pressure without environment values, command arguments or user data.
# A vanished emulator is distinct from an assertion failure in the app.
mkdir -p build/v10-runner-diagnostics
monitor_pid=""
trace_launcher_pid=""
core_file=""
emulator_executable=""
if [[ "$OSTYPE" == linux* ]]; then
  # Only signal/exit metadata from the native main process; no syscall data,
  # process arguments, environment, raw memory or app network payloads.
  emulator_pid=$(pgrep -x qemu-system-x86 | head -n 1 || true)
  if [[ "$emulator_pid" =~ ^[0-9]+$ ]] && command -v strace >/dev/null; then
    if [[ "${GITHUB_ACTIONS:-false}" == true ]]; then
      # Private ephemeral core; only sanitized backtrace metadata is uploaded.
      mkdir -p build/v10-native-cores
      core_file="$PWD/build/v10-native-cores/core.$emulator_pid"
      emulator_executable=$(readlink -f "/proc/$emulator_pid/exe")
      sudo -n sysctl -w "kernel.core_pattern=$PWD/build/v10-native-cores/core.%p" >/dev/null || true
      sudo -n prlimit --pid "$emulator_pid" --core=unlimited || true
    fi
    sudo -n bash -c '
      echo "$$" > build/v10-runner-diagnostics/tracer.pid
      exec strace -tt -e trace=none -e signal=all -p "$1" \
        -o build/v10-runner-diagnostics/emulator-signals.log
    ' v10-native-tracer "$emulator_pid" \
      > build/v10-runner-diagnostics/tracer-status.log 2>&1 &
    trace_launcher_pid=$!
  fi
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
  if [[ -n "$trace_launcher_pid" ]]; then
    if [[ -f build/v10-runner-diagnostics/tracer.pid ]]; then
      read -r tracer_pid < build/v10-runner-diagnostics/tracer.pid
      # Interrupt the tracer itself: strace detaches and leaves QEMU running.
      [[ "$tracer_pid" =~ ^[0-9]+$ ]] && sudo -n kill -INT "$tracer_pid" 2>/dev/null || true
    fi
    wait "$trace_launcher_pid" 2>/dev/null || true
    cat build/v10-runner-diagnostics/emulator-signals.log 2>/dev/null || true
    if [[ -n "$core_file" && -n "$emulator_executable" ]]; then
      python3 infrastructure/scripts/read-emulator-crash.py \
        --core "$core_file" --executable "$emulator_executable" \
        > build/v10-runner-diagnostics/native-backtrace.log 2>&1 || true
      cat build/v10-runner-diagnostics/native-backtrace.log
    fi
  fi
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
