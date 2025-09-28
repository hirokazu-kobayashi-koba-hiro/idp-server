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

package org.idp.server.platform.security.hook;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;

public class SecurityEventHookResult {

  SecurityEventHookResultIdentifier identifier;
  SecurityEventHookStatus status;
  SecurityEventHookType type;
  SecurityEvent securityEvent;
  Map<String, Object> contents;
  LocalDateTime createdAt;
  LocalDateTime updatedAt;

  public SecurityEventHookResult() {}

  /**
   * Creates a successful hook result with detailed execution context.
   *
   * @param configuration hook configuration
   * @param securityEvent original security event
   * @param executionDetails detailed execution information including request, response, and
   *     metadata
   * @param executionDurationMs execution duration in milliseconds
   * @return successful hook result with execution context
   */
  public static SecurityEventHookResult successWithContext(
      SecurityEventHookConfiguration configuration,
      SecurityEvent securityEvent,
      Map<String, Object> executionDetails,
      long executionDurationMs) {

    SecurityEventHookExecutionContext context =
        SecurityEventHookExecutionContext.success(
            configuration, SystemDateTime.now(), executionDetails, executionDurationMs);

    SecurityEventHookResultIdentifier identifier =
        new SecurityEventHookResultIdentifier(UUID.randomUUID().toString());
    LocalDateTime createdAt = SystemDateTime.now();
    return new SecurityEventHookResult(
        identifier,
        SecurityEventHookStatus.SUCCESS,
        configuration.hookType(),
        securityEvent,
        context.toMap(),
        createdAt,
        createdAt);
  }

  /**
   * Creates a failed hook result with detailed execution context.
   *
   * @param configuration hook configuration
   * @param securityEvent original security event
   * @param executionDetails detailed execution information including request, response, and
   *     metadata (if available)
   * @param executionDurationMs execution duration in milliseconds
   * @param errorType type of error that occurred
   * @param errorMessage detailed error message
   * @return failed hook result with execution context
   */
  public static SecurityEventHookResult failureWithContext(
      SecurityEventHookConfiguration configuration,
      SecurityEvent securityEvent,
      Map<String, Object> executionDetails,
      long executionDurationMs,
      String errorType,
      String errorMessage) {

    SecurityEventHookExecutionContext context =
        SecurityEventHookExecutionContext.failure(
            configuration,
            SystemDateTime.now(),
            executionDetails,
            executionDurationMs,
            errorType,
            errorMessage);

    SecurityEventHookResultIdentifier identifier =
        new SecurityEventHookResultIdentifier(UUID.randomUUID().toString());
    LocalDateTime createdAt = SystemDateTime.now();
    return new SecurityEventHookResult(
        identifier,
        SecurityEventHookStatus.FAILURE,
        configuration.hookType(),
        securityEvent,
        context.toMap(),
        createdAt,
        createdAt);
  }

  public SecurityEventHookResult(
      SecurityEventHookResultIdentifier identifier,
      SecurityEventHookStatus status,
      SecurityEventHookType type,
      SecurityEvent securityEvent,
      Map<String, Object> contents,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    this.identifier = identifier;
    this.status = status;
    this.type = type;
    this.securityEvent = securityEvent;
    this.contents = contents;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public SecurityEventHookResultIdentifier identifier() {
    return identifier;
  }

  public SecurityEventHookStatus status() {
    return status;
  }

  public SecurityEventHookType type() {
    return type;
  }

  public SecurityEvent securityEvent() {
    return securityEvent;
  }

  public Map<String, Object> contents() {
    return contents;
  }

  public LocalDateTime createdAt() {
    return createdAt;
  }

  public LocalDateTime updatedAt() {
    return updatedAt;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", identifier.value());
    map.put("status", status);
    map.put("type", type.name());
    map.put("security_event", securityEvent.toMap());
    map.put("contents", contents);
    map.put("created_at", createdAt);
    map.put("updated_at", updatedAt);
    return map;
  }

  public boolean exists() {
    return identifier != null && identifier.exists();
  }

  public boolean isSuccess() {
    return status.isSuccess();
  }

  public boolean isFailure() {
    return status.isFailure();
  }
}
