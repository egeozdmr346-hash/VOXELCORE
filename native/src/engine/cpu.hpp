#pragma once
#include <cstdint>

namespace vc {

constexpr int CPU_SSE2 = 1;
constexpr int CPU_SSE41 = 2;
constexpr int CPU_AVX = 4;
constexpr int CPU_AVX2 = 8;

constexpr int SIMD_SCALAR = 0;
constexpr int SIMD_SSE2 = 1;
constexpr int SIMD_AVX2 = 2;

// Bitmask of CPU_* flags, detected once (cpuid + OS XSAVE check for AVX). 0 on non-x86.
int cpu_features();

// Highest SIMD level this CPU supports AND this build contains.
int best_simd_level();

// mode: -1 = auto (best), 0..2 = request that level (clamped to what is supported).
// Returns the level now active.
int set_simd_mode(int mode);
int active_simd_level();

}  // namespace vc
