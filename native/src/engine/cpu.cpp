#include "cpu.hpp"

#include <atomic>

#if defined(_MSC_VER) && (defined(_M_X64) || defined(_M_IX86))
#include <immintrin.h>
#include <intrin.h>
#define VC_X86 1
#elif defined(__x86_64__) || defined(__i386__)
#define VC_X86 1
#endif

namespace vc {

static int detect() {
    int f = 0;
#if defined(VC_X86)
#if defined(_MSC_VER)
    int r[4];
    __cpuid(r, 0);
    int max_leaf = r[0];
    if (max_leaf >= 1) {
        __cpuid(r, 1);
        if (r[3] & (1 << 26)) f |= CPU_SSE2;
        if (r[2] & (1 << 19)) f |= CPU_SSE41;
        bool osxsave = (r[2] & (1 << 27)) != 0;
        bool avx = (r[2] & (1 << 28)) != 0;
        if (osxsave && avx) {
            unsigned long long xcr0 = _xgetbv(0);
            if ((xcr0 & 6) == 6) {  // OS saves XMM and YMM state
                f |= CPU_AVX;
                if (max_leaf >= 7) {
                    __cpuidex(r, 7, 0);
                    if (r[1] & (1 << 5)) f |= CPU_AVX2;
                }
            }
        }
    }
#else
    __builtin_cpu_init();  // __builtin_cpu_supports("avx*") also verifies OS YMM support
    if (__builtin_cpu_supports("sse2")) f |= CPU_SSE2;
    if (__builtin_cpu_supports("sse4.1")) f |= CPU_SSE41;
    if (__builtin_cpu_supports("avx")) f |= CPU_AVX;
    if (__builtin_cpu_supports("avx2")) f |= CPU_AVX2;
#endif
#endif
    return f;
}

int cpu_features() {
    static const int f = detect();
    return f;
}

int best_simd_level() {
    int f = cpu_features();
    if ((f & CPU_AVX2) && (f & CPU_AVX)) return SIMD_AVX2;
    if (f & CPU_SSE2) return SIMD_SSE2;
    return SIMD_SCALAR;
}

static std::atomic<int> g_level{-1};  // -1 = not yet initialised (auto)

int set_simd_mode(int mode) {
    int best = best_simd_level();
    int level = best;
    if (mode >= 0) level = mode > best ? best : mode;
    g_level.store(level, std::memory_order_relaxed);
    return level;
}

int active_simd_level() {
    int l = g_level.load(std::memory_order_relaxed);
    return l >= 0 ? l : best_simd_level();
}

}  // namespace vc
