#pragma once
#include <cstdint>

namespace vc {

// planes: 6 planes * (nx, ny, nz, d); a point p is inside a plane when n.p + d >= 0.
// boxes:  n * (minX, minY, minZ, maxX, maxY, maxZ).
// out[i] = 1 if box i is inside or intersects the frustum (conservative: NaN => visible), else 0.
// Returns number of visible boxes. Scalar code (no hand-written SIMD).
int32_t frustum_cull(const float* planes, const float* boxes, int32_t n, uint8_t* out);

// pts: n * (x, y, z); out[i] = (dx*dx + dy*dy) + dz*dz relative to (cx, cy, cz).
void distance_sq(const float* pts, int32_t n, float cx, float cy, float cz, float* out);

}  // namespace vc
