#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLCHAIN="$ROOT/.toolchain"

export JAVA_HOME="$TOOLCHAIN/jdk"
export ANDROID_HOME="$TOOLCHAIN/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$TOOLCHAIN/gradle-home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

TASK="${1:-assembleRelease}"
shift || true

exec "$TOOLCHAIN/gradle-8.13/bin/gradle" \
  --project-dir "$ROOT/dos-print" \
  --console=plain \
  "$@" \
  "$TASK"