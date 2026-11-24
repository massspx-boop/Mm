# Baseline APK findings

## Package and platform

| Item | Finding |
|---|---|
| Package | `com.ziczac.shooter` |
| Version | `3.6` / code `43` |
| Engine | Unity 2021.3.11f1 |
| Scripting backend | IL2CPP |
| minSdk | 22 |
| targetSdk | 33 |
| Graphics | OpenGL ES 3.0 |
| APK size | 210,989,054 bytes |
| ZIP CRC | PASS |

## Notable contents

| Entry | Approx. uncompressed size |
|---|---:|
| `lib/arm64-v8a/libil2cpp.so` | 55.57 MiB |
| `assets/bin/Data/datapack.unity3d` | 54.18 MiB |
| `lib/armeabi-v7a/libil2cpp.so` | 50.63 MiB |
| `lib/arm64-v8a/libunity.so` | 19.49 MiB |
| `lib/armeabi-v7a/libunity.so` | 14.68 MiB |
| `assets/bin/Data/data.unity3d` | 10.74 MiB |
| `assets/bin/Data/Managed/Metadata/global-metadata.dat` | 9.23 MiB |

Both ARM64 and ARMv7 native libraries are present. Removing ARMv7 could reduce package size but would reduce compatibility; it is not an FPS/RAM optimization and must not be done without checking the target device ABI and testing.

## Unity configuration observed

```ini
gfx-enable-gfx-jobs=1
gfx-enable-native-gfx-jobs=1
wait-for-native-debugger=0
hdr-display-enabled=0
gc-max-time-slice=3
androidStartInFullscreen=1
androidRenderOutsideSafeArea=0
```

Addressables configuration reports `m_maxConcurrentWebRequests=500` and `m_CatalogRequestsTimeout=0`. These values alone do not prove a performance defect; changing them without runtime evidence is not justified.

## What this inspection does not establish

- Actual FPS or frame-time behavior on the realme C30.
- CPU/GPU bottlenecks or peak RAM usage.
- Whether any specific asset is unused.
- Whether changing manifest flags or native libraries would improve performance.
- Whether the game will pass login, multiplayer, anti-cheat, and update checks after re-signing.

A before/after device test and a reproducible build path are required before claiming improvement.
