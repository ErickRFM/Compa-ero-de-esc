#!/usr/bin/env bash
# Shared by Codemagic and GitHub. No Apple credentials or signing required.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
mkdir -p build/ios
xcodebuild -version | tee build/ios/xcode-version.txt
java -version
if ! xcodebuild -version | head -n 1 | grep -Eq '^Xcode 26[.]0([.]|$)'; then
  echo "Expected Xcode 26.0.x for Kotlin 2.2.21. Select the pinned image."
  exit 1
fi
if [ "$(uname -m)" = "arm64" ]; then
  sim_arch=arm64
  native_test=iosSimulatorArm64Test
else
  sim_arch=x86_64
  native_test=iosX64Test
fi

./gradlew -p multiplatform :shared:jvmTest ":shared:$native_test" --console=plain --max-workers=2 \
  2>&1 | tee build/ios/kmp-tests.log
python3 - "$native_test" <<'PY'
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
for target in ("jvmTest", sys.argv[1]):
    reports = list((Path("multiplatform/shared/build/test-results") / target).glob("TEST-*.xml"))
    suites = [ET.parse(p).getroot() for p in reports]
    total = sum(int(s.get("tests", 0)) for s in suites)
    failures = sum(int(s.get("failures", 0)) + int(s.get("errors", 0)) for s in suites)
    skipped = sum(int(s.get("skipped", 0)) for s in suites)
    print(f"{target}: tests={total} failures={failures} skipped={skipped}")
    if total < 16 or failures or skipped:
        raise SystemExit(f"{target}: expected executed tests without failures or skips")
PY
plutil -lint iosApp/iosApp.xcodeproj/project.pbxproj iosApp/iosApp/Info.plist iosApp/iosApp/Info-Debug.plist
xcodebuild -list -project iosApp/iosApp.xcodeproj
xcodebuild build \
  -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath build/ios/DerivedData \
  ARCHS="$sim_arch" ONLY_ACTIVE_ARCH=YES \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO \
  2>&1 | tee build/ios/xcode-build.log

app=build/ios/DerivedData/Build/Products/Debug-iphonesimulator/iosApp.app
test -d "$app"
test -f "$app/iosApp"
lipo "$app/iosApp" -verify_arch "$sim_arch"
# Select an available iPhone from the selected Xcode, without assuming its name.
xcrun simctl list devices available --json > build/ios/simulators.json
sim_udid="$(python3 - <<'PY'
import json
with open("build/ios/simulators.json") as f:
    devices = json.load(f)["devices"]
for runtime, items in sorted(devices.items(), reverse=True):
    if ".iOS-" in runtime:
        for device in items:
            if device.get("isAvailable") and device["name"].startswith("iPhone"):
                print(device["udid"])
                raise SystemExit()
raise SystemExit("No available iPhone simulator")
PY
)"
# A simulator selected by Native tests can already be booted.
xcrun simctl boot "$sim_udid" || xcrun simctl list devices booted | grep -F "$sim_udid"
xcrun simctl bootstatus "$sim_udid" -b
xcodebuild test \
  -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -destination "platform=iOS Simulator,id=$sim_udid" \
  -derivedDataPath build/ios/DerivedData -resultBundlePath build/ios/TestResults.xcresult \
  ARCHS="$sim_arch" ONLY_ACTIVE_ARCH=YES \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO \
  2>&1 | tee build/ios/xcode-tests.log
xcrun xcresulttool get test-results summary --path build/ios/TestResults.xcresult > build/ios/xctest-summary.json
python3 - <<'PY'
import json
with open("build/ios/xctest-summary.json") as f:
    result = json.load(f)
if result.get("passedTests", 0) < 3 or result.get("failedTests", 0) or result.get("skippedTests", 0):
    raise SystemExit("XCTest must execute at least three tests without failures or skips")
print("XCTest:", result["passedTests"], "passed")
PY
xcrun simctl install "$sim_udid" "$app"
xcrun simctl launch --terminate-running-process "$sim_udid" org.companerodeescuela.ios | tee build/ios/simulator-launch.txt
sleep 3
xcrun simctl io "$sim_udid" screenshot build/ios/simulator.png
short_sha="$(git rev-parse --short HEAD)"
ditto -c -k --sequesterRsrc --keepParent "$app" "build/ios/companero-ios-simulator-$short_sha.zip"
{
  git rev-parse HEAD
  uname -m
  xcodebuild -version
  echo "target=simulator signing=disabled"
} > build/ios/BUILD_PROVENANCE.txt
