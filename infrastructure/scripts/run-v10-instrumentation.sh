#!/usr/bin/env bash
# One shell: android-emulator-runner runs separate script lines in separate shells.
set -uo pipefail
# Variant-specific package for run-as evidence; never probe another package.
case "${1:-academic}" in
  academic)
    variant=Academic
    export COMPANERO_EVIDENCE_PACKAGE=org.companerodeescuela
    ;;
  management)
    variant=Management
    export COMPANERO_EVIDENCE_PACKAGE=org.companerodeescuela.gestion
    ;;
  *)
    echo "Unsupported Compañero test variant" >&2
    exit 2
    ;;
esac
# Record host pressure without environment values, command arguments or user data.
# A vanished emulator is distinct from an assertion failure in the app.
mkdir -p build/v10-runner-diagnostics
storage_log=build/v10-runner-diagnostics/storage.log
{
  echo "timestamp=$(date -u +%FT%TZ)"
  df -h "$PWD" /tmp 2>&1 || true
  df -i "$PWD" /tmp 2>&1 || true
} > "$storage_log"
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
# The emulator action signals readiness at boot completion, but package
# manager/Binder services can still be settling on API 35. Confirm both system
# boot and a responsive package manager before starting Compose instrumentation.
adb wait-for-device
boot_ready=false
for attempt in $(seq 1 60); do
  boot_completed=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
  boot_animation=$(adb shell getprop init.svc.bootanim 2>/dev/null | tr -d '\r' || true)
  if [[ "$boot_completed" == "1" && "$boot_animation" == "stopped" ]] && \
     adb shell cmd package list packages >/dev/null 2>&1; then
    boot_ready=true
    break
  fi
  sleep 2
done
if [[ "$boot_ready" != true ]]; then
  echo "Android framework did not become responsive within 120 seconds" >&2
  adb shell getprop sys.boot_completed >> "$storage_log" 2>&1 || true
  adb shell getprop init.svc.bootanim >> "$storage_log" 2>&1 || true
  adb shell df -h /data >> "$storage_log" 2>&1 || true
  exit 1
fi
{
  echo "emulator_ready_timestamp=$(date -u +%FT%TZ)"
  adb shell getprop ro.build.version.sdk 2>&1 || true
  adb shell getprop ro.build.version.release 2>&1 || true
  adb shell df -h /data 2>&1 || true
  adb shell df -i /data 2>&1 || true
} >> "$storage_log"
# Management cannot run the academic registration/login screenshot suites:
# those tests deliberately assert an academic package and student access.
# Verify real management package, role-select UX, scoped administration,
# navigation and theme instead; no test failures or skips are hidden.
runner_args=()
if [[ "$variant" == Management ]]; then
  runner_args+=(
    "-Pandroid.testInstrumentationRunnerArguments.class=org.companerodeescuela.AppDistributionSmokeTest,org.companerodeescuela.DistributionLoginUiTest,org.companerodeescuela.V12AdministrationUiTest,org.companerodeescuela.V14RoleNavigationUiTest,org.companerodeescuela.InstitutionalClassesUiTest"
  )
fi
./gradlew ":apps:android:app:connected${variant}DebugAndroidTest" --max-workers=1 --no-daemon \
  "${runner_args[@]}" \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8" --console=plain
instrumentation_status=$?
set -e
if [[ "$instrumentation_status" -ne 0 ]]; then
  {
    echo "failure_timestamp=$(date -u +%FT%TZ)"
    adb shell getprop sys.boot_completed 2>&1 || true
    adb shell getprop init.svc.bootanim 2>&1 || true
    adb shell df -h /data 2>&1 || true
    adb shell df -i /data 2>&1 || true
  } >> "$storage_log"
  adb shell dumpsys binder > build/v10-runner-diagnostics/device-binder.txt 2>&1 || true
  adb shell dumpsys meminfo > build/v10-runner-diagnostics/device-memory.txt 2>&1 || true
fi
# Only academic V10 suites produce the complete 19+ screenshot set.
# Management must never claim academic screenshots as its own evidence.
if [[ "$variant" == Academic ]]; then
  python3 infrastructure/scripts/export-v10-evidence.py
fi
if [[ "$instrumentation_status" -eq 0 && "$variant" == Management ]]; then
  if ! api_level=$(adb shell getprop ro.build.version.sdk); then
    echo "Cannot verify emulator API before MFA stability executions" >&2
    exit 1
  fi
  api_level="${api_level//$'\r'/}"
  if [[ ! "$api_level" =~ ^[0-9]+$ ]]; then
    echo "Invalid emulator API before MFA stability executions" >&2
    exit 1
  fi
  if [[ "$api_level" == 35 ]]; then
  # Require two additional real MFA executions after the complete suite passed.
  runner_component=$(adb shell pm list instrumentation | tr -d '\r' | \
    awk -v target="(target=$COMPANERO_EVIDENCE_PACKAGE)" '$2 == target { sub(/^instrumentation:/, "", $1); print $1 }')
  test -n "$runner_component"
  for iteration in 1 2; do
    stability_log="build/v10-runner-diagnostics/mfa-stability-api35-${iteration}.log"
    if ! adb shell am instrument -w -r \
      -e class org.companerodeescuela.V12AdministrationUiTest\#profileNavigatesToRealMfaRepositoryAndConfirmationClearsSetupMaterial \
      "$runner_component" > "$stability_log" 2>&1; then
      instrumentation_status=1
      break
    fi
    cat "$stability_log"
    if ! grep -Fq 'OK (1 test)' "$stability_log" || ! grep -Fq 'INSTRUMENTATION_CODE: -1' "$stability_log"; then
      instrumentation_status=1
      break
    fi
  done
  fi
fi
exit "$instrumentation_status"
