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

package org.idp.server.authentication.interactors.attribute_verification;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Issue #1907: the comparison rules of attribute verification, without the interactor. */
class AttributeVerificationRulesTest {

  /**
   * Issue #1935: how each named normalization treats what people actually type, Japanese and
   * English. Each row is registered value, submitted value, whether they should match. Characters
   * that look alike are written as {@code \\uXXXX} escapes, which the compiler turns into the
   * character, so the table shows which one a row is about.
   */
  @Nested
  @DisplayName("normalization: what matches and what does not")
  class Normalization {

    private static boolean matches(
        String normalize, int suffix, String registered, String submitted) {
      AttributeVerificationField field =
          new AttributeVerificationField("in", "x", AttributeNormalization.of(normalize), suffix);
      return field.matches(submitted, registered);
    }

    @ParameterizedTest(name = "[{0}] {1} vs {2} -> {3}")
    @CsvSource(
        delimiter = '|',
        textBlock =
            """
            # name: spaces, dashes, case and width fold; kanji variants do not
            name | 山田 太郎      | 山田太郎        | true
            name | 山田 太郎      | 山田　太郎      | true
            name | Yamada Taro    | yamada taro     | true
            name | YAMADA         | ＹＡＭＡＤＡ    | true
            name | Smith-Jones    | Smith\u2010Jones | true
            name | Smith-Jones    | ＳＭＩＴＨ－ＪＯＮＥＳ | true
            name | Smith-Jones    | smith - jones   | true
            name | Smith-Jones    | Smith\u2212Jones | true
            name | 高橋           | 髙橋            | false
            name | 渡辺           | 渡邊            | false
            name | Yamada         | Yamamoto        | false
            # kana: as name, and hiragana is katakana; ー is kept
            kana | ヤマダ タロウ  | やまだ たろう   | true
            kana | ヤマダ タロウ  | ﾔﾏﾀﾞ ﾀﾛｳ        | true
            kana | ヤマダ タロウ  | ヤマダ　　タロウ | true
            kana | ジョーンズ     | じょーんず      | true
            kana | ジョーンズ     | ジョンズ        | false
            kana | ヤマダ         | ヤマタ          | false
            # email: width, surrounding spaces and case fold
            email | Taro@Example.com | taro@example.com | true
            email | taro@example.com | ｔａｒｏ＠ｅｘａｍｐｌｅ．ｃｏｍ | true
            email | taro@example.com | ' taro@example.com ' | true
            email | taro@example.com | taro@example.co | false
            # digits: everything but digits goes
            digits | 123-4567      | 1234567         | true
            digits | 123-4567      | １２３－４５６７ | true
            digits | 123-4567      | 〒123ー4567      | true
            digits | 090-1234-5678 | 090\u20101234\u20105678 | true
            digits | 090-1234-5678 | ０９０（１２３４）５６７８ | true
            digits | +819012345678 | 09012345678     | false
            # date: the common notations, not the Japanese era
            date | 1990-04-01 | 1990/4/1           | true
            date | 1990-04-01 | 19900401           | true
            date | 1990-04-01 | １９９０／０４／０１ | true
            date | 1990-04-01 | 1990年4月1日       | true
            date | 1990-04-01 | 1990.04.01         | true
            date | 1990-04-01 | ' 1990-04-01 '     | true
            date | 1990-04-01 | H2.4.1             | false
            date | 1990-04-01 | 1990-04-02         | false
            # nfkc: width folds; spaces inside, kana scripts and case do not
            nfkc | 山田 太郎   | 山田　太郎 | true
            nfkc | YAMADA      | ＹＡＭＡＤＡ | true
            nfkc | 山田 太郎   | 山田太郎   | false
            nfkc | ヤマダ      | やまだ     | false
            nfkc | Yamada      | yamada     | false
            # exact: nothing folds
            exact | 山田太郎   | ' 山田太郎 ' | false
            exact | 山田太郎   | 山田太郎   | true
            """)
    void table(String normalize, String registered, String submitted, boolean expected) {
      assertEquals(expected, matches(normalize, 0, registered, submitted));
    }

    @ParameterizedTest(name = "{0} vs last 4 -> {1}")
    @CsvSource(
        delimiter = '|',
        textBlock =
            """
            +819012345678      | true
            090-1234-5678      | true
            ０９０‐１２３４‐５６７８ | true
            090-1234-5679      | false
            """)
    void phoneLastFour(String registered, boolean expected) {
      assertEquals(expected, matches("digits", 4, registered, "5678"));
    }

    @Test
    @DisplayName("an empty result is no value: two values reduced to nothing do not match")
    void emptyResultIsNoValue() {
      assertFalse(matches("digits", 0, "abc", "xyz"));
      assertFalse(matches("date", 0, "not a date", "also not"));
      assertFalse(matches("exact", 0, "", ""));
      assertFalse(matches("name", 0, "   ", "\u3000"));
    }

    @Test
    @DisplayName("an unknown name is not a normalization")
    void unknownName() {
      assertNull(AttributeNormalization.of("lowercase"));
    }
  }

  @Nested
  @DisplayName("field")
  class Field {

    @Test
    @DisplayName("suffix_length compares the last N characters after normalization")
    void suffix() {
      AttributeVerificationField field =
          new AttributeVerificationField(
              "phone_last4", "phone_number", AttributeNormalization.DIGITS, 4);

      assertTrue(field.matches("5678", "090-1234-5678"));
      assertFalse(field.matches("5679", "090-1234-5678"));
      assertFalse(field.matches("678", "090-1234-5678"));
    }

    @Test
    @DisplayName("a missing value on either side never matches")
    void missing() {
      AttributeVerificationField field =
          new AttributeVerificationField("birthdate", "birthdate", AttributeNormalization.DATE, 0);

      assertFalse(field.matches("2000-01-05", null));
      assertFalse(field.matches(null, "2000-01-05"));
      assertFalse(field.matches(null, null));
    }
  }

  @Nested
  @DisplayName("config")
  class Config {

    @Test
    @DisplayName("reads fields and falls back to the default attempt limits")
    void defaults() {
      AttributeVerificationConfig config =
          AttributeVerificationConfig.from(
              Map.of(
                  "fields",
                  List.of(
                      Map.of(
                          "input",
                          "birthdate",
                          "user_attribute",
                          "birthdate",
                          "normalize",
                          "date"))));

      assertTrue(config.isValid());
      assertEquals(1, config.fields().size());
      assertEquals(AttributeVerificationConfig.DEFAULT_MAX_ATTEMPTS, config.maxAttempts());
      assertEquals(AttributeVerificationConfig.DEFAULT_LOCKOUT_SECONDS, config.lockoutSeconds());
    }

    @Test
    @DisplayName("conditions alone are a valid config, with the default error unless one is given")
    void conditionsOnly() {
      Map<String, Object> conditions =
          Map.of(
              "any_of",
              List.of(
                  List.of(
                      Map.of(
                          "path",
                          "$.user.status",
                          "type",
                          "string",
                          "operation",
                          "eq",
                          "value",
                          "IDENTITY_VERIFIED"))));

      AttributeVerificationConfig withDefault =
          AttributeVerificationConfig.from(Map.of("conditions", conditions));
      assertTrue(withDefault.isValid());
      assertTrue(withDefault.hasConditions());
      assertFalse(withDefault.hasFields());
      assertEquals(
          AttributeVerificationConfig.DEFAULT_CONDITION_ERROR, withDefault.conditionError());

      assertEquals(
          "identity_verification_required",
          AttributeVerificationConfig.from(
                  Map.of("conditions", conditions, "error", "identity_verification_required"))
              .conditionError());
    }

    @Test
    @DisplayName("a field can name its own functions instead of a normalization")
    void customFunctions() {
      AttributeVerificationConfig config =
          AttributeVerificationConfig.from(
              Map.of(
                  "fields",
                  List.of(
                      Map.of(
                          "input", "member_no",
                          "user_attribute", "custom_properties.member_no",
                          "functions",
                              List.of(
                                  Map.of("name", "normalize"),
                                  Map.of(
                                      "name",
                                      "regex_replace",
                                      "args",
                                      Map.of("pattern", "^M-", "replacement", "")))))));

      assertTrue(config.isValid(), config.invalidReason());
      AttributeVerificationField field = config.fields().get(0);
      assertTrue(field.matches("Ｍ-001", "M-001"));
      assertTrue(field.matches("001", "M-001"));
      assertFalse(field.matches("002", "M-001"));
    }

    @Test
    @DisplayName(
        "functions that do not suit a comparison, or carry wrong arguments, make the config invalid")
    void invalidFunctions() {
      List<Object> unsuitable =
          List.of(
              List.of(Map.of("name", "exists")),
              List.of(Map.of("name", "random_string", "args", Map.of("length", 8))),
              List.of(Map.of("name", "kana", "args", Map.of("to", "katakan"))),
              List.of(Map.of("name", "no_such_function")),
              List.of(),
              "normalize");
      for (Object functions : unsuitable) {
        assertFalse(
            AttributeVerificationConfig.from(
                    Map.of(
                        "fields",
                        List.of(
                            Map.of(
                                "input",
                                "x",
                                "user_attribute",
                                "birthdate",
                                "functions",
                                functions))))
                .isValid(),
            String.valueOf(functions));
      }

      assertFalse(
          AttributeVerificationConfig.from(
                  Map.of(
                      "fields",
                      List.of(
                          Map.of(
                              "input", "x",
                              "user_attribute", "birthdate",
                              "normalize", "date",
                              "functions", List.of(Map.of("name", "date"))))))
              .isValid());
    }

    @Test
    @DisplayName("one interaction is one kind of check: conditions and fields together are invalid")
    void oneKindPerInteraction() {
      Map<String, Object> conditions =
          Map.of(
              "any_of",
              List.of(List.of(Map.of("path", "$.user.status", "operation", "eq", "value", "x"))));
      List<Object> fields = List.of(Map.of("input", "birthdate", "user_attribute", "birthdate"));

      assertFalse(
          AttributeVerificationConfig.from(Map.of("conditions", conditions, "fields", fields))
              .isValid());
      assertFalse(
          AttributeVerificationConfig.from(Map.of("fields", fields, "error", "some_error"))
              .isValid());
    }

