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

#include <gtest/gtest.h>

#include <unicode/utypes.h>

TEST(Icu4cUTypesTest, test_u_errorName) {
  EXPECT_STREQ("U_ZERO_ERROR", u_errorName(U_ZERO_ERROR));
  EXPECT_STREQ("U_ILLEGAL_ARGUMENT_ERROR", u_errorName(U_ILLEGAL_ARGUMENT_ERROR));
  EXPECT_STREQ("U_USING_FALLBACK_WARNING", u_errorName(U_USING_FALLBACK_WARNING));
  EXPECT_STREQ("U_BAD_VARIABLE_DEFINITION", u_errorName(U_BAD_VARIABLE_DEFINITION));
  EXPECT_STREQ("U_UNEXPECTED_TOKEN", u_errorName(U_UNEXPECTED_TOKEN));
  EXPECT_STREQ("U_BRK_INTERNAL_ERROR", u_errorName(U_BRK_INTERNAL_ERROR));
  EXPECT_STREQ("U_REGEX_INTERNAL_ERROR", u_errorName(U_REGEX_INTERNAL_ERROR));
  EXPECT_STREQ("U_STRINGPREP_PROHIBITED_ERROR", u_errorName(U_STRINGPREP_PROHIBITED_ERROR));
  EXPECT_STREQ("U_REGEX_INTERNAL_ERROR", u_errorName(U_REGEX_INTERNAL_ERROR));
}
