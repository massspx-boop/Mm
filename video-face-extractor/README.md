# Video Face Extractor (Android) — V1.0

Android app that samples a selected video, detects every face in sampled frames with Google ML Kit, crops face images, saves them to the Gallery, creates a contact sheet, and packages the result as a ZIP.

## V1.0 behavior

- **Face mode:** all faces found in each sampled frame.
- **Output:** individual JPG files + one contact sheet JPG + ZIP archive.
- **Processing:** balanced sampling every 800 ms; frames are downscaled to a maximum side of 960 px before detection.
- **Duplicate reduction:** average-hash comparison suppresses visually similar crops. This is not person identification.
- **Privacy:** video processing is on-device. No video or face images are uploaded to a server.
- **Offline model:** bundled ML Kit dependency; the model is included in the APK.

## Build on GitHub

1. Open the repository and enter the `video-face-extractor` folder.
2. Open **Actions** and run **Android APK Build** (or push a commit affecting this folder).
3. Download the `video-face-extractor-debug-apk` artifact from the completed workflow.
4. Install the APK on Android 11 or later. Allow installation from the browser/file manager if Android asks.

## Build locally

Requirements: JDK 17 and Android SDK Platform 35.

```bash
cd video-face-extractor
gradle wrapper --gradle-version 8.9
./gradlew :app:assembleDebug
```

APK output:
`app/build/outputs/apk/debug/app-debug.apk`

## Notes and limitations

- Face detection is not perfect. Small, blurred, side-facing, occluded, or very dark faces may be missed.
- The app samples one frame every 0.8 seconds; a face visible only between samples can be missed.
- V1.0 detects faces, not identities. It does not name people or verify who they are.
- The current version processes in the foreground. Keep the app open during extraction.
- Very long videos can take time. The safety cap is 3,600 sampled frames (about 48 minutes at this interval).
- Gallery images are saved under **Pictures/VideoFaceExtractor** on Android 10+.
- ZIP archives are stored in the app-specific Downloads directory and can be shared from the app.

## Tech

- Kotlin / Android SDK
- Google ML Kit Face Detection 16.1.7 (bundled model)
- Gradle 8.9 / Android Gradle Plugin 8.7.3 / Kotlin 2.0.21
