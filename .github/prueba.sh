#!/bin/bash
set -x
APK=android/app/build/outputs/apk/debug/app-debug.apk
adb install -r -g "$APK"
adb logcat -c
adb shell am start -n trade.litlejacob.cloud/.MainActivity
sleep 20
adb exec-out screencap -p > prueba-panel.png
adb shell input keyevent KEYCODE_BACK
sleep 3
adb shell input swipe 500 1600 500 400 400
sleep 2
adb exec-out screencap -p > prueba-panel2.png
adb shell input swipe 500 1600 500 400 400
sleep 2
adb exec-out screencap -p > prueba-panel3.png
adb shell am start -n trade.litlejacob.cloud/.MainActivity
sleep 5
adb shell am start -n trade.litlejacob.cloud/.VisorActivity
sleep 15
adb exec-out screencap -p > prueba-visor.png
{
  echo "=== ERRORES (FATAL) ==="
  adb logcat -d -b crash
  echo
  echo "=== LOG DE LA APP ==="
  adb logcat -d | grep -iE "litlejacob|AndroidRuntime|FATAL|JobScheduler|Exception" | tail -n 300
} > prueba-log.txt
cat prueba-log.txt
exit 0
