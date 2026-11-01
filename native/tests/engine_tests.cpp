#include <cmath>
#include <cstdio>
#include <cstring>
#include <limits>
#include <random>
#include <vector>

#include "chunk.hpp"
#include "cpu.hpp"
#include "cull.hpp"

static int g_fail = 0, g_pass = 0;
#define CHECK(cond, msg)                                           \
    do {                                                           \
        if (cond) { ++g_pass; }                                    \
        else { ++g_fail; std::printf("  [FAIL] %s (line %d)\n", msg, __LINE__); } \
    } while (0)

static int64_t ref_count(const int16_t* p, size_t n) {
    int64_t c = 0;
    for (size_t i = 0; i < n; ++i) if (p[i] != 0) ++c;
    return c;
}

static void test_cpu() {
    int f = vc::cpu_features();
    std::printf("cpu features=0x%x best level=%d\n", f, vc::best_simd_level());
    if (f & vc::CPU_AVX2) CHECK(f & vc::CPU_AVX, "AVX2 implies AVX");
    int best = vc::best_simd_level();
    CHECK(vc::set_simd_mode(0) == 0, "force scalar");
    CHECK(vc::active_simd_level() == 0, "active scalar");
    CHECK(vc::set_simd_mode(99) == best, "request above best is clamped");
    CHECK(vc::set_simd_mode(-1) == best, "auto = best");
}

static void test_count() {
    std::mt19937 rng(1234);
    const size_t sizes[] = {0, 1, 7, 8, 9, 15, 16, 17, 31, 33, 255, 1000, 4096, 4097, 65536 + 5, 98304, 300000};
    for (int density : {0, 1, 10, 50, 100}) {
        for (size_t n : sizes) {
            std::vector<int16_t> v(n + 1);
            for (size_t i = 0; i < v.size(); ++i)
                v[i] = (int)(rng() % 100) < density ? (int16_t)(rng() % 65535 - 32768 + 1) : 0;
            for (size_t off = 0; off < 2; ++off) {  // aligned and 2-byte-misaligned start
                const int16_t* p = v.data() + off;
                size_t m = n;
                int64_t want = ref_count(p, m);
                for (int lvl = 0; lvl <= 2; ++lvl) {
                    int64_t got = vc::count_nonzero_level(p, m, lvl);
                    if (got != want) { std::printf("  mismatch n=%zu lvl=%d off=%zu got=%lld want=%lld\n", n, lvl, off, (long long)got, (long long)want); }
                    CHECK(got == want, "count_nonzero equals reference");
                }
            }
        }
    }
    int16_t edge[3] = {(int16_t)0x8000, 0, 0x7FFF};
    for (int lvl = 0; lvl <= 2; ++lvl) CHECK(vc::count_nonzero_level(edge, 3, lvl) == 2, "INT16_MIN/MAX are non-zero");
    CHECK(vc::count_nonzero(nullptr, 10) == 0, "null pointer -> 0");
    // large all-nonzero array exercises the 4096-block accumulator flush
    std::vector<int16_t> big(4096 * 16 * 3 + 5, 1);
    for (int lvl = 0; lvl <= 2; ++lvl) CHECK(vc::count_nonzero_level(big.data(), big.size(), lvl) == (int64_t)big.size(), "flush path all non-zero");
    std::vector<int16_t> zeros(4096 * 16 * 3 + 5, 0);
    for (int lvl = 0; lvl <= 2; ++lvl) CHECK(vc::count_nonzero_level(zeros.data(), zeros.size(), lvl) == 0, "flush path all zero");
}

static void test_stats() {
    int16_t d[8] = {0, 5, 5, 7, 0, -1, 7, 7};
    vc::SectionStats s = vc::section_stats(d, 8);
    CHECK(s.nonzero == 6, "stats nonzero");
    CHECK(s.distinct == 4, "stats distinct {0,5,7,-1}");
    vc::SectionStats e = vc::section_stats(d, 0);
    CHECK(e.nonzero == 0 && e.distinct == 0, "stats empty");
}

static void make_planes(float* pl) {
    // axis-aligned "frustum" = box [-10,10]^3 : x>=-10, x<=10, ...
    const float p[24] = {1, 0, 0, 10, -1, 0, 0, 10, 0, 1, 0, 10, 0, -1, 0, 10, 0, 0, 1, 10, 0, 0, -1, 10};
    std::memcpy(pl, p, sizeof(p));
}

static void test_cull() {
    float pl[24];
    make_planes(pl);
    float boxes[6 * 5] = {
        -1, -1, -1, 1, 1, 1,          // inside
        20, 0, 0, 22, 1, 1,           // outside +x
        9, 9, 9, 15, 15, 15,          // intersects corner
        -30, -30, -30, -11, 0, 0,     // outside -x
        std::nanf(""), 0, 0, 1, 1, 1  // NaN -> conservative visible
    };
    uint8_t out[5] = {9, 9, 9, 9, 9};
    int32_t vis = vc::frustum_cull(pl, boxes, 5, out);
    CHECK(out[0] == 1 && out[1] == 0 && out[2] == 1 && out[3] == 0 && out[4] == 1, "cull flags");
    CHECK(vis == 3, "cull visible count");
    CHECK(vc::frustum_cull(pl, boxes, 0, out) == 0, "cull n=0");
}

static void test_dist() {
    float pts[6] = {1, 2, 3, -1, 0, 4};
    float out[2];
    vc::distance_sq(pts, 2, 1, 2, 3, out);
    CHECK(out[0] == 0.0f, "dist self");
    CHECK(out[1] == 4.0f + 4.0f + 1.0f, "dist second");
    vc::distance_sq(pts, 0, 0, 0, 0, out);
    CHECK(true, "dist n=0 does not crash");
}

int main() {
    test_cpu();
    test_count();
    test_stats();
    test_cull();
    test_dist();
    std::printf("%d checks passed, %d failed\n", g_pass, g_fail);
    return g_fail == 0 ? 0 : 1;
}
