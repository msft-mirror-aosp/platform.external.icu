# Copyright (C) 2015 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Common variables and actions for inclusion in srcgen scripts.

set -e

if [[ -z ${ANDROID_BUILD_TOP} ]]; then
  echo ANDROID_BUILD_TOP not set
  exit 1
fi

# source envsetup.sh because functions we use like mm are not exported.
source ${ANDROID_BUILD_TOP}/build/envsetup.sh

# Build Options used by Android.bp
while true; do
  case "$1" in
    --do-not-make ) DO_NOT_MAKE=1; shift ;;
    -- ) shift; break ;;
    * ) break ;;
  esac
done

# Build the srcgen tools.
cd ${ANDROID_BUILD_TOP}
if [ -z "$DO_NOT_MAKE" ]; then
    make -j16 android_icu4j_srcgen_binary
fi

ICU_SRCGEN_DIR=${ANDROID_BUILD_TOP}/external/icu/tools/srcgen
ICU4J_DIR=${ANDROID_BUILD_TOP}/external/icu/icu4j
ANDROID_ICU4J_DIR=${ANDROID_BUILD_TOP}/external/icu/android_icu4j

CLASSPATH=${ANDROID_HOST_OUT}/framework/currysrc.jar:${ANDROID_HOST_OUT}/framework/android_icu4j_srcgen.jar

# The parts of ICU4J to include during srcgen.
#
# The following are deliberately excluded:
#   localespi - is not supported on Android
#   charset - because icu4c is used instead
INPUT_DIRS="\
    ${ICU4J_DIR}/main/collate/src/main \
    ${ICU4J_DIR}/main/core/src/main \
    ${ICU4J_DIR}/main/currdata/src/main \
    ${ICU4J_DIR}/main/langdata/src/main \
    ${ICU4J_DIR}/main/regiondata/src/main \
    ${ICU4J_DIR}/main/translit/src/main \
    "

INPUT_JAVA_DIRS=""
INPUT_RESOURCE_DIRS=""
for INPUT_DIR in ${INPUT_DIRS}; do
  if [ -d "${INPUT_DIR}/java" ]; then
    INPUT_JAVA_DIRS="${INPUT_JAVA_DIRS} ${INPUT_DIR}/java"
  fi
  if [ -d "${INPUT_DIR}/resources" ]; then
    INPUT_RESOURCE_DIRS="${INPUT_RESOURCE_DIRS} ${INPUT_DIR}/resources"
  fi
done

SAMPLE_INPUT_DIR=${ICU4J_DIR}/samples/src/main/java/com/ibm/icu/samples
# Only generate sample files for code we know should compile on Android with the public APIs.
SAMPLE_INPUT_FILES="\
    ${SAMPLE_INPUT_DIR}/text/dateintervalformat/DateIntervalFormatSample.java \
    ${SAMPLE_INPUT_DIR}/text/datetimepatterngenerator/DateTimePatternGeneratorSample.java \
    ${SAMPLE_INPUT_DIR}/text/pluralformat/PluralFormatSample.java \
    "

# See above for an explanation as to why the tests for charset and localespi are not included here.
TEST_INPUT_DIR=${ICU4J_DIR}/main/tests
TEST_INPUT_DIRS="\
    ${ICU4J_DIR}/main/collate/src/test \
    ${ICU4J_DIR}/main/framework/src/test \
    ${ICU4J_DIR}/main/core/src/test \
    ${ICU4J_DIR}/main/common_tests/src/test \
    ${ICU4J_DIR}/main/translit/src/test \
    "

TEST_INPUT_JAVA_DIRS=""
TEST_INPUT_RESOURCE_DIRS=""
for TEST_INPUT_DIR in ${TEST_INPUT_DIRS}; do
  if [ -d "${TEST_INPUT_DIR}/java" ]; then
    TEST_INPUT_JAVA_DIRS="${TEST_INPUT_JAVA_DIRS} ${TEST_INPUT_DIR}/java"
  fi
  if [ -d "${TEST_INPUT_DIR}/resources" ]; then
    TEST_INPUT_RESOURCE_DIRS="${TEST_INPUT_RESOURCE_DIRS} ${TEST_INPUT_DIR}/resources"
  fi
done

# Allow override of the java runtime to avoid http://b/27775477
SRCGEN_JAVA_BINARY=${SRCGEN_JAVA_BINARY:-java}
