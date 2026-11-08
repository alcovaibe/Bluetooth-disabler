#!/usr/bin/env bash
set -euo pipefail

# connectedDebugAndroidTest uninstalls its APK when finishing. Instead, keep
# a real installed debug APK in place until the screenshot bytes are retrieved.
./gradlew assembleDebug assembleDebugAndroidTest

apk="app/build/outputs/apk/debug/app-debug.apk"
test_apk="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
test -s "$apk" && test -s "$test_apk"
adb install -r "$apk"
adb install -r "$test_apk"

mkdir -p app/build/about-previews
adb shell am instrument -w -r \
  -e class com.pulse.bluetoothdisable.ui.AboutScreenScreenshotsInstrumentedTest \
  com.pulse.bluetoothdisable.test/androidx.test.runner.AndroidJUnitRunner \
  | tee app/build/about-previews/instrumentation.txt
grep -q 'OK (1 test)' app/build/about-previews/instrumentation.txt

for name in 01-main-screen.png 02-navigation-drawer.png 03-language-bottom-sheet.png 04-theme-bottom-sheet.png 05-about-app.png; do
  adb exec-out run-as com.pulse.bluetoothdisable cat "files/about-previews/$name" \
    > "app/build/about-previews/$name"
done

# Reject textual adb/run-as errors and corrupted or missing files, even if the
# shell command returned 0. These must be actual frames captured by Android.
python3 - <<'PY'
from pathlib import Path
import struct
directory = Path("app/build/about-previews")
expected = (
    "01-main-screen.png", "02-navigation-drawer.png",
    "03-language-bottom-sheet.png", "04-theme-bottom-sheet.png",
    "05-about-app.png",
)
for name in expected:
    content = (directory / name).read_bytes()
    if len(content) < 4096 or not content.startswith(b"\x89PNG\r\n\x1a\n"):
        raise SystemExit(f"Invalid emulator screenshot: {name}")
    width, height = struct.unpack(">II", content[16:24])
    if width < 300 or height < 500:
        raise SystemExit(f"Unexpected emulator screenshot dimensions: {name} ({width}x{height})")
    print(f"{name}: genuine PNG, {width}x{height}, {len(content)} bytes")
PY
