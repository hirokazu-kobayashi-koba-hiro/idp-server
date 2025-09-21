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

package org.idp.server.core.extension.identity.verification.io;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IdentityVerificationErrorDetailsTest {

  @Test
  void build_shouldCreateErrorDetailsWithAllFields() {
    IdentityVerificationErrorDetails errorDetails =
        IdentityVerificationErrorDetails.builder()
            .error("pre_hook_validation_failed")
            .errorDescription("Pre-hook validation failed for identity verification request")
            .addErrorDetail("verification_type", "age_verification_failed")
            .addErrorDetail("field", "birth_date")
            .addErrorMessage("Age verification: minimum age not met")
            .addErrorMessage("Missing required field: birth_date")
            .build();

    assertEquals("pre_hook_validation_failed", errorDetails.error());
    assertEquals(
        "Pre-hook validation failed for identity verification request",
        errorDetails.errorDescription());
    assertEquals("age_verification_failed", errorDetails.errorDetails().get("verification_type"));
    assertEquals("birth_date", errorDetails.errorDetails().get("field"));
    assertEquals(2, errorDetails.errorMessages().size());
    assertTrue(errorDetails.errorMessages().contains("Age verification: minimum age not met"));
    assertTrue(errorDetails.errorMessages().contains("Missing required field: birth_date"));
  }

  @Test
  void toMap_shouldReturnOAuth2CompliantFormat() {
    IdentityVerificationErrorDetails errorDetails =
        IdentityVerificationErrorDetails.builder()
            .error("invalid_request_format")
            .errorDescription("Request validation failed due to invalid input format")
            .addErrorDetail("email", "invalid_format")
            .addErrorDetail("phone_number", "required_field_missing")
            .addErrorMessage("Email format is invalid")
            .addErrorMessage("Phone number is required")
            .build();

    Map<String, Object> result = errorDetails.toMap();

    assertEquals("invalid_request_format", result.get("error"));
    assertEquals(
        "Request validation failed due to invalid input format", result.get("error_description"));

    @SuppressWarnings("unchecked")
    Map<String, Object> details = (Map<String, Object>) result.get("error_details");
    assertEquals("invalid_format", details.get("email"));
    assertEquals("required_field_missing", details.get("phone_number"));

    @SuppressWarnings("unchecked")
    List<String> messages = (List<String>) result.get("error_messages");
    assertEquals(2, messages.size());
    assertTrue(messages.contains("Email format is invalid"));
    assertTrue(messages.contains("Phone number is required"));
  }

  @Test
  void builder_shouldThrowExceptionWhenErrorCodeMissing() {
    assertThrows(
        IllegalArgumentException.class,
        () -> IdentityVerificationErrorDetails.builder().errorDescription("Description").build());
  }

  @Test
  void builder_shouldThrowExceptionWhenErrorDescriptionMissing() {
    assertThrows(
        IllegalArgumentException.class,
        () -> IdentityVerificationErrorDetails.builder().error("error_code").build());
  }

  @Test
  void errorDetails_shouldReturnImmutableCopy() {
    IdentityVerificationErrorDetails errorDetails =
        IdentityVerificationErrorDetails.builder()
            .error("test_error")
            .errorDescription("Test description")
            .addErrorDetail("key", "value")
            .build();

    Map<String, Object> details = errorDetails.errorDetails();

    assertThrows(UnsupportedOperationException.class, () -> details.put("new_key", "new_value"));
  }

  @Test
  void errorMessages_shouldReturnImmutableCopy() {
    IdentityVerificationErrorDetails errorDetails =
        IdentityVerificationErrorDetails.builder()
            .error("test_error")
            .errorDescription("Test description")
            .addErrorMessage("Test message")
            .build();

    List<String> messages = errorDetails.errorMessages();

    assertThrows(UnsupportedOperationException.class, () -> messages.add("New message"));
  }
}
