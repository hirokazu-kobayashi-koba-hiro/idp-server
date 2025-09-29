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

package org.idp.server.core.extension.identity.verification.application.pre_hook.additional_parameter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationErrorDetails;
import org.junit.jupiter.api.Test;

class AdditionalParameterResolveResultTest {

  @Test
  void success_shouldCreateSuccessfulResult() {
    Map<String, Object> data = Map.of("access_token", "token123", "user_id", "user456");

    AdditionalParameterResolveResult result = AdditionalParameterResolveResult.success(data);

    assertTrue(result.isSuccess());
    assertFalse(result.isFailFast());
    assertFalse(result.isResilientError());
    assertEquals(data, result.getData());
    assertNull(result.getErrorDetails());
  }

  @Test
  void failFastError_shouldCreateFailFastErrorResult() {
    IdentityVerificationErrorDetails errorDetails =
        IdentityVerificationErrorDetails.builder()
            .error("network_failure")
            .errorDescription("Failed to connect to external service")
            .addErrorDetail("error_type", "NETWORK_ERROR")
            .addErrorDetail("retryable", true)
            .build();

    AdditionalParameterResolveResult result =
        AdditionalParameterResolveResult.failFastError(errorDetails);

    assertFalse(result.isSuccess());
    assertTrue(result.isFailFast());
    assertFalse(result.isResilientError());
    assertNotNull(result.getData());
    assertEquals(errorDetails, result.getErrorDetails());
  }

  @Test
  void resilientError_shouldCreateResilientErrorResult() {
    IdentityVerificationErrorDetails errorDetails =
        IdentityVerificationErrorDetails.builder()
            .error("service_unavailable")
            .errorDescription("External service temporarily unavailable")
            .addErrorDetail("error_type", "SERVICE_ERROR")
            .addErrorDetail("retryable", true)
            .build();

    Map<String, Object> fallbackData =
        Map.of(
            "status_code", 503,
            "error", "service_unavailable",
            "error_description", "Service temporarily unavailable");

    AdditionalParameterResolveResult result =
        AdditionalParameterResolveResult.resilientError(errorDetails, fallbackData);

    assertFalse(result.isSuccess());
    assertFalse(result.isFailFast());
    assertTrue(result.isResilientError());
    assertEquals(fallbackData, result.getData());
    assertEquals(errorDetails, result.getErrorDetails());
  }

  @Test
  void mutuallyExclusiveStates_shouldBeCorrect() {
    // Success state
    AdditionalParameterResolveResult successResult =
        AdditionalParameterResolveResult.success(Map.of("key", "value"));

    assertTrue(successResult.isSuccess());
    assertFalse(successResult.isFailFast());
    assertFalse(successResult.isResilientError());

    // Fail-fast error state
    IdentityVerificationErrorDetails errorDetails =
        IdentityVerificationErrorDetails.builder()
            .error("test_error")
            .errorDescription("Test error")
            .build();

    AdditionalParameterResolveResult failFastResult =
        AdditionalParameterResolveResult.failFastError(errorDetails);

    assertFalse(failFastResult.isSuccess());
    assertTrue(failFastResult.isFailFast());
    assertFalse(failFastResult.isResilientError());

    // Resilient error state
    AdditionalParameterResolveResult resilientResult =
        AdditionalParameterResolveResult.resilientError(errorDetails, Map.of("fallback", "data"));

    assertFalse(resilientResult.isSuccess());
    assertFalse(resilientResult.isFailFast());
    assertTrue(resilientResult.isResilientError());
  }

  @Test
  void success_shouldBeValid() {
    AdditionalParameterResolveResult result = AdditionalParameterResolveResult.success(null);

    assertTrue(result.isSuccess());
    assertNotNull(result.getData());
  }

  @Test
  void resilientErrorFallbackData_shouldBeValid() {
    IdentityVerificationErrorDetails errorDetails =
        IdentityVerificationErrorDetails.builder()
            .error("test_error")
            .errorDescription("Test error")
            .build();

    AdditionalParameterResolveResult result =
        AdditionalParameterResolveResult.resilientError(errorDetails, null);

    assertTrue(result.isResilientError());
    assertNotNull(result.getData());
    assertEquals(errorDetails, result.getErrorDetails());
  }
}
