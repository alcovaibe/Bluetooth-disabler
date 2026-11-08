#!/usr/bin/env bash
# One external download per Platform CI run; all Android jobs consume the verified artifact.
set -euo pipefail

expected_sha="21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607"
wrapper_sha="$(sed -n 's/^distributionSha256Sum=//p' gradle/wrapper/gradle-wrapper.properties)"
if [[ "$wrapper_sha" != "$expected_sha" ]]; then
  echo "::error::Gradle wrapper checksum differs from the pinned CI distribution." >&2
  exit 1
fi

archive="${RUNNER_TEMP:?}/gradle-dist/gradle-9.8.2-bin.zip"
mkdir -p "$(dirname "$archive")"
sources=(
  "https://services.gradle.org/distributions/gradle-9.8.2-bin.zip"
  "https://github.com/gradle/gradle-distributions/releases/download/v9.8.2/gradle-9.8.2-bin.zip"
  "https://downloads.gradle.org/distributions/gradle-9.8.2-bin.zip"
)

for source in "${sources[@]}"; do
  echo "Downloading Gradle from $source"
  rm -f "$archive.part"
  if curl --fail --location --show-error --silent \
    --retry 8 --retry-all-errors --retry-delay 5 --retry-max-time 480 \
    --connect-timeout 20 --max-time 180 \
    --output "$archive.part" "$source"; then
    if printf '%s  %s\n' "$expected_sha" "$archive.part" | sha256sum --check --status; then
      mv "$archive.part" "$archive"
      echo "Verified Gradle 9.8.2 archive: $archive"
      exit 0
    fi
    echo "::warning::Checksum mismatch for $source" >&2
  fi
done

rm -f "$archive.part"
echo "::error::Unable to download verified Gradle 9.8.2 from any official source." >&2
exit 1
