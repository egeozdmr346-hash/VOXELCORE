#include "com_voxelcore_native_bridge_NativeBridge.h"
#include "voxelcore_version.h"

#include <cstdint>

// Stage 1's only real native operation: count non-zero entries in a short[] handed over
// from Java. Stands in for scanning a chunk section's 16x16x16 palette-index array for
// non-air blocks — a real bulk, cache-friendly scan, the shape of thing actually worth
// paying the JNI transition cost for (one call per chunk section, not one per block).
//
// GetShortArrayElements with isCopy may or may not copy depending on the JVM; either way
// ReleaseShortArrayElements with JNI_ABORT is correct here since we never write back into
// the array.
JNIEXPORT jlong JNICALL Java_com_voxelcore_native_1bridge_NativeBridge_nativeCountNonZeroShorts
  (JNIEnv* env, jclass /* clazz */, jshortArray data) {
    if (data == nullptr) {
        return 0;
    }

    jsize length = env->GetArrayLength(data);
    jboolean isCopy = JNI_FALSE;
    jshort* elements = env->GetShortArrayElements(data, &isCopy);
    if (elements == nullptr) {
        // Allocation failure inside the JVM — an exception is already pending.
        return -1;
    }

    int64_t count = 0;
    for (jsize i = 0; i < length; ++i) {
        if (elements[i] != 0) {
            ++count;
        }
    }

    env->ReleaseShortArrayElements(data, elements, JNI_ABORT);
    return static_cast<jlong>(count);
}

JNIEXPORT jstring JNICALL Java_com_voxelcore_native_1bridge_NativeBridge_nativeGetVersion
  (JNIEnv* env, jclass /* clazz */) {
    return env->NewStringUTF(VOXELCORE_NATIVE_VERSION);
}
