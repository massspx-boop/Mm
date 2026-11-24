# Carnage Wars 3.6 APK investigation

This folder is isolated from the existing Game Mode V7 and other Android projects in this repository.

## Current status

- Source APK inspected locally: `CarnageWars.apk`
- Package: `com.ziczac.shooter`
- Version: `3.6` (versionCode `43`)
- Engine: Unity `2021.3.11f1`, IL2CPP
- Minimum Android: API 22 (Android 5.1)
- Target Android: API 33
- OpenGL ES requirement: 3.0
- APK size: 210,989,054 bytes (about 201.21 MiB)
- ZIP integrity / CRC check: passed
- SHA-256: `d82eb356d98a2303014bab81d613ca4f7cd803df6689bf610ae31e7474851727`

## Read-only audit workflow

The manual GitHub Actions workflow `.github/workflows/carnage-wars-audit.yml` accepts a direct HTTPS download URL to the original APK, runs the read-only audit script, and stores Markdown/JSON reports as a workflow artifact. The APK itself is not committed to Git.

To run it: open the repository's **Actions** tab, choose **Carnage Wars APK Audit**, select **Run workflow**, and provide a direct HTTPS URL to the APK. A private ChatGPT attachment URL is not accessible to GitHub Actions; the APK must be placed at a reachable direct HTTPS download location first.

## Important limitation

The supplied APK is a compiled Unity IL2CPP application. The Unity project/source code is not available in this repository. Static inspection alone cannot safely change render quality, game logic, memory allocation, CPU load, or frame pacing. Blindly deleting Unity assets or changing native libraries can make the game fail to launch or break maps, models, multiplayer, or anti-cheat.

The original APK is about 201 MiB, above GitHub's 100 MB regular-file limit, and has not been uploaded to this repository. No modified APK has been produced yet.

## Work plan

1. Keep all Carnage Wars work inside this folder.
2. Preserve the original APK and its SHA-256 as the baseline.
3. Run the read-only audit workflow on the same APK.
4. Make only changes supported by evidence; do not remove assets or ABIs without compatibility testing.
5. Build/sign with Android build tools and verify installation and launch on the target phone.
6. Compare FPS, frame-time stability, RAM, CPU, heat, and crashes before and after on the same device and settings.

## Target-device note

The intended test device is a realme C30 with 2 GB RAM and Android 11 Go. The APK's current minimum Android version is compatible on paper, but compatibility and performance must be confirmed on the actual device.

## Expected output

A verified APK is the goal, but this folder currently contains investigation notes and a read-only audit workflow. A successful GitHub commit or static audit is not evidence of an optimized game APK.
