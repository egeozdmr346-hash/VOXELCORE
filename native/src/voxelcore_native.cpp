#include "com_voxelcore_native_bridge_NativeBridge.h"
#include "voxelcore_version.h"

#include <cstdint>

#include "engine/chunk.hpp"
#include "engine/cpu.hpp"
#include "engine/cull.hpp"

// JNI glue only. Rules: never call other JNI functions while a critical region is held,
// never let a C++ exception cross the boundary, return -1 on any invalid input
// (Java then falls back to its own implementation).

namespace {

// RAII holder for GetPrimitiveArrayCritical (zero-copy). mode: JNI_ABORT = read-only, 0 = write back.
template <class ArrayT, class ElemT>
struct Critical {
    JNIEnv* env;
    ArrayT arr;
    ElemT* ptr;
    jint mode;
    Critical(JNIEnv* e, ArrayT a, jint m) : env(e), arr(a), ptr(nullptr), mode(m) {
        if (a != nullptr) ptr = static_cast<ElemT*>(env->GetPrimitiveArrayCritical(a, nullptr));
    }
    ~Critical() {
        if (ptr != nullptr) env->ReleasePrimitiveArrayCritical(arr, ptr, mode);
    }
    Critical(const Critical&) = delete;
    Critical& operator=(const Critical&) = delete;
};

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_com_voxelcore_native_1bridge_NativeBridge_nativeCountNonZeroShorts
  (JNIEnv* env, jclass, jshortArray data) {
    if (data == nullptr) return -1;
    try {
        jsize len = env->GetArrayLength(data);
        Critical<jshortArray, jshort> d(env, data, JNI_ABORT);
        if (d.ptr == nullptr) return -1;
        return static_cast<jlong>(vc::count_nonzero(reinterpret_cast<const int16_t*>(d.ptr), static_cast<size_t>(len)));
    } catch (...) {
        return -1;
    }
}

JNIEXPORT jint JNICALL Java_com_voxelcore_native_1bridge_NativeBridge_nativeSectionStats
  (JNIEnv* env, jclass, jshortArray data, jintArray out) {
    if (data == nullptr || out == nullptr) return -1;
    try {
        jsize len = env->GetArrayLength(data);
        if (env->GetArrayLength(out) < 2) return -1;
        Critical<jshortArray, jshort> d(env, data, JNI_ABORT);
        Critical<jintArray, jint> o(env, out, 0);
        if (d.ptr == nullptr || o.ptr == nullptr) return -1;
        vc::SectionStats s = vc::section_stats(reinterpret_cast<const int16_t*>(d.ptr), static_cast<size_t>(len));
        o.ptr[0] = static_cast<jint>(s.nonzero);
        o.ptr[1] = static_cast<jint>(s.distinct);
        return 0;
    } catch (...) {
        return -1;
    }
}

JNIEXPORT jint JNICALL Java_com_voxelcore_native_1bridge_NativeBridge_nativeFrustumCull
  (JNIEnv* env, jclass, jfloatArray planes, jfloatArray boxes, jint count, jbyteArray out) {
    if (planes == nullptr || boxes == nullptr || out == nullptr || count < 0) return -1;
    try {
        if (env->GetArrayLength(planes) < 24) return -1;
        if (static_cast<int64_t>(env->GetArrayLength(boxes)) < static_cast<int64_t>(count) * 6) return -1;
        if (env->GetArrayLength(out) < count) return -1;
        Critical<jfloatArray, jfloat> p(env, planes, JNI_ABORT);
        Critical<jfloatArray, jfloat> b(env, boxes, JNI_ABORT);
        Critical<jbyteArray, jbyte> o(env, out, 0);
        if (p.ptr == nullptr || b.ptr == nullptr || o.ptr == nullptr) return -1;
        return static_cast<jint>(vc::frustum_cull(p.ptr, b.ptr, count, reinterpret_cast<uint8_t*>(o.ptr)));
    } catch (...) {
        return -1;
    }
}

JNIEXPORT jint JNICALL Java_com_voxelcore_native_1bridge_NativeBridge_nativeDistanceSq
  (JNIEnv* env, jclass, jfloatArray pts, jint count, jfloat cx, jfloat cy, jfloat cz, jfloatArray out) {
    if (pts == nullptr || out == nullptr || count < 0) return -1;
    try {
        if (static_cast<int64_t>(env->GetArrayLength(pts)) < static_cast<int64_t>(count) * 3) return -1;
        if (env->GetArrayLength(out) < count) return -1;
        Critical<jfloatArray, jfloat> p(env, pts, JNI_ABORT);
        Critical<jfloatArray, jfloat> o(env, out, 0);
        if (p.ptr == nullptr || o.ptr == nullptr) return -1;
        vc::distance_sq(p.ptr, count, cx, cy, cz, o.ptr);
        return 0;
    } catch (...) {
        return -1;
    }
}

JNIEXPORT jint JNICALL Java_com_voxelcore_native_1bridge_NativeBridge_nativeCpuFeatures
  (JNIEnv*, jclass) {
    return static_cast<jint>(vc::cpu_features());
}

JNIEXPORT jint JNICALL Java_com_voxelcore_native_1bridge_NativeBridge_nativeActiveSimd
  (JNIEnv*, jclass) {
    return static_cast<jint>(vc::active_simd_level());
}

JNIEXPORT jint JNICALL Java_com_voxelcore_native_1bridge_NativeBridge_nativeSetSimdMode
  (JNIEnv*, jclass, jint mode) {
    return static_cast<jint>(vc::set_simd_mode(static_cast<int>(mode)));
}

JNIEXPORT jstring JNICALL Java_com_voxelcore_native_1bridge_NativeBridge_nativeGetVersion
  (JNIEnv* env, jclass) {
    return env->NewStringUTF(VOXELCORE_NATIVE_VERSION);
}

}  // extern "C"
