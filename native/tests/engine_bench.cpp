// Pure C++ engine benchmark (no JNI). Shows what the native kernels cost in isolation;
// the Java side has its own benchmark that includes JNI overhead.
#include <algorithm>
#include <chrono>
#include <cstdio>
#include <random>
#include <vector>

#include "chunk.hpp"
#include "cpu.hpp"
#include "cull.hpp"

using clk = std::chrono::steady_clock;
static volatile int64_t g_sink;

template <class F>
static double median_ns(F f, int reps) {
    std::vector<double> t;
    for (int i = 0; i < 5; ++i) f();  // warmup
    for (int i = 0; i < reps; ++i) {
        auto a = clk::now();
        f();
        auto b = clk::now();
        t.push_back(std::chrono::duration<double, std::nano>(b - a).count());
    }
    std::sort(t.begin(), t.end());
    return t[t.size() / 2];
}

int main() {
    std::mt19937 rng(7);
    std::printf("cpu features=0x%x best simd level=%d\n", vc::cpu_features(), vc::best_simd_level());
    const char* names[] = {"scalar", "sse2", "avx2"};
    for (size_t n : {4096u, 98304u, 1000000u}) {
        std::vector<int16_t> v(n);
        for (auto& x : v) x = (rng() % 100) < 30 ? (int16_t)(rng() % 500 + 1) : 0;  // ~30% solid
        for (int lvl = 0; lvl <= vc::best_simd_level(); ++lvl) {
            double ns = median_ns([&] { g_sink = vc::count_nonzero_level(v.data(), n, lvl); }, 200);
            std::printf("count_nonzero n=%-8zu %-6s %10.0f ns  %6.2f GB/s\n", n, names[lvl], ns, n * 2.0 / ns);
        }
    }
    {
        const int n = 10000;
        float pl[24] = {1, 0, 0, 100, -1, 0, 0, 100, 0, 1, 0, 100, 0, -1, 0, 100, 0, 0, 1, 100, 0, 0, -1, 100};
        std::vector<float> boxes(n * 6);
        std::vector<uint8_t> out(n);
        for (int i = 0; i < n; ++i) {
            float x = (rng() % 400) - 200.f, y = (rng() % 400) - 200.f, z = (rng() % 400) - 200.f;
            float* b = &boxes[i * 6];
            b[0] = x; b[1] = y; b[2] = z; b[3] = x + 1; b[4] = y + 1; b[5] = z + 1;
        }
        double ns = median_ns([&] { g_sink = vc::frustum_cull(pl, boxes.data(), n, out.data()); }, 200);
        std::printf("frustum_cull n=%d %10.0f ns  %.1f ns/box\n", n, ns, ns / n);
    }
    {
        const int n = 100000;
        std::vector<float> pts(n * 3), out(n);
        for (auto& x : pts) x = (float)(rng() % 1000);
        double ns = median_ns([&] { vc::distance_sq(pts.data(), n, 1, 2, 3, out.data()); g_sink = (int64_t)out[0]; }, 200);
        std::printf("distance_sq n=%d %10.0f ns  %.2f ns/point\n", n, ns, ns / n);
    }
    return 0;
}
