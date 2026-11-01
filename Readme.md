# VoxelCore 0.2.0

A client-side Fabric mod for Minecraft 1.20.1 (Java 17), combining a Java core with an optional C++/JNI native library.

## Status Legend

- **W** = Written
- **C** = Compiled
- **T** = Passed automated test
- **M** = Tested inside Minecraft

| Feature | W | C | T | M |
|---|---|---|---|---|
| Config, ModuleRegistry, EventBus | Yes | Yes (plain javac, JDK 21 `-source 17`) | Yes | No |
| NativeBridge enabled flag / Java fallback | Yes | Yes | Yes (Linux, JNI call counter) | No |
| NativeLoader safe failure | Yes | Yes | Partly (missing-resource path) | No |
| OverlayStats (FPS/memory text) | Yes | Yes | Yes | No |
| SodiumCompat labels | Yes | Yes (with stubs) | Yes | No |
| HudOverlay, VoxelCoreScreen, VoxelCoreClient, Shift+V | Yes | No | No | No |
| C++ library on Windows (.dll) | Yes | No | No | No |
| Gradle/Loom build, JAR, Sodium 0.5.11 co-run | Yes | No | No | No |
| Sodium integration | Not implemented (detection only) | — | — | — |

Nothing marked "No" has been verified. CI (`.github/workflows/build.yml`) is written but has not run.

### Version 0.1.3 Changes

- Version bump to 0.1.3.
- Loom pinned to a stable release.
- CI extended with MSVC DLL build, JNI export checks, source ZIP generation, and JAR content checks.

## Build Requirements

- JDK 17
- Internet connection
- Gradle wrapper (Gradle 8.7)
- CMake and a C++ compiler (optional, for native builds)

The native build is optional:

`-PbuildNative=auto|false|true`

Default: `auto`. The native library is built only if CMake and a compatible C++ compiler are available. In auto mode, the build does not fail if native compilation is unavailable.

Native files from previous builds are removed before each build.

### Gradle Wrapper

This repository includes `gradle/wrapper/gradle-wrapper.properties` configured for Gradle 8.7.

To generate the standard wrapper files using an installed Gradle version:

```bash
gradle wrapper --gradle-version 8.7
```

This generates `gradlew`, `gradlew.bat`, and `gradle-wrapper.jar`. Commit these files to the repository.

CI generates the wrapper and publishes it as an artifact.

### Build Commands

**Windows:**

```bat
gradlew.bat build
```

**Linux/macOS:**

```bash
./gradlew build
```

### Build Output

The expected JAR output is:

```text
build/libs/voxelcore-0.1.3.jar
```

Ignore the `-sources` JAR when installing the mod.

Expected native library filenames:

- Windows: `voxelcore_native_windows_x64.dll`
- Linux: `voxelcore_native_linux_x64.so`
- macOS: `voxelcore_native_macos.dylib`

Native libraries only run on the operating system and CPU architecture they were built for. The Linux and Windows loaders accept x86_64 only; unsupported platforms use the Java fallback.

## Installation

1. Install Minecraft 1.20.1.
2. Install Fabric Loader version 0.15.11 or newer.
3. Install Fabric API version `0.92.2+1.20.1`.
4. Place `voxelcore-0.1.3.jar` into your Minecraft installation's `mods` folder.
5. Optionally install Sodium 0.5.11.
6. Launch Minecraft using your Fabric profile.

If the in-game interface is available, press **Shift + V** to open the VoxelCore settings screen.

Configuration file:

```text
config/voxelcore/voxelcore.properties
```

## Compatibility

- Minecraft: 1.20.1
- Java: 17
- Mod loader: Fabric
- Fabric Loader: 0.15.11 or newer
- Fabric API: 0.92.2+1.20.1
- Sodium: 0.5.11 (optional; detection only, no direct integration)

## Licensing

VoxelCore is distributed under the **VoxelCore Custom Source-Available License**.

- Personal and non-commercial use is permitted under the license terms.
- Commercial use requires prior written permission from the copyright holder.
- Approved commercial licensees are subject to a 10% net-profit royalty unless a separate written agreement states otherwise.

See the `LICENSE` file for the complete terms.

No Sodium source code is included or copied. Sodium remains a separate project under its own license.

## Known Issues and Limitations

- Features marked as unverified have not yet been confirmed through a complete build or in-game test.
- Loom is pinned to version `1.6.12`, but this configuration has not yet been verified in CI.
- The first complete build may reveal Yarn mapping or compilation errors.
- Native code currently only counts non-zero shorts. No measurable performance advantage over Java has been demonstrated.
- Sodium integration is not implemented; only detection is available.
- No mixins or dedicated Render, Chunk, or Memory optimization modules are implemented.
- The HUD uses its own frame counter rather than Minecraft's vanilla FPS value.
- The complete in-game interface and native Windows library have not yet been verified.

## Project Goals

VoxelCore aims to provide a modular performance optimization framework for Minecraft, combining Java-based functionality with optional native C++ acceleration.

Future development may include more efficient memory processing, CPU optimization, rendering support, chunk processing, and broader native acceleration.
