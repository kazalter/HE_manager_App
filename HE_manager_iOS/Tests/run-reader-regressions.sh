#!/bin/bash
set -euo pipefail
device="${1:?Pass a booted iOS simulator UDID}"
root="$(cd "$(dirname "$0")/.." && pwd)"
build="$(mktemp -d /tmp/he-reader-regression.XXXXXX)"
sdk="$(xcrun --sdk iphonesimulator --show-sdk-path)"
arch="$(uname -m)"
app="$build/ReaderRegression.app"
mkdir -p "$app"
cp "$root/Tests/ReaderRegressionInfo.plist" "$app/Info.plist"
xcrun swiftc -swift-version 5 -parse-as-library -sdk "$sdk" \
  -target "${arch}-apple-ios17.0-simulator" \
  "$root/Features/Reader/WebtoonZoomContainer.swift" \
  "$root/Core/DesignSystem/Components/AsyncCoverImage.swift" \
  "$root/Core/DesignSystem/OPTheme.swift" \
  "$root/Tests/ReaderRegressionApp.swift" -o "$app/ReaderRegression"
codesign --force --sign - "$app"
xcrun simctl install "$device" "$app"
run_id="${build##*.}"
xcrun simctl launch --terminate-running-process "$device" com.hemanager.reader-regression "$run_id"
container="$(xcrun simctl get_app_container "$device" com.hemanager.reader-regression data)"
report="$container/Documents/results-$run_id.log"
for attempt in {1..30}; do
  if test -f "$report" && rg -q 'HE_READER_TESTS_' "$report"; then break; fi
  sleep 1
done
cp "$report" "$build/results.log"
sed -n '1,100p' "$build/results.log"
rg -q HE_READER_TESTS_OK "$build/results.log"
echo "Regression results: $build/results.log"
