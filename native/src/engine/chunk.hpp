#pragma once
#include <cstddef>
#include <cstdint>

namespace vc {

// Number of non-zero int16 values (e.g. non-air entries of a chunk section id array).
int64_t count_nonzero(const int16_t* data, size_t n);                  // uses active SIMD level
int64_t count_nonzero_level(const int16_t* data, size_t n, int level);  // level clamped to supported

struct SectionStats {
    int64_t nonzero;   // non-zero values
    int32_t distinct;  // distinct values (including 0 if present), 0..65536
};
SectionStats section_stats(const int16_t* data, size_t n);

}  // namespace vc
