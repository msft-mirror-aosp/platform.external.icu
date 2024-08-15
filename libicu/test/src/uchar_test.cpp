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

#include <gtest/gtest.h>

#include <unicode/uchar.h>

TEST(Icu4cUCharTest, test_u_hasBinaryProperty) {
  ASSERT_TRUE(u_hasBinaryProperty(U' '  /* ascii space */, UCHAR_WHITE_SPACE));
  ASSERT_TRUE(u_hasBinaryProperty(8200 /* Punctuation space U+2008 */, UCHAR_WHITE_SPACE));
  ASSERT_TRUE(u_hasBinaryProperty(U'❤' /* Emoji heart U+2764 */, UCHAR_EMOJI));
  ASSERT_FALSE(u_hasBinaryProperty(U'❤' /* Emoji heart U+2764 */, UCHAR_WHITE_SPACE));
}

TEST(Icu4cUCharTest, test_u_toupper) {
  ASSERT_EQ(U'A', u_toupper(U'a'));
  ASSERT_EQ(U'A', u_toupper(U'A'));
  ASSERT_EQ(U'1', u_toupper(U'1'));
  ASSERT_EQ(U'Ë', u_toupper(U'ë'));
}

TEST(Icu4cUCharTest, test_u_charFromName) {
  UErrorCode err;
  ASSERT_EQ(0x0020, u_charFromName(U_UNICODE_CHAR_NAME, "SPACE", &err));
  ASSERT_EQ(0x0061, u_charFromName(U_UNICODE_CHAR_NAME, "LATIN SMALL LETTER A", &err));
  ASSERT_EQ(0x0042, u_charFromName(U_UNICODE_CHAR_NAME, "LATIN CAPITAL LETTER B", &err));
  ASSERT_EQ(0x00a2, u_charFromName(U_UNICODE_CHAR_NAME, "CENT SIGN", &err));
  ASSERT_EQ(0xffe5, u_charFromName(U_UNICODE_CHAR_NAME, "FULLWIDTH YEN SIGN", &err));
  ASSERT_EQ(0x3401, u_charFromName(U_UNICODE_CHAR_NAME, "CJK UNIFIED IDEOGRAPH-3401", &err));
}
