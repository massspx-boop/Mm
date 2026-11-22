# Game Mode V7

Android app project for Android 6.0+ (target SDK 32).

## Features
- Lists launchable apps installed on the phone; select the games to monitor.
- Uses Android Usage Access to detect a selected game coming to foreground.
- Shows a small draggable overlay only while a selected game is foreground.
- Displays Android-reported available/total RAM and battery temperature when available.
- Runs a foreground notification while monitoring.

## Setup
1. Install the debug APK from the GitHub Actions artifact.
2. Open Game Mode V7 and grant **Display over other apps** and **Usage access**.
3. Select your games and tap **Start**.
4. Return to Home and launch a selected game.

## Honest limitations
This app does not root the phone, change CPU governor/GPU clocks, kill other apps, or promise FPS gains. Android does not provide ordinary apps with unrestricted control of those features. RAM and battery temperature values are informational; battery temperature is not CPU temperature. Usage Access and overlay permissions are required for automatic detection.

## Build
GitHub Actions builds `app/build/outputs/apk/debug/app-debug.apk` and uploads it as the `Game-Mode-V7-debug` artifact.