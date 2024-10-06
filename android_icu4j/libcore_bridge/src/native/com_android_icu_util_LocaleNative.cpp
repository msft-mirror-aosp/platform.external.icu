/*
 * Copyright (C) 2008 The Android Open Source Project
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

#include <nativehelper/JNIHelp.h>
#include <nativehelper/jni_macros.h>

#include "IcuUtilities.h"
#include "ScopedIcuLocale.h"

#include "unicode/locid.h"

static void LocaleNative_setDefaultNative(JNIEnv* env, jclass, jstring javaLanguageTag) {
  ScopedIcuLocale icuLocale(env, javaLanguageTag);
  if (!icuLocale.valid()) {
    return;
  }

  UErrorCode status = U_ZERO_ERROR;
  icu::Locale::setDefault(icuLocale.locale(), status);
  maybeThrowIcuException(env, "uloc_setDefault", status);
}

static jstring LocaleNative_getDisplayCountryNative(JNIEnv* env, jclass, jstring javaTargetLanguageTag, jstring javaLanguageTag) {
  ScopedIcuLocale icuLocale(env, javaLanguageTag);
  if (!icuLocale.valid()) {
    return NULL;
  }
  ScopedIcuLocale icuTargetLocale(env, javaTargetLanguageTag);
  if (!icuTargetLocale.valid()) {
    return NULL;
  }

  icu::UnicodeString str;
  icuTargetLocale.locale().getDisplayCountry(icuLocale.locale(), str);
  return jniCreateString(env, str.getBuffer(), str.length());
}

static jstring LocaleNative_getDisplayLanguageNative(JNIEnv* env, jclass, jstring javaTargetLanguageTag, jstring javaLanguageTag) {
  ScopedIcuLocale icuLocale(env, javaLanguageTag);
  if (!icuLocale.valid()) {
    return NULL;
  }
  ScopedIcuLocale icuTargetLocale(env, javaTargetLanguageTag);
  if (!icuTargetLocale.valid()) {
    return NULL;
  }

  icu::UnicodeString str;
  icuTargetLocale.locale().getDisplayLanguage(icuLocale.locale(), str);
  return jniCreateString(env, str.getBuffer(), str.length());
}

static jstring LocaleNative_getDisplayScriptNative(JNIEnv* env, jclass, jstring javaTargetLanguageTag, jstring javaLanguageTag) {
  ScopedIcuLocale icuLocale(env, javaLanguageTag);
  if (!icuLocale.valid()) {
    return NULL;
  }
  ScopedIcuLocale icuTargetLocale(env, javaTargetLanguageTag);
  if (!icuTargetLocale.valid()) {
    return NULL;
  }

  icu::UnicodeString str;
  icuTargetLocale.locale().getDisplayScript(icuLocale.locale(), str);
  return jniCreateString(env, str.getBuffer(), str.length());
}

static jstring LocaleNative_getDisplayVariantNative(JNIEnv* env, jclass, jstring javaTargetLanguageTag, jstring javaLanguageTag) {
  ScopedIcuLocale icuLocale(env, javaLanguageTag);
  if (!icuLocale.valid()) {
    return NULL;
  }
  ScopedIcuLocale icuTargetLocale(env, javaTargetLanguageTag);
  if (!icuTargetLocale.valid()) {
    return NULL;
  }

  icu::UnicodeString str;
  icuTargetLocale.locale().getDisplayVariant(icuLocale.locale(), str);
  return jniCreateString(env, str.getBuffer(), str.length());
}

static void LocaleNative_cacheUnicodeExtensionSubtagsKeyMap(JNIEnv*, jclass) {
    UErrorCode status = U_ZERO_ERROR;
    char localeID[ULOC_FULLNAME_CAPACITY] = {};
    // Cache the key map by calling uloc_forLanguageTag with a subtag.
    // The UI library minikin on Android calls uloc_forLanguageTag with an Unicode extension
    // specifying the line breaking strictness. Parsing the extension requires loading the key map
    // from keyTypeData.txt.
    // "lb" is the key commonly used by minikin. "ca" is a common legacy key mapping to
    // the "calendar" key. It ensures that the key map is loaded and cached in icu4c.
    // "en-Latn-US" is a common locale used in the Android system regardless what default locale
    // is selected in the Settings app.
    uloc_forLanguageTag("en-Latn-US-u-lb-loose-ca-gregory",
                        localeID, ULOC_FULLNAME_CAPACITY, nullptr, &status);
}

static JNINativeMethod gMethods[] = {
    NATIVE_METHOD(LocaleNative, getDisplayCountryNative, "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"),
    NATIVE_METHOD(LocaleNative, getDisplayLanguageNative, "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"),
    NATIVE_METHOD(LocaleNative, getDisplayScriptNative, "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"),
    NATIVE_METHOD(LocaleNative, getDisplayVariantNative, "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"),
    NATIVE_METHOD(LocaleNative, setDefaultNative, "(Ljava/lang/String;)V"),
    FAST_NATIVE_METHOD(LocaleNative, cacheUnicodeExtensionSubtagsKeyMap, "()V"),
};

void register_com_android_icu_util_LocaleNative(JNIEnv* env) {
  jniRegisterNativeMethods(env, "com/android/icu/util/LocaleNative", gMethods, NELEM(gMethods));
}