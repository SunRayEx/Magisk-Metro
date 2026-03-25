#!/bin/bash
# check_debuggable.sh
# CI script to verify that the release APK is not debuggable

APK_PATH=$1

if [ -z "$APK_PATH" ]; then
    echo "Usage: $0 <path_to_apk>"
    exit 1
fi

if [ ! -f "$APK_PATH" ]; then
    echo "Error: File $APK_PATH not found!"
    exit 1
fi

echo "Checking if APK is debuggable..."
# Use aapt to dump badging and grep for application-debuggable
# Or just use unzip and parse AndroidManifest.xml (requires androguard or similar)
# Since aapt is usually in Android SDK build-tools:
AAPT_CMD=$(find $ANDROID_HOME/build-tools -name "aapt" | sort -r | head -n 1)

if [ -z "$AAPT_CMD" ]; then
    echo "Warning: aapt not found, falling back to basic strings check"
    # Basic check - not 100% accurate but better than nothing
    DEBUG_FLAG=$(unzip -p "$APK_PATH" AndroidManifest.xml | strings | grep -i "debuggable")
    if [ ! -z "$DEBUG_FLAG" ]; then
        echo "FAIL: Found 'debuggable' in AndroidManifest.xml. Build is rejected!"
        # Send alert email
        # echo "Release build is debuggable" | mail -s "CI Alert: Debuggable Release APK" admin@example.com
        exit 1
    fi
else
    IS_DEBUGGABLE=$($AAPT_CMD dump badging "$APK_PATH" | grep "application-debuggable")
    if [ ! -z "$IS_DEBUGGABLE" ]; then
        echo "FAIL: APK is debuggable (application-debuggable flag found). Build is rejected!"
        # Send alert email
        # echo "Release build is debuggable" | mail -s "CI Alert: Debuggable Release APK" admin@example.com
        exit 1
    fi
fi

echo "SUCCESS: APK is not debuggable. Safe to flash in Recovery."
exit 0
