#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
temp="$(mktemp -d /tmp/he-interaction-regressions.XXXXXX)"
fixture_pid=""
cleanup() { if [[ -n "$fixture_pid" ]]; then kill "$fixture_pid" 2>/dev/null || true; wait "$fixture_pid" 2>/dev/null || true; fi; }
trap cleanup EXIT
printf 'Artifacts: %s\n' "$temp"
python3 "$root/Tests/InteractionFixtures.py" "$temp/port" > "$temp/fixture.log" 2>&1 &
fixture_pid=$!
for _ in {1..100}; do [[ -s "$temp/port" ]] && break; sleep 0.05; done
[[ -s "$temp/port" ]]
# Compile the actual model definitions preceding their SwiftUI views, without an app or simulator.
python3 - "$root" "$temp" <<'PY'
from pathlib import Path
import sys
root, temp = map(Path, sys.argv[1:])
for source, boundary, target in [
    ('Features/Library/Components/FilterSheetView.swift', 'public struct FilterSheetView', 'FilterState.swift'),
    ('Features/Creators/CreatorsView.swift', 'public struct CreatorsView', 'CreatorsViewModel.swift'),
]:
    text = (root / source).read_text()
    assert boundary in text, f'Missing extraction boundary: {source}'
    (temp / target).write_text(text.split(boundary)[0])
PY
xcrun swiftc -swift-version 5 -target "$(uname -m)-apple-macosx14.0" \
  -o "$temp/interaction-tests" "$root/Tests/InteractionRegression.swift" \
  "$temp/FilterState.swift" "$temp/CreatorsViewModel.swift" \
  "$root"/Core/Models/*.swift "$root/Core/Storage/Preferences.swift" \
  "$root"/Core/Network/*.swift "$root/Features/Library/LibraryViewModel.swift" \
  > "$temp/compile.log" 2>&1 || { cat "$temp/compile.log"; exit 1; }
"$temp/interaction-tests" "$temp/port" | tee "$temp/results.log"
