#include "chunk.hpp"

#include <cstring>

#include "cpu.hpp"

#if defined(_MSC_VER) && (defined(_M_X64) || defined(_M_IX86))
#include <immintrin.h>
#define VC_X86 1
#define VC_AVX2_FN
#elif defined(__x86_64__) || defined(__i386__)
#include <immintrin.h>
#define VC_X86 1
#define VC_AVX2_FN __attribute__((target("avx2")))
#endif

namespace vc {

static int64_t count_nonzero_scalar(const int16_t* p, size_t n) {
    int64_t c = 0;
    for (size_t i = 0; i < n; ++i) c += (p[i] != 0);
    return c;
}

#if defined(VC_X86)
// Counts zero lanes per 16-bit accumulator (cmpeq gives -1 for equal => subtracting adds 1).
// Each accumulator lane gains at most 1 per vector, flushed every 4096 vectors (< 32768).
static int64_t count_nonzero_sse2(const int16_t* p, size_t n) {
    size_t i = 0;
    int64_t zeros = 0;
    const __m128i zero = _mm_setzero_si128();
    const __m128i ones = _mm_set1_epi16(1);
    while (i + 8 <= n) {
        size_t blocks = (n - i) / 8;
        if (blocks > 4096) blocks = 4096;
        __m128i acc = _mm_setzero_si128();
        for (size_t b = 0; b < blocks; ++b, i += 8) {
            __m128i v = _mm_loadu_si128(reinterpret_cast<const __m128i*>(p + i));
            acc = _mm_sub_epi16(acc, _mm_cmpeq_epi16(v, zero));
        }
        __m128i s = _mm_madd_epi16(acc, ones);
        alignas(16) int32_t t[4];
        _mm_store_si128(reinterpret_cast<__m128i*>(t), s);
        zeros += static_cast<int64_t>(t[0]) + t[1] + t[2] + t[3];
    }
    for (; i < n; ++i) zeros += (p[i] == 0);
    return static_cast<int64_t>(n) - zeros;
}

VC_AVX2_FN static int64_t count_nonzero_avx2(const int16_t* p, size_t n) {
    size_t i = 0;
    int64_t zeros = 0;
    const __m256i zero = _mm256_setzero_si256();
    const __m256i ones = _mm256_set1_epi16(1);
    while (i + 16 <= n) {
        size_t blocks = (n - i) / 16;
        if (blocks > 4096) blocks = 4096;
        __m256i acc = _mm256_setzero_si256();
        for (size_t b = 0; b < blocks; ++b, i += 16) {
            __m256i v = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(p + i));
            acc = _mm256_sub_epi16(acc, _mm256_cmpeq_epi16(v, zero));
        }
        __m256i s = _mm256_madd_epi16(acc, ones);
        alignas(32) int32_t t[8];
        _mm256_store_si256(reinterpret_cast<__m256i*>(t), s);
        for (int k = 0; k < 8; ++k) zeros += t[k];
    }
    for (; i < n; ++i) zeros += (p[i] == 0);
    return static_cast<int64_t>(n) - zeros;
}
#endif

int64_t count_nonzero_level(const int16_t* data, size_t n, int level) {
    if (data == nullptr || n == 0) return 0;
    int best = best_simd_level();
    if (level > best) level = best;
#if defined(VC_X86)
    if (level >= SIMD_AVX2) return count_nonzero_avx2(data, n);
    if (level >= SIMD_SSE2) return count_nonzero_sse2(data, n);
#endif
    return count_nonzero_scalar(data, n);
}

int64_t count_nonzero(const int16_t* data, size_t n) {
    return count_nonzero_level(data, n, active_simd_level());
}

static inline int popcount64(uint64_t x) {
    x = x - ((x >> 1) & 0x5555555555555555ULL);
    x = (x & 0x3333333333333333ULL) + ((x >> 2) & 0x3333333333333333ULL);
    x = (x + (x >> 4)) & 0x0F0F0F0F0F0F0F0FULL;
    return static_cast<int>((x * 0x0101010101010101ULL) >> 56);
}

SectionStats section_stats(const int16_t* data, size_t n) {
    SectionStats s{0, 0};
    if (data == nullptr || n == 0) return s;
    uint64_t bits[1024];  // 65536-bit presence set (8 KB, stack)
    std::memset(bits, 0, sizeof(bits));
    for (size_t i = 0; i < n; ++i) {
        uint32_t v = static_cast<uint16_t>(data[i]);
        bits[v >> 6] |= (1ULL << (v & 63));
    }
    s.nonzero = count_nonzero(data, n);
    int32_t d = 0;
    for (int k = 0; k < 1024; ++k) d += popcount64(bits[k]);
    s.distinct = d;
    return s;
}

}  // namespace vc
