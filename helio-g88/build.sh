#!/usr/bin/env bash
# Winlator Helio G88 Edition — Build Script
# Usage: ./helio-g88/build.sh [--release|--debug]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

BUILD_TYPE="${1:---debug}"

echo "============================================================"
echo " Building Winlator Helio G88 / Mali-G52 Optimized Edition"
echo "============================================================"
echo "Project Root: $PROJECT_ROOT"
echo "Build Type  : $BUILD_TYPE"
echo ""

cd "$PROJECT_ROOT/app"

# Check Android SDK
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    echo "ERROR: ANDROID_HOME or ANDROID_SDK_ROOT is not set."
    echo "Please set ANDROID_HOME to your Android SDK path."
    exit 1
fi

# Ensure submodules are updated
echo "--> Syncing submodules..."
git submodule update --init --recursive

# Select Gradle task
if [ "$BUILD_TYPE" = "--release" ]; then
    GRADLE_TASK="assembleRelease"
else
    GRADLE_TASK="assembleDebug"
fi

echo "--> Running Gradle $GRADLE_TASK..."
./gradlew $GRADLE_TASK --stacktrace

APK_PATH="$(find "$PROJECT_ROOT/app/app/build/outputs/apk" -name "*.apk" | head -n 1)"

if [ -f "$APK_PATH" ]; then
    echo ""
    echo "============================================================"
    echo " BUILD SUCCESSFUL!"
    echo " Output APK: $APK_PATH"
    echo "============================================================"
else
    echo "ERROR: APK build finished but output APK not found."
    exit 1
fi
