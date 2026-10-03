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

/** Issue #1907: the comparison rules of attribute verification, without the interactor. */
class AttributeVerificationRulesTest {

  @Nested
  @DisplayName("normalization")
  class Normalization {

    @Test
    @DisplayName("nfkc folds full-width forms")
    void nfkc() {
      assertEquals("ABC123", AttributeNormalization.NFKC.apply(" ＡＢＣ１２３ "));
    }

    @Test
    @DisplayName("digits keeps only digits, after nfkc")
    void digits() {
      assertEquals("09012345678", AttributeNormalization.DIGITS.apply("０９０-1234-5678"));
      assertEquals("819012345678", AttributeNormalization.DIGITS.apply("+81 90 1234 5678"));
    }

    @Test
    @DisplayName("date accepts the usual written forms and writes yyyy-MM-dd")
    void date() {
      assertEquals("2000-01-05", AttributeNormalization.DATE.apply("2000-01-05"));
      assertEquals("2000-01-05", AttributeNormalization.DATE.apply("2000/1/5"));
      assertEquals("2000-01-05", AttributeNormalization.DATE.apply("20000105"));
      assertEquals("2000-01-05", AttributeNormalization.DATE.apply("２０００／０１／０５"));
    }

    @Test
    @DisplayName("a value that cannot be normalized becomes null")
    void unnormalizable() {
      assertNull(AttributeNormalization.DATE.apply("2000-02-30"));
      assertNull(AttributeNormalization.DATE.apply("not a date"));
      assertNull(AttributeNormalization.DIGITS.apply("abc"));
      assertNull(AttributeNormalization.EXACT.apply(""));
      assertNull(AttributeNormalization.EXACT.apply(null));
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
