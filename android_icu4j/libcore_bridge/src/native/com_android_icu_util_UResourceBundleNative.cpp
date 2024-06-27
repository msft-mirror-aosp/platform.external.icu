/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "UResourceBundleNative"

#include <nativehelper/JNIHelp.h>
#include <nativehelper/jni_macros.h>

#include <log/log.h>
#include "unicode/ures.h"
#include "unicode/utypes.h"

static inline void openDirectAndCloseRes(const char* res_name) {
    UErrorCode status = U_ZERO_ERROR;
    UResourceBundle *res = ures_openDirect(nullptr, res_name, &status);
    if (U_FAILURE(status)) {
        ALOGE("Failed to load ICU resource '%s': %s", res_name, u_errorName(status));
        return;
    }

    ures_close(res);
}

static void UResourceBundleNative_cacheTimeZoneBundles(JNIEnv* env, jclass) {
    openDirectAndCloseRes("zoneinfo64");
    openDirectAndCloseRes("timezoneTypes");
    openDirectAndCloseRes("metaZones");
    openDirectAndCloseRes("windowsZones");
}

static JNINativeMethod gMethods[] = {
    NATIVE_METHOD(UResourceBundleNative, cacheTimeZoneBundles, "()V"),
};

void register_com_android_icu_util_UResourceBundleNative(JNIEnv* env) {
    jniRegisterNativeMethods(env, "com/android/icu/util/UResourceBundleNative", gMethods, NELEM(gMethods));
}
