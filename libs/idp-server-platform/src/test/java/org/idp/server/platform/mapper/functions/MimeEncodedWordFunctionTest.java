/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.platform.mapper.functions;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MimeEncodedWordFunctionTest {

  private final MimeEncodedWordFunction function = new MimeEncodedWordFunction();

  @Test
  public void testName() {
    assertEquals("mimeEncodedWord", function.name());
  }

  @Test
  public void testApplyWithNullInput() {
    assertNull(function.apply(null, null));
  }

  @Test
  public void testApplyWithAsciiOnly() {
    // ASCII only text should not be encoded
    String result = (String) function.apply("Example Service", null);
    assertEquals("Example Service", result);
  }

  @Test
  public void testApplyWithAsciiOnlyWithArgs() {
    Map<String, Object> args = new HashMap<>();
    args.put("charset", "UTF-8");
    args.put("encoding", "B");

    String result = (String) function.apply("Example Service", args);
    assertEquals("Example Service", result);
  }

  @Test
  public void testApplyBase64EncodingDefault() {
    // Japanese text should be Base64 encoded by default
    String result = (String) function.apply("日本語サービス", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
    assertEquals("=?UTF-8?B?5pel5pys6Kqe44K144O844OT44K5?=", result);
  }

  @Test
  public void testApplyBase64EncodingExplicit() {
    Map<String, Object> args = new HashMap<>();
    args.put("encoding", "B");

    String result = (String) function.apply("日本語サービス", args);
    assertEquals("=?UTF-8?B?5pel5pys6Kqe44K144O844OT44K5?=", result);
  }

  @Test
  public void testApplyBase64EncodingCaseInsensitive() {
    Map<String, Object> args = new HashMap<>();
    args.put("encoding", "b");

    String result = (String) function.apply("日本語サービス", args);
    assertTrue(result.startsWith("=?UTF-8?B?"));
  }

  @Test
  public void testApplyQuotedPrintableEncoding() {
    Map<String, Object> args = new HashMap<>();
    args.put("encoding", "Q");

    String result = (String) function.apply("日本語", args);
    assertTrue(result.startsWith("=?UTF-8?Q?"));
    assertTrue(result.endsWith("?="));
    // Q-encoding uses =XX format for non-ASCII bytes
    assertTrue(result.contains("="));
  }

  @Test
  public void testApplyQuotedPrintableCaseInsensitive() {
    Map<String, Object> args = new HashMap<>();
    args.put("encoding", "q");

    String result = (String) function.apply("Café", args);
    assertTrue(result.startsWith("=?UTF-8?Q?"));
  }

  @Test
  public void testApplyQuotedPrintableWithSpace() {
    Map<String, Object> args = new HashMap<>();
    args.put("encoding", "Q");

    String result = (String) function.apply("Hello 世界", args);
    // Space should be encoded as underscore in Q-encoding
    assertTrue(result.contains("_"));
  }

  @Test
  public void testApplyWithCustomCharset() {
    Map<String, Object> args = new HashMap<>();
    args.put("charset", "ISO-2022-JP");
    args.put("encoding", "B");

    String result = (String) function.apply("日本語", args);
    assertTrue(result.startsWith("=?ISO-2022-JP?B?"));
  }

  @Test
  public void testApplyWithUnsupportedEncoding() {
    Map<String, Object> args = new HashMap<>();
    args.put("encoding", "X");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> function.apply("日本語", args));

    assertTrue(exception.getMessage().contains("Unsupported encoding"));
    assertTrue(exception.getMessage().contains("use B or Q"));
  }

  @Test
  public void testApplyWithEmptyString() {
    String result = (String) function.apply("", null);
    assertEquals("", result);
  }

  @Test
  public void testApplyWithNumericInput() {
    // Numeric input converted to string
    String result = (String) function.apply(12345, null);
    assertEquals("12345", result);
  }

  @Test
  public void testApplyWithMixedAsciiAndNonAscii() {
    Map<String, Object> args = new HashMap<>();
    args.put("encoding", "B");

    String result = (String) function.apply("Example 日本語", args);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithEmojiCharacters() {
    Map<String, Object> args = new HashMap<>();
    args.put("encoding", "B");

    String result = (String) function.apply("Hello 👋", args);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithSpecialCharacters() {
    Map<String, Object> args = new HashMap<>();
    args.put("encoding", "Q");

    String result = (String) function.apply("Tëst=?_", args);
    // Special characters should be encoded in Q-encoding
    assertTrue(result.contains("=3D")); // '=' encoded
    assertTrue(result.contains("=3F")); // '?' encoded
    assertTrue(result.contains("=5F")); // '_' encoded
  }

  @Test
  public void testApplyDefaultCharsetIsUtf8() {
    Map<String, Object> args = new HashMap<>();
    args.put("encoding", "B");

    String result = (String) function.apply("テスト", args);
    assertTrue(result.startsWith("=?UTF-8?B?"));
  }

  @Test
  public void testApplyDefaultEncodingIsBase64() {
    String result = (String) function.apply("テスト", null);
    assertTrue(result.contains("?B?"));
  }

  @Test
  public void testApplyWithNullArgs() {
    // Should use default values (UTF-8, B)
    String result = (String) function.apply("日本語", null);
    assertEquals("=?UTF-8?B?5pel5pys6Kqe?=", result);
  }

  @Test
  public void testApplyWithEmptyArgs() {
    Map<String, Object> args = new HashMap<>();

    // Should use default values (UTF-8, B)
    String result = (String) function.apply("日本語", args);
    assertEquals("=?UTF-8?B?5pel5pys6Kqe?=", result);
  }

  @Test
  public void testApplyPreservesAsciiCharsInQuotedPrintable() {
    Map<String, Object> args = new HashMap<>();
    args.put("encoding", "Q");

    String result = (String) function.apply("Café", args);
    // 'C', 'a', 'f' should remain as-is, only 'é' should be encoded
    assertTrue(result.contains("Caf"));
  }

  @Test
  public void testApplySingleNonAsciiCharacter() {
    String result = (String) function.apply("あ", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyAsciiOnlyAtBoundary() {
    // Character 127 is DEL (ASCII)
    String result = (String) function.apply(String.valueOf((char) 127), null);
    assertEquals(String.valueOf((char) 127), result);
  }

  @Test
  public void testApplyNonAsciiAtBoundary() {
    // Character 128 is non-ASCII
    String result = (String) function.apply(String.valueOf((char) 128), null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
  }

  // Real-world email sender name patterns
  @Test
  public void testApplyWithCompanyNameJapanese() {
    String result = (String) function.apply("株式会社サンプルサービス", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithCompanyNameAndDepartment() {
    String result = (String) function.apply("株式会社○○・カスタマーサポート", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithParenthesesJapanese() {
    String result = (String) function.apply("サポートセンター（平日9-18時）", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithParenthesesHalfWidth() {
    String result = (String) function.apply("サポートチーム(重要)", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithBrackets() {
    String result = (String) function.apply("お知らせ【緊急】", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithCornerBrackets() {
    String result = (String) function.apply("サポート「重要なお知らせ」", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithDoubleCornerBrackets() {
    String result = (String) function.apply("お知らせ『緊急メンテナンス』", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithFullWidthColon() {
    String result = (String) function.apply("サポートチーム：カスタマーサービス", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithWaveDash() {
    String result = (String) function.apply("営業時間：9時～18時", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithFullWidthTilde() {
    String result = (String) function.apply("サービス〜お客様窓口", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithReferenceMark() {
    String result = (String) function.apply("※重要※ サポートセンター", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithStar() {
    String result = (String) function.apply("★重要★ カスタマーサービス", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithDiamond() {
    String result = (String) function.apply("◆お知らせ◆ サポート窓口", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithBlackSquare() {
    String result = (String) function.apply("■緊急メンテナンス■ 運営チーム", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithAtSign() {
    String result = (String) function.apply("サポート＠カスタマーセンター", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithMultipleSymbols() {
    String result = (String) function.apply("【重要】お知らせ★サポート窓口（9-18時）", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithMiddleDot() {
    String result = (String) function.apply("株式会社サンプル・サービス部門", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithFullWidthSpace() {
    String result = (String) function.apply("サンプル　サービス", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithMixedJapaneseAndEnglish() {
    String result = (String) function.apply("Sample株式会社 Support Team", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithCompanyLtdJapanese() {
    String result = (String) function.apply("サンプル株式会社 カスタマーセンター", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithQuotedPrintableCompanyName() {
    Map<String, Object> args = new HashMap<>();
    args.put("encoding", "Q");

    String result = (String) function.apply("サポートチーム", args);
    assertTrue(result.startsWith("=?UTF-8?Q?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithLongCompanyName() {
    // Test a realistic long company name (still under typical limits)
    String result = (String) function.apply("株式会社サンプルシステムサービスカスタマーサポートセンター", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
    // Verify it's a valid encoded-word (though may exceed 75 chars - documented limitation)
  }

  @Test
  public void testApplyWithEmailAddressFormat() {
    // Some systems include email-like format in sender name
    String result = (String) function.apply("サポート <noreply@example.com>", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithHyphenAndDash() {
    String result = (String) function.apply("サービス名-カスタマーサポート", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }

  @Test
  public void testApplyWithAmpersand() {
    String result = (String) function.apply("Sample & サービス株式会社", null);
    assertTrue(result.startsWith("=?UTF-8?B?"));
    assertTrue(result.endsWith("?="));
  }
}
