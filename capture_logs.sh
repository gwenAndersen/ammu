#!/bin/bash

# Clear existing logcat buffer
adb logcat -c

# Start capturing logcat
adb logcat -d -v time -s InboxViewModel > logcat_capture.txt

echo "Logcat captured to logcat_capture.txt"