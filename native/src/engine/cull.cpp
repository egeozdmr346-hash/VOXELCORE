#include "cull.hpp"

namespace vc {

int32_t frustum_cull(const float* planes, const float* boxes, int32_t n, uint8_t* out) {
    int32_t visible = 0;
    for (int32_t i = 0; i < n; ++i) {
        const float* b = boxes + static_cast<int64_t>(i) * 6;
        bool vis = true;
        for (int p = 0; p < 6; ++p) {
            const float* pl = planes + p * 4;
            float x = pl[0] >= 0.0f ? b[3] : b[0];  // positive vertex
            float y = pl[1] >= 0.0f ? b[4] : b[1];
            float z = pl[2] >= 0.0f ? b[5] : b[2];
            float d = pl[0] * x + pl[1] * y + pl[2] * z + pl[3];
            if (d < 0.0f) {
                vis = false;
                break;
            }
        }
        out[i] = vis ? 1 : 0;
        visible += vis ? 1 : 0;
    }
    return visible;
}

void distance_sq(const float* pts, int32_t n, float cx, float cy, float cz, float* out) {
    for (int32_t i = 0; i < n; ++i) {
        const float* q = pts + static_cast<int64_t>(i) * 3;
        float dx = q[0] - cx;
        float dy = q[1] - cy;
        float dz = q[2] - cz;
        out[i] = dx * dx + dy * dy + dz * dz;
    }
}

}  // namespace vc
