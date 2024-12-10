/*
 * Copyright (C) 2020 The Android Open Source Project
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

#define ATRACE_TAG ATRACE_TAG_DALVIK

#include <cutils/trace.h>
#include "jni.h"
#include <nativehelper/JNIHelp.h>
#include <nativehelper/jni_macros.h>
#include <nativehelper/scoped_utf_chars.h>

static void ATrace_nativeTraceBegin(JNIEnv* env, jclass, jstring event) {
  ScopedUtfChars event_name(env, event);

  ATRACE_BEGIN(event_name.c_str());
}

static void ATrace_nativeTraceEnd(JNIEnv* env, jclass) {
  ATRACE_END();
}

static JNINativeMethod gMethods[] = {
  NATIVE_METHOD(ATrace, nativeTraceBegin, "(Ljava/lang/String;)V"),
  NATIVE_METHOD(ATrace, nativeTraceEnd, "()V"),
};

void register_com_android_i18n_util_ATrace(JNIEnv* env) {
  jniRegisterNativeMethods(env, "com/android/i18n/util/ATrace", gMethods, NELEM(gMethods));
}
