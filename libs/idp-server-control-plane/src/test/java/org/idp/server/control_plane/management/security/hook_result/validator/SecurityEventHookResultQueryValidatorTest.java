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

package org.idp.server.control_plane.management.security.hook_result.validator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.platform.security.hook.SecurityEventHookResultQueries;
import org.junit.jupiter.api.Test;

class SecurityEventHookResultQueryValidatorTest {

  @Test
  void testValidate_WithValidParameters_Success() {
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("security_event_id", "123e4567-e89b-12d3-a456-426614174000");
    queryParams.put("event_type", "user_created");
    queryParams.put("status", "success");
    queryParams.put("limit", "100");
    queryParams.put("offset", "0");

    SecurityEventHookResultQueries queries = new SecurityEventHookResultQueries(queryParams);
    SecurityEventHookResultQueryValidator validator =
        new SecurityEventHookResultQueryValidator(queries);

    assertDoesNotThrow(() -> validator.validate());
  }

  @Test
  void testValidate_WithMinimumParameters_Success() {
    Map<String, String> queryParams = new HashMap<>();

    SecurityEventHookResultQueries queries = new SecurityEventHookResultQueries(queryParams);
    SecurityEventHookResultQueryValidator validator =
        new SecurityEventHookResultQueryValidator(queries);

    assertDoesNotThrow(() -> validator.validate());
  }

  @Test
  void testValidate_WithMultipleEventTypes_Success() {
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("event_type", "user_created,user_updated,user_deleted");
    queryParams.put("limit", "50");

    SecurityEventHookResultQueries queries = new SecurityEventHookResultQueries(queryParams);
    SecurityEventHookResultQueryValidator validator =
        new SecurityEventHookResultQueryValidator(queries);

    assertDoesNotThrow(() -> validator.validate());
  }

  @Test
  void testValidate_LimitZero_ThrowsException() {
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("limit", "0");

    SecurityEventHookResultQueries queries = new SecurityEventHookResultQueries(queryParams);
    SecurityEventHookResultQueryValidator validator =
        new SecurityEventHookResultQueryValidator(queries);

    InvalidRequestException exception =
        assertThrows(InvalidRequestException.class, () -> validator.validate());
    assertTrue(
        exception.getMessage().contains("Security event hook result query validation failed"));
  }

  @Test
  void testValidate_LimitNegative_ThrowsException() {
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("limit", "-1");

    SecurityEventHookResultQueries queries = new SecurityEventHookResultQueries(queryParams);
    SecurityEventHookResultQueryValidator validator =
        new SecurityEventHookResultQueryValidator(queries);

    InvalidRequestException exception =
        assertThrows(InvalidRequestException.class, () -> validator.validate());
    assertTrue(
        exception.getMessage().contains("Security event hook result query validation failed"));
  }

  @Test
  void testValidate_LimitExceedsMax_ThrowsException() {
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("limit", "1001");

    SecurityEventHookResultQueries queries = new SecurityEventHookResultQueries(queryParams);
    SecurityEventHookResultQueryValidator validator =
        new SecurityEventHookResultQueryValidator(queries);

    InvalidRequestException exception =
        assertThrows(InvalidRequestException.class, () -> validator.validate());
    assertTrue(
        exception.getMessage().contains("Security event hook result query validation failed"));
  }

  @Test
  void testValidate_LimitAtBoundary_Success() {
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("limit", "1000");

    SecurityEventHookResultQueries queries = new SecurityEventHookResultQueries(queryParams);
    SecurityEventHookResultQueryValidator validator =
        new SecurityEventHookResultQueryValidator(queries);

    assertDoesNotThrow(() -> validator.validate());
  }

  @Test
  void testValidate_OffsetNegative_ThrowsException() {
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("offset", "-1");

    SecurityEventHookResultQueries queries = new SecurityEventHookResultQueries(queryParams);
    SecurityEventHookResultQueryValidator validator =
        new SecurityEventHookResultQueryValidator(queries);

    InvalidRequestException exception =
        assertThrows(InvalidRequestException.class, () -> validator.validate());
    assertTrue(
        exception.getMessage().contains("Security event hook result query validation failed"));
  }

  @Test
  void testValidate_OffsetZero_Success() {
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("offset", "0");

    SecurityEventHookResultQueries queries = new SecurityEventHookResultQueries(queryParams);
    SecurityEventHookResultQueryValidator validator =
        new SecurityEventHookResultQueryValidator(queries);

    assertDoesNotThrow(() -> validator.validate());
  }

