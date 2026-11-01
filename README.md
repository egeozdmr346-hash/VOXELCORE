# VoxelCore 0.1.3

Client-side Fabric mod for Minecraft 1.20.1 (Java 17). Java core + optional C++/JNI library.

## Status legend
**W** = written, **C** = compiled, **T** = passed automated test, **M** = tested inside Minecraft.

| Feature | W | C | T | M |
|---|---|---|---|---|
| Config, ModuleRegistry, EventBus | yes | yes (plain javac, JDK 21 `-source 17`) | yes | no |
| NativeBridge enabled flag / Java fallback | yes | yes | yes (Linux, JNI call counter) | no |
| NativeLoader safe failure | yes | yes | partly (missing-resource path) | no |
| OverlayStats (FPS/mem text) | yes | yes | yes | no |
| SodiumCompat labels | yes | yes (with stubs) | yes | no |
| HudOverlay, VoxelCoreScreen, VoxelCoreClient, Shift+V | yes | **no** | no | **no** |
| C++ library on Windows (.dll) | yes | **no** | no | no |
| Gradle/Loom build, jar, Sodium 0.5.11 co-run | yes | **no** | no | **no** |
| Sodium integration | **not implemented** (detection only) | | | |

Nothing marked "no" has been verified. CI (`.github/workflows/build.yml`) is written but has not run. 0.1.3 changes: version bump, Loom pinned to a release, CI extended (MSVC DLL, JNI export check, source zip, jar content checks).

## Build
Requires JDK 17, internet. Native part is optional (`-PbuildNative=auto|false|true`, default auto: built only if cmake
and a C++ compiler work; never fails the build in auto mode). Native files from older builds are wiped before each build.

Wrapper: this repo ships `gradle/wrapper/gradle-wrapper.properties` (Gradle 8.7). Generate the standard wrapper once
with an installed Gradle: `gradle wrapper --gradle-version 8.7` (this creates `gradlew`, `gradlew.bat`,
`gradle-wrapper.jar`; commit them). CI does this and publishes them as an artifact.

```
gradlew.bat build        # Windows
./gradlew build          # Linux/macOS
```
Output: `build/libs/voxelcore-0.1.3.jar` (ignore `-sources`). Native filenames: `voxelcore_native_windows_x64.dll`,
`voxelcore_native_linux_x64.so`, `voxelcore_native_macos.dylib` (a native lib only runs on the OS/CPU it was built for;
Linux/Windows loader accepts x86_64 only, others use Java fallback).

## SKLauncher install
1. Create a Fabric 1.20.1 profile (Fabric Loader >= 0.15.11) in SKLauncher.
2. Put `voxelcore-0.1.3.jar` and Fabric API 0.92.2+1.20.1 into the profile's `mods` folder. Sodium 0.5.11 is optional.
3. Start the game; press Shift+V in-world to open settings. Config: `config/voxelcore/voxelcore.properties`.

## Licensing
VoxelCore is MIT (see LICENSE). No Sodium code is included or copied; Sodium is only a compile-time soft dependency
and remains under its own license.

## Known issues / missing
- Everything marked unverified above; first real build may reveal Yarn-mapping compile errors.
- Loom is pinned as `1.6.12` (release, replaces the old snapshot); NOT verified here: no network, CI has not run.
- Native code only counts non-zero shorts; no performance advantage over Java has been measured.
- No Sodium integration, no mixins, no Render/Chunk/Memory modules.
- HUD uses its own frame counter (not vanilla's FPS value).
