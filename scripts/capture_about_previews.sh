#!/usr/bin/env bash
set -euo pipefail

# Run only the screenshot scenario; regular compatibility tests remain in Platform CI.
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.pulse.bluetoothdisable.ui.AboutScreenScreenshotsInstrumentedTest

mkdir -p app/build/about-previews
for name in 01-main-screen.png 02-navigation-drawer.png 03-language-bottom-sheet.png 04-theme-bottom-sheet.png 05-about-app.png; do
  adb exec-out run-as com.pulse.bluetoothdisable cat "files/about-previews/$name" \
    > "app/build/about-previews/$name"
  test -s "app/build/about-previews/$name"
done