    @Test
    @DisplayName("unreadable conditions or error codes make the config invalid")
    void invalidConditions() {
      Map<String, Object> unknownOperation =
          Map.of(
              "any_of",
              List.of(
                  List.of(Map.of("path", "$.user.status", "operation", "equals", "value", "x"))));
      Map<String, Object> emptyGroup = Map.of("any_of", List.of(List.of()));
      Map<String, Object> valid =
          Map.of(
              "any_of",
              List.of(List.of(Map.of("path", "$.user.status", "operation", "eq", "value", "x"))));

      assertFalse(
          AttributeVerificationConfig.from(Map.of("conditions", unknownOperation)).isValid());
      assertFalse(AttributeVerificationConfig.from(Map.of("conditions", emptyGroup)).isValid());
      assertFalse(AttributeVerificationConfig.from(Map.of("conditions", List.of())).isValid());
      assertFalse(
          AttributeVerificationConfig.from(Map.of("conditions", valid, "error", "Not A Code"))
              .isValid());
    }

    @Test
    @DisplayName("value_path compares two paths with eq / ne, and is invalid otherwise (#1907)")
    void valuePath() {
      Map<String, Object> eq =
          conditionWith(
              Map.of(
                  "path", "$.request.custom_params.member_no",
                  "operation", "eq",
                  "value_path", "$.user.custom_properties.member_no"));
      Map<String, Object> otherOperation =
          conditionWith(Map.of("path", "$.a", "operation", "gte", "value_path", "$.b"));
      Map<String, Object> withValue =
          conditionWith(
              Map.of("path", "$.a", "operation", "eq", "value", "x", "value_path", "$.b"));

      assertTrue(AttributeVerificationConfig.from(Map.of("conditions", eq)).isValid());
      assertFalse(AttributeVerificationConfig.from(Map.of("conditions", otherOperation)).isValid());
      assertFalse(AttributeVerificationConfig.from(Map.of("conditions", withValue)).isValid());
    }

    private static Map<String, Object> conditionWith(Map<String, Object> condition) {
      return Map.of("any_of", List.of(List.of(condition)));
    }

    @Test
    @DisplayName("one unreadable field makes the whole config invalid, rather than being dropped")
    void invalid() {
      assertFalse(AttributeVerificationConfig.from(null).isValid());
      assertFalse(AttributeVerificationConfig.from(Map.of("fields", List.of())).isValid());
      assertFalse(
          AttributeVerificationConfig.from(
                  Map.of(
                      "fields",
                      List.of(
                          Map.of("input", "birthdate", "user_attribute", "birthdate"),
                          Map.of("input", "secret", "user_attribute", "hashed_password"))))
              .isValid());
      assertFalse(
          AttributeVerificationConfig.from(
                  Map.of(
                      "fields",
                      List.of(
                          Map.of(
                              "input",
                              "birthdate",
                              "user_attribute",
                              "birthdate",
                              "normalize",
                              "lowercase"))))
              .isValid());
      assertFalse(
          AttributeVerificationConfig.from(
                  Map.of(
                      "fields",
                      List.of(Map.of("input", "birthdate", "user_attribute", "birthdate")),
                      "max_attempts",
                      0))
              .isValid());
    }
  }

  @Nested
  @DisplayName("verifiable attributes")
  class Attributes {

    @Test
    @DisplayName("resolves listed attributes and scalar custom properties")
    void resolves() {
      HashMap<String, Object> customProperties = new HashMap<>();
      customProperties.put("member_no", "M-001");
      customProperties.put("rank", 3);
      customProperties.put("nested", Map.of("a", "b"));
      User user =
          new User()
              .setSub("user-1")
              .setStatus(UserStatus.REGISTERED)
              .setBirthdate("2000-01-05")
              .setCustomProperties(customProperties);

      VerifiableUserAttributes attributes = new VerifiableUserAttributes(user);

      assertEquals("2000-01-05", attributes.valueOf("birthdate"));
      assertEquals("M-001", attributes.valueOf("custom_properties.member_no"));
      assertEquals("3", attributes.valueOf("custom_properties.rank"));
      assertNull(attributes.valueOf("custom_properties.nested"));
      assertNull(attributes.valueOf("phone_number"));
    }

    @Test
    @DisplayName("credentials and verified claims are not verifiable")
    void excluded() {
      assertFalse(VerifiableUserAttributes.isVerifiable("hashed_password"));
      assertFalse(VerifiableUserAttributes.isVerifiable("verified_claims"));
      assertFalse(VerifiableUserAttributes.isVerifiable("verified_claims.claims.birthdate"));
      assertFalse(VerifiableUserAttributes.isVerifiable("custom_properties."));
      assertFalse(VerifiableUserAttributes.isVerifiable(null));
    }
  }
}
