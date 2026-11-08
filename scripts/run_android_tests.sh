#!/usr/bin/env bash
set -euo pipefail

mkdir -p app/build/android-ci
capture_logcat() {
  local result=$?
  adb logcat -d -v threadtime > app/build/android-ci/logcat.txt 2>&1 || true
  if (( result != 0 )); then
    tail -n 200 app/build/android-ci/logcat.txt
  fi
  exit "$result"
}
trap capture_logcat EXIT
adb logcat -c || true
adb shell settings put secure show_ime_with_hard_keyboard 0

./gradlew connectedDebugAndroidTest
python3 scripts/verify_android_test_results.py
adb pull /data/local/tmp/calendar-previews app/build/calendar-previews
# Retrieve original PNGs from the debuggable app's private storage.
# exec-out preserves raw binary bytes; run-as limits access to our own test app.
mkdir -p app/build/about-previews
for name in 01-main-screen.png 02-navigation-drawer.png 03-language-bottom-sheet.png 04-theme-bottom-sheet.png 05-about-app.png; do
  adb exec-out run-as com.pulse.bluetoothdisable cat "files/about-previews/$name" > "app/build/about-previews/$name"
  test -s "app/build/about-previews/$name"
done
