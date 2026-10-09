#!/usr/bin/env bash
# One shell: android-emulator-runner runs separate script lines in separate shells.
set -uo pipefail
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
