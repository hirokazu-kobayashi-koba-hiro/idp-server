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

package org.idp.server.core.openid.identity.authentication;

import static org.junit.jupiter.api.Assertions.*;

import org.idp.server.platform.multi_tenancy.tenant.policy.PasswordPolicyConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PasswordPolicyValidatorTest {

  @Nested
  @DisplayName("Default policy (minimum 8 characters only)")
  class DefaultPolicy {

    @Test
    @DisplayName("Should accept password with exactly 8 characters")
    void testMinimumLengthAccepted() {
      PasswordPolicyValidator policy = new PasswordPolicyValidator();

      PasswordPolicyValidationResult result = policy.validate("12345678");

      assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Should accept password with more than 8 characters")
    void testLongerPasswordAccepted() {
      PasswordPolicyValidator policy = new PasswordPolicyValidator();

      PasswordPolicyValidationResult result = policy.validate("123456789abcdef");

      assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Should reject password with less than 8 characters")
    void testShorterPasswordRejected() {
      PasswordPolicyValidator policy = new PasswordPolicyValidator();

      PasswordPolicyValidationResult result = policy.validate("1234567");

      assertFalse(result.isValid());
      assertEquals("Password must be at least 8 characters long.", result.errorMessage());
    }

    @Test
    @DisplayName("Should reject null password")
    void testNullPasswordRejected() {
      PasswordPolicyValidator policy = new PasswordPolicyValidator();

      PasswordPolicyValidationResult result = policy.validate(null);

      assertFalse(result.isValid());
      assertEquals("Password is required.", result.errorMessage());
    }

    @Test
    @DisplayName("Should reject empty password")
    void testEmptyPasswordRejected() {
      PasswordPolicyValidator policy = new PasswordPolicyValidator();

      PasswordPolicyValidationResult result = policy.validate("");

      assertFalse(result.isValid());
      assertEquals("Password is required.", result.errorMessage());
    }

    @Test
    @DisplayName("Should reject password with only spaces")
    void testWhitespaceOnlyPasswordRejected() {
      PasswordPolicyValidator policy = new PasswordPolicyValidator();

      PasswordPolicyValidationResult result = policy.validate("        ");

      assertFalse(result.isValid());
      assertEquals("Password is required.", result.errorMessage());
    }

    @Test
    @DisplayName("Should accept password without uppercase (default policy)")
    void testNoUppercaseAcceptedByDefault() {
      PasswordPolicyValidator policy = new PasswordPolicyValidator();

      PasswordPolicyValidationResult result = policy.validate("alllowercase123");

      assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Should accept password without lowercase (default policy)")
    void testNoLowercaseAcceptedByDefault() {
      PasswordPolicyValidator policy = new PasswordPolicyValidator();

      PasswordPolicyValidationResult result = policy.validate("ALLUPPERCASE123");

      assertTrue(result.isValid());
    }
  }

  @Nested
  @DisplayName("Uppercase requirement")
  class UppercaseRequirement {

    @Test
    @DisplayName("Should accept password with uppercase when required")
    void testUppercaseAccepted() {
      PasswordPolicyConfig config = new PasswordPolicyConfig(8, true, false, false, false, 0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      PasswordPolicyValidationResult result = policy.validate("Password123");

      assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Should reject password without uppercase when required")
    void testNoUppercaseRejected() {
      PasswordPolicyConfig config = new PasswordPolicyConfig(8, true, false, false, false, 0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      PasswordPolicyValidationResult result = policy.validate("password123");

      assertFalse(result.isValid());
      assertEquals("Password must contain at least one uppercase letter.", result.errorMessage());
    }
  }

  @Nested
  @DisplayName("Lowercase requirement")
  class LowercaseRequirement {

    @Test
    @DisplayName("Should accept password with lowercase when required")
    void testLowercaseAccepted() {
      PasswordPolicyConfig config = new PasswordPolicyConfig(8, false, true, false, false, 0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      PasswordPolicyValidationResult result = policy.validate("Password123");

      assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Should reject password without lowercase when required")
    void testNoLowercaseRejected() {
      PasswordPolicyConfig config = new PasswordPolicyConfig(8, false, true, false, false, 0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      PasswordPolicyValidationResult result = policy.validate("PASSWORD123");

      assertFalse(result.isValid());
      assertEquals("Password must contain at least one lowercase letter.", result.errorMessage());
    }
  }

  @Nested
  @DisplayName("Number requirement")
  class NumberRequirement {

    @Test
    @DisplayName("Should accept password with number when required")
    void testNumberAccepted() {
      PasswordPolicyConfig config = new PasswordPolicyConfig(8, false, false, true, false, 0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      PasswordPolicyValidationResult result = policy.validate("Password123");

      assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Should reject password without number when required")
    void testNoNumberRejected() {
      PasswordPolicyConfig config = new PasswordPolicyConfig(8, false, false, true, false, 0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      PasswordPolicyValidationResult result = policy.validate("PasswordOnly");

      assertFalse(result.isValid());
      assertEquals("Password must contain at least one number.", result.errorMessage());
    }
  }

  @Nested
  @DisplayName("Special character requirement")
  class SpecialCharRequirement {

    @Test
    @DisplayName("Should accept password with special character when required")
    void testSpecialCharAccepted() {
      PasswordPolicyConfig config = new PasswordPolicyConfig(8, false, false, false, true, 0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      PasswordPolicyValidationResult result = policy.validate("Password123!");

      assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Should reject password without special character when required")
    void testNoSpecialCharRejected() {
      PasswordPolicyConfig config = new PasswordPolicyConfig(8, false, false, false, true, 0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      PasswordPolicyValidationResult result = policy.validate("Password123");

      assertFalse(result.isValid());
      assertEquals("Password must contain at least one special character.", result.errorMessage());
    }

    @Test
    @DisplayName("Should accept various special characters")
    void testVariousSpecialCharsAccepted() {
      PasswordPolicyConfig config = new PasswordPolicyConfig(8, false, false, false, true, 0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      String[] specialChars = {"!", "@", "#", "$", "%", "^", "&", "*", "(", ")", ".", ","};

      for (String specialChar : specialChars) {
        PasswordPolicyValidationResult result = policy.validate("Password" + specialChar);
        assertTrue(
            result.isValid(),
            "Password with special char '" + specialChar + "' should be accepted");
      }
    }
  }

  @Nested
  @DisplayName("Combined requirements")
  class CombinedRequirements {

    @Test
    @DisplayName("Should accept password meeting all requirements")
    void testAllRequirementsMet() {
      PasswordPolicyConfig config = new PasswordPolicyConfig(12, true, true, true, true, 0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      PasswordPolicyValidationResult result = policy.validate("MyP@ssw0rd123");

      assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Should reject password missing one requirement (uppercase)")
    void testMissingUppercase() {
      PasswordPolicyConfig config = new PasswordPolicyConfig(12, true, true, true, true, 0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      PasswordPolicyValidationResult result = policy.validate("myp@ssw0rd123");

      assertFalse(result.isValid());
      assertEquals("Password must contain at least one uppercase letter.", result.errorMessage());
    }

    @Test
    @DisplayName("Should reject password missing one requirement (lowercase)")
    void testMissingLowercase() {
      PasswordPolicyConfig config = new PasswordPolicyConfig(12, true, true, true, true, 0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      PasswordPolicyValidationResult result = policy.validate("MYP@SSW0RD123");

      assertFalse(result.isValid());
      assertEquals("Password must contain at least one lowercase letter.", result.errorMessage());
    }

    @Test
    @DisplayName("Should reject password missing one requirement (number)")
    void testMissingNumber() {
      PasswordPolicyConfig config = new PasswordPolicyConfig(12, true, true, true, true, 0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      PasswordPolicyValidationResult result = policy.validate("MyP@ssword!!!");

      assertFalse(result.isValid());
      assertEquals("Password must contain at least one number.", result.errorMessage());
    }

    @Test
    @DisplayName("Should reject password missing one requirement (special char)")
    void testMissingSpecialChar() {
      PasswordPolicyConfig config = new PasswordPolicyConfig(12, true, true, true, true, 0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      PasswordPolicyValidationResult result = policy.validate("MyPassw0rd123");

      assertFalse(result.isValid());
      assertEquals("Password must contain at least one special character.", result.errorMessage());
    }

    @Test
    @DisplayName("Should reject password too short even with all complexity requirements")
    void testTooShortWithComplexity() {
      PasswordPolicyConfig config = new PasswordPolicyConfig(12, true, true, true, true, 0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      PasswordPolicyValidationResult result = policy.validate("MyP@ss0");

      assertFalse(result.isValid());
      assertEquals("Password must be at least 12 characters long.", result.errorMessage());
    }
  }

  @Nested
  @DisplayName("Custom minimum length")
  class CustomMinimumLength {

    @Test
    @DisplayName("Should enforce custom minimum length of 16 characters")
    void testCustomMinLength16() {
      PasswordPolicyConfig config = new PasswordPolicyConfig(16, false, false, false, false, 0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      PasswordPolicyValidationResult valid = policy.validate("1234567890123456");
      PasswordPolicyValidationResult invalid = policy.validate("123456789012345");

      assertTrue(valid.isValid());
      assertFalse(invalid.isValid());
      assertEquals("Password must be at least 16 characters long.", invalid.errorMessage());
    }
  }

  @Nested
  @DisplayName("Maximum length (BCrypt limitation)")
  class MaximumLength {

    @Test
    @DisplayName("Should accept password with exactly 72 characters (BCrypt limit)")
    void testMaximumLengthAccepted() {
      PasswordPolicyValidator policy = new PasswordPolicyValidator();
      String maxPassword = "A".repeat(72);

      PasswordPolicyValidationResult result = policy.validate(maxPassword);

      assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Should reject password exceeding 72 characters (BCrypt limit)")
    void testExceedingMaximumLengthRejected() {
      PasswordPolicyValidator policy = new PasswordPolicyValidator();
      String tooLongPassword = "A".repeat(73);

      PasswordPolicyValidationResult result = policy.validate(tooLongPassword);

      assertFalse(result.isValid());
      assertEquals("Password must not exceed 72 characters.", result.errorMessage());
    }

    @Test
    @DisplayName("Should reject password with 100 characters")
    void testVeryLongPasswordRejected() {
      PasswordPolicyValidator policy = new PasswordPolicyValidator();
      String veryLongPassword = "A".repeat(100);

      PasswordPolicyValidationResult result = policy.validate(veryLongPassword);

      assertFalse(result.isValid());
      assertEquals("Password must not exceed 72 characters.", result.errorMessage());
    }

    @Test
    @DisplayName("Should enforce custom maximum length")
    void testCustomMaxLength() {
      PasswordPolicyConfig config =
          new PasswordPolicyConfig(8, 50, false, false, false, false, null, null, 0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      String validPassword = "A".repeat(50);
      String invalidPassword = "A".repeat(51);

      PasswordPolicyValidationResult valid = policy.validate(validPassword);
      PasswordPolicyValidationResult invalid = policy.validate(invalidPassword);

      assertTrue(valid.isValid());
      assertFalse(invalid.isValid());
      assertEquals("Password must not exceed 50 characters.", invalid.errorMessage());
    }
  }

  @Nested
  @DisplayName("Custom regex pattern")
  class CustomRegexPattern {

    @Test
    @DisplayName("Should accept password matching custom regex")
    void testCustomRegexAccepted() {
      // Regex: Must contain "idp" (case-insensitive)
      PasswordPolicyConfig config =
          new PasswordPolicyConfig(
              8,
              false,
              false,
              false,
              false,
              ".*(?i)idp.*",
              "Password must contain 'idp' (case-insensitive)",
              0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      PasswordPolicyValidationResult result1 = policy.validate("myIDPpassword");
      PasswordPolicyValidationResult result2 = policy.validate("idp12345");
      PasswordPolicyValidationResult result3 = policy.validate("SecureIdpPass");

      assertTrue(result1.isValid());
      assertTrue(result2.isValid());
      assertTrue(result3.isValid());
    }

    @Test
    @DisplayName("Should reject password not matching custom regex")
    void testCustomRegexRejected() {
      // Regex: Must contain "idp" (case-insensitive)
      PasswordPolicyConfig config =
          new PasswordPolicyConfig(
              8,
              false,
              false,
              false,
              false,
              ".*(?i)idp.*",
              "Password must contain 'idp' (case-insensitive)",
              0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      PasswordPolicyValidationResult result = policy.validate("SecurePassword123");

      assertFalse(result.isValid());
      assertEquals("Password must contain 'idp' (case-insensitive)", result.errorMessage());
    }

    @Test
    @DisplayName("Should use default error message when not provided")
    void testCustomRegexDefaultErrorMessage() {
      // Regex without custom error message
      PasswordPolicyConfig config =
          new PasswordPolicyConfig(8, false, false, false, false, ".*idp.*", null, 0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      PasswordPolicyValidationResult result = policy.validate("SecurePassword123");

      assertFalse(result.isValid());
      assertEquals("Password does not match the required pattern.", result.errorMessage());
    }

    @Test
    @DisplayName("Should accept complex regex: Japanese company email format")
    void testComplexRegexJapaneseCompany() {
      // Regex: Must be Japanese company email (ends with .co.jp)
      PasswordPolicyConfig config =
          new PasswordPolicyConfig(
              8,
              false,
              false,
              false,
              false,
              ".*@[a-zA-Z0-9.-]+\\.co\\.jp$",
              "Password must end with a valid Japanese company email pattern (@xxx.co.jp)",
              0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      PasswordPolicyValidationResult valid1 = policy.validate("user@example.co.jp");
      PasswordPolicyValidationResult valid2 = policy.validate("admin@my-company.co.jp");
      PasswordPolicyValidationResult invalid = policy.validate("user@example.com");

      assertTrue(valid1.isValid());
      assertTrue(valid2.isValid());
      assertFalse(invalid.isValid());
    }

    @Test
    @DisplayName("Should accept complex regex: No sequential numbers")
    void testComplexRegexNoSequentialNumbers() {
      // Regex: Must NOT contain sequential numbers (123, 234, etc.)
      PasswordPolicyConfig config =
          new PasswordPolicyConfig(
              8,
              false,
              false,
              false,
              false,
              "^(?!.*(012|123|234|345|456|567|678|789)).*$",
              "Password must not contain sequential numbers",
              0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      PasswordPolicyValidationResult valid1 = policy.validate("Pass1024word");
      PasswordPolicyValidationResult valid2 = policy.validate("Secure8019Pass");
      PasswordPolicyValidationResult invalid1 = policy.validate("Pass123word");
      PasswordPolicyValidationResult invalid2 = policy.validate("Test456Pass");

      assertTrue(valid1.isValid());
      assertTrue(valid2.isValid());
      assertFalse(invalid1.isValid());
      assertFalse(invalid2.isValid());
    }

    @Test
    @DisplayName("Should handle invalid regex gracefully")
    void testInvalidRegexPattern() {
      // Invalid regex: unclosed bracket
      PasswordPolicyConfig config =
          new PasswordPolicyConfig(8, false, false, false, false, ".*[a-z", "Invalid pattern", 0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      PasswordPolicyValidationResult result = policy.validate("AnyPassword123");

      assertFalse(result.isValid());
      assertEquals(
          "Password policy configuration error: invalid regex pattern.", result.errorMessage());
    }

    @Test
    @DisplayName("Should ignore empty custom regex")
    void testEmptyCustomRegex() {
      PasswordPolicyConfig config =
          new PasswordPolicyConfig(8, false, false, false, false, "", "", 0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      PasswordPolicyValidationResult result = policy.validate("AnyPassword");

      assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Should combine custom regex with other requirements")
    void testCustomRegexWithOtherRequirements() {
      // Regex: Must contain "secure" + uppercase + number
      PasswordPolicyConfig config =
          new PasswordPolicyConfig(
              10, true, false, true, false, ".*(?i)secure.*", "Password must contain 'secure'", 0);
      PasswordPolicyValidator policy = new PasswordPolicyValidator(config);

      PasswordPolicyValidationResult valid = policy.validate("MySecure123Pass");
      PasswordPolicyValidationResult invalidNoRegex = policy.validate("MyPassword123");
      PasswordPolicyValidationResult invalidNoUppercase = policy.validate("mysecure123pass");
      PasswordPolicyValidationResult invalidNoNumber = policy.validate("MySecurePass");

      assertTrue(valid.isValid());
      assertFalse(invalidNoRegex.isValid());
      assertEquals("Password must contain 'secure'", invalidNoRegex.errorMessage());
      assertFalse(invalidNoUppercase.isValid());
      assertEquals(
          "Password must contain at least one uppercase letter.",
          invalidNoUppercase.errorMessage());
      assertFalse(invalidNoNumber.isValid());
      assertEquals("Password must contain at least one number.", invalidNoNumber.errorMessage());
    }
  }
}