  @Test
  void testValidate_InvalidSecurityEventId_ThrowsException() {
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("security_event_id", "not-a-uuid");

    SecurityEventHookResultQueries queries = new SecurityEventHookResultQueries(queryParams);
    SecurityEventHookResultQueryValidator validator =
        new SecurityEventHookResultQueryValidator(queries);

    InvalidRequestException exception =
        assertThrows(InvalidRequestException.class, () -> validator.validate());
    assertTrue(
        exception.getMessage().contains("Security event hook result query validation failed"));
  }

  @Test
  void testValidate_ValidSecurityEventId_Success() {
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("security_event_id", "123e4567-e89b-12d3-a456-426614174000");

    SecurityEventHookResultQueries queries = new SecurityEventHookResultQueries(queryParams);
    SecurityEventHookResultQueryValidator validator =
        new SecurityEventHookResultQueryValidator(queries);

    assertDoesNotThrow(() -> validator.validate());
  }

  @Test
  void testValidate_InvalidStatus_ThrowsException() {
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("status", "invalid_status");

    SecurityEventHookResultQueries queries = new SecurityEventHookResultQueries(queryParams);
    SecurityEventHookResultQueryValidator validator =
        new SecurityEventHookResultQueryValidator(queries);

    InvalidRequestException exception =
        assertThrows(InvalidRequestException.class, () -> validator.validate());
    assertTrue(
        exception.getMessage().contains("Security event hook result query validation failed"));
  }

  @Test
  void testValidate_ValidStatus_Success() {
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("status", "success");

    SecurityEventHookResultQueries queries = new SecurityEventHookResultQueries(queryParams);
    SecurityEventHookResultQueryValidator validator =
        new SecurityEventHookResultQueryValidator(queries);

    assertDoesNotThrow(() -> validator.validate());
  }

  @Test
  void testValidate_ValidStatusPending_Success() {
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("status", "pending");

    SecurityEventHookResultQueries queries = new SecurityEventHookResultQueries(queryParams);
    SecurityEventHookResultQueryValidator validator =
        new SecurityEventHookResultQueryValidator(queries);

    assertDoesNotThrow(() -> validator.validate());
  }

  @Test
  void testValidate_ValidStatusFailure_Success() {
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("status", "failure");

    SecurityEventHookResultQueries queries = new SecurityEventHookResultQueries(queryParams);
    SecurityEventHookResultQueryValidator validator =
        new SecurityEventHookResultQueryValidator(queries);

    assertDoesNotThrow(() -> validator.validate());
  }

  @Test
  void testValidate_WithFromAndTo_Success() {
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("from", "2025-01-01T00:00:00Z");
    queryParams.put("to", "2025-01-31T23:59:59Z");

    SecurityEventHookResultQueries queries = new SecurityEventHookResultQueries(queryParams);
    SecurityEventHookResultQueryValidator validator =
        new SecurityEventHookResultQueryValidator(queries);

    assertDoesNotThrow(() -> validator.validate());
  }

  @Test
  void testValidate_EmptyEventType_ThrowsException() {
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("event_type", "");

    SecurityEventHookResultQueries queries = new SecurityEventHookResultQueries(queryParams);
    SecurityEventHookResultQueryValidator validator =
        new SecurityEventHookResultQueryValidator(queries);

    InvalidRequestException exception =
        assertThrows(InvalidRequestException.class, () -> validator.validate());
    assertTrue(
        exception.getMessage().contains("Security event hook result query validation failed"));
  }

  @Test
  void testValidate_AllParameters_Success() {
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("security_event_id", "123e4567-e89b-12d3-a456-426614174000");
    queryParams.put("event_type", "user_created,user_updated");
    queryParams.put("hook_type", "webhook");
    queryParams.put("status", "success");
    queryParams.put("from", "2025-01-01T00:00:00Z");
    queryParams.put("to", "2025-01-31T23:59:59Z");
    queryParams.put("limit", "100");
    queryParams.put("offset", "20");

    SecurityEventHookResultQueries queries = new SecurityEventHookResultQueries(queryParams);
    SecurityEventHookResultQueryValidator validator =
        new SecurityEventHookResultQueryValidator(queries);

    assertDoesNotThrow(() -> validator.validate());
  }
}
