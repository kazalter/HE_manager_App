#!/usr/bin/env sh
set -eu
TASK_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TASK_CLASSES=$(mktemp -d)
trap 'rm -rf "$TASK_CLASSES"' EXIT
javac -encoding UTF-8 -d "$TASK_CLASSES" \
  "$TASK_ROOT/app/src/main/java/com/hemanager/mobile/feature/tools/WebEndpoint.java" \
  "$TASK_ROOT/app/src/main/java/com/hemanager/mobile/feature/tools/DownloadNames.java" \
  "$TASK_ROOT/tests/WebEndpointTest.java"
java -cp "$TASK_CLASSES" WebEndpointTest
