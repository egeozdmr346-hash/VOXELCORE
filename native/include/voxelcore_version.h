#pragma once

// Must match gradle.properties mod_version (CI checks this).
// Checked against by SodiumCompat-style diagnostics in the settings UI so a stale
// native library (old .dll sitting in a mods folder) is visible to the user, not silent.
#define VOXELCORE_NATIVE_VERSION "0.1.3"
