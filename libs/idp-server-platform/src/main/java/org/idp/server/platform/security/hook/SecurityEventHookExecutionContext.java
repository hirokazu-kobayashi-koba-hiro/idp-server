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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;

/**
 * Comprehensive context information for security event hook execution.
 *
 * <p>This class captures detailed information about hook execution for retry mechanisms, debugging,
 * and audit purposes. The data is stored in the security_event_hook_execution_payload column.
 */
public class SecurityEventHookExecutionContext {

  private final HookExecutionContext hookExecutionContext;
  private final ExecutionResult executionResult;

  public SecurityEventHookExecutionContext(
      HookExecutionContext hookExecutionContext, ExecutionResult executionResult) {
    this.hookExecutionContext = hookExecutionContext;
    this.executionResult = executionResult;
  }

  /**
   * Creates execution context for successful hook execution.
   *
   * @param configuration hook configuration
   * @param securityEvent original security event
   * @param executionTimestamp when the hook was executed
   * @param executionDetails detailed execution information including request, response, and
   *     metadata
   * @param executionDurationMs execution duration in milliseconds
   * @return execution context for success case
   */
  public static SecurityEventHookExecutionContext success(
      SecurityEventHookConfiguration configuration,
      SecurityEvent securityEvent,
      Instant executionTimestamp,
      Map<String, Object> executionDetails,
      long executionDurationMs) {

    HookExecutionContext hookContext =
        new HookExecutionContext(
            configuration.hookType().name(),
            configuration.identifier().value(),
            executionTimestamp,
            securityEvent.tenantIdentifierValue());

    ExecutionResult result = ExecutionResult.success(executionDetails, executionDurationMs);

    return new SecurityEventHookExecutionContext(hookContext, result);
  }

  /**
   * Creates execution context for failed hook execution.
   *
   * @param configuration hook configuration
   * @param securityEvent original security event
   * @param executionTimestamp when the hook was executed
   * @param executionDetails detailed execution information including request, response, and
   *     metadata (if available)
   * @param executionDurationMs execution duration in milliseconds
   * @param errorType type of error that occurred
   * @param errorMessage detailed error message
   * @return execution context for failure case
   */
  public static SecurityEventHookExecutionContext failure(
      SecurityEventHookConfiguration configuration,
      SecurityEvent securityEvent,
      Instant executionTimestamp,
      Map<String, Object> executionDetails,
      long executionDurationMs,
      String errorType,
      String errorMessage) {

    HookExecutionContext hookContext =
        new HookExecutionContext(
            configuration.hookType().name(),
            configuration.identifier().value(),
            executionTimestamp,
            securityEvent.tenantIdentifierValue());

    ExecutionResult result =
        ExecutionResult.failure(executionDetails, executionDurationMs, errorType, errorMessage);

    return new SecurityEventHookExecutionContext(hookContext, result);
  }

  /**
   * Converts this execution context to a Map for JSON serialization.
   *
   * @return Map representation suitable for storage in security_event_hook_execution_payload
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("hook_execution_context", hookExecutionContext.toMap());
    map.put("execution_result", executionResult.toMap());
    return map;
  }

  public HookExecutionContext hookExecutionContext() {
    return hookExecutionContext;
  }

  public ExecutionResult executionResult() {
    return executionResult;
  }

  /** Hook execution context containing metadata about the hook execution. */
  public static class HookExecutionContext {
    private final String hookType;
    private final String hookConfigurationId;
    private final Instant executionTimestamp;
    private final String tenantId;

    public HookExecutionContext(
        String hookType, String hookConfigurationId, Instant executionTimestamp, String tenantId) {
      this.hookType = hookType;
      this.hookConfigurationId = hookConfigurationId;
      this.executionTimestamp = executionTimestamp;
      this.tenantId = tenantId;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("hook_type", hookType);
      map.put("hook_configuration_id", hookConfigurationId);
      map.put("execution_timestamp", executionTimestamp.toString());
      map.put("tenant_id", tenantId);
      return map;
    }

    public String hookType() {
      return hookType;
    }

    public String hookConfigurationId() {
      return hookConfigurationId;
    }

    public Instant executionTimestamp() {
      return executionTimestamp;
    }

    public String tenantId() {
      return tenantId;
    }
  }

  /** Execution result containing status and detailed information about the execution outcome. */
  public static class ExecutionResult {
    private final SecurityEventHookStatus status;
    private final Map<String, Object> executionDetails;
    private final long executionDurationMs;
    private final ErrorDetails errorDetails;

    private ExecutionResult(
        SecurityEventHookStatus status,
        Map<String, Object> executionDetails,
        long executionDurationMs,
        ErrorDetails errorDetails) {
      this.status = status;
      this.executionDetails = executionDetails;
      this.executionDurationMs = executionDurationMs;
      this.errorDetails = errorDetails;
    }

    public static ExecutionResult success(
        Map<String, Object> executionDetails, long executionDurationMs) {
      return new ExecutionResult(
          SecurityEventHookStatus.SUCCESS, executionDetails, executionDurationMs, null);
    }

    public static ExecutionResult failure(
        Map<String, Object> executionDetails,
        long executionDurationMs,
        String errorType,
        String errorMessage) {
      ErrorDetails errorDetails = new ErrorDetails(errorType, errorMessage);
      return new ExecutionResult(
          SecurityEventHookStatus.FAILURE, executionDetails, executionDurationMs, errorDetails);
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("status", status.name());
      if (executionDetails != null) {
        map.put("execution_details", executionDetails);
      }
      map.put("execution_duration_ms", executionDurationMs);
      if (errorDetails != null) {
        map.put("error_details", errorDetails.toMap());
      }
      return map;
    }

    public SecurityEventHookStatus status() {
      return status;
    }

    public Map<String, Object> executionDetails() {
      return executionDetails;
    }

    public long executionDurationMs() {
      return executionDurationMs;
    }

    public ErrorDetails errorDetails() {
      return errorDetails;
    }
  }

  /** Error details for failed hook executions. */
  public static class ErrorDetails {
    private final String errorType;
    private final String errorMessage;

    public ErrorDetails(String errorType, String errorMessage) {
      this.errorType = errorType;
      this.errorMessage = errorMessage;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("error_type", errorType);
      map.put("error_message", errorMessage);
      return map;
    }

    public String errorType() {
      return errorType;
    }

    public String errorMessage() {
      return errorMessage;
    }
  }
}
