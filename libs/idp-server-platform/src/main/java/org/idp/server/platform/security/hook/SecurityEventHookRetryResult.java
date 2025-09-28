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

import java.util.Map;

/** Result of a security event hook retry operation. */
public class SecurityEventHookRetryResult {

  private final SecurityEventHookResultIdentifier originalResultIdentifier;
  private final SecurityEventHookRetryStatus status;
  private final SecurityEventHookResultIdentifier newResultIdentifier;
  private final SecurityEventHookResult newResult;
  private final String errorMessage;
  private final Map<String, Object> retryDetails;

  private SecurityEventHookRetryResult(
      SecurityEventHookResultIdentifier originalResultIdentifier,
      SecurityEventHookRetryStatus status,
      SecurityEventHookResultIdentifier newResultIdentifier,
      SecurityEventHookResult newResult,
      String errorMessage,
      Map<String, Object> retryDetails) {
    this.originalResultIdentifier = originalResultIdentifier;
    this.status = status;
    this.newResultIdentifier = newResultIdentifier;
    this.newResult = newResult;
    this.errorMessage = errorMessage;
    this.retryDetails = retryDetails != null ? retryDetails : Map.of();
  }

  public static SecurityEventHookRetryResult success(
      SecurityEventHookResultIdentifier originalResultIdentifier,
      SecurityEventHookResult newResult) {
    return new SecurityEventHookRetryResult(
        originalResultIdentifier,
        SecurityEventHookRetryStatus.SUCCESS,
        newResult != null ? newResult.identifier() : null,
        newResult,
        null,
        Map.of());
  }

  public static SecurityEventHookRetryResult success(
      SecurityEventHookResultIdentifier originalResultIdentifier,
      SecurityEventHookResultIdentifier newResultIdentifier,
      Map<String, Object> retryDetails) {
    return new SecurityEventHookRetryResult(
        originalResultIdentifier,
        SecurityEventHookRetryStatus.SUCCESS,
        newResultIdentifier,
        null,
        null,
        retryDetails);
  }

  public static SecurityEventHookRetryResult failure(
      SecurityEventHookResultIdentifier originalResultIdentifier, String errorMessage) {
    return new SecurityEventHookRetryResult(
        originalResultIdentifier,
        SecurityEventHookRetryStatus.FAILURE,
        null,
        null,
        errorMessage,
        Map.of());
  }

  public static SecurityEventHookRetryResult alreadySuccessful(
      SecurityEventHookResultIdentifier originalResultIdentifier) {
    return new SecurityEventHookRetryResult(
        originalResultIdentifier,
        SecurityEventHookRetryStatus.ALREADY_SUCCESSFUL,
        null,
        null,
        null,
        Map.of());
  }

  public static SecurityEventHookRetryResult notFound(
      SecurityEventHookResultIdentifier originalResultIdentifier) {
    return new SecurityEventHookRetryResult(
        originalResultIdentifier,
        SecurityEventHookRetryStatus.NOT_FOUND,
        null,
        null,
        "Hook result not found",
        Map.of());
  }

  public SecurityEventHookResultIdentifier originalResultIdentifier() {
    return originalResultIdentifier;
  }

  public SecurityEventHookRetryStatus status() {
    return status;
  }

  public SecurityEventHookResultIdentifier newResultIdentifier() {
    return newResultIdentifier;
  }

  public SecurityEventHookResult newResult() {
    return newResult;
  }

  public String errorMessage() {
    return errorMessage;
  }

  public Map<String, Object> retryDetails() {
    return retryDetails;
  }

  public boolean isSuccess() {
    return status == SecurityEventHookRetryStatus.SUCCESS;
  }

  public boolean isFailure() {
    return status == SecurityEventHookRetryStatus.FAILURE;
  }

  public boolean isAlreadySuccessful() {
    return status == SecurityEventHookRetryStatus.ALREADY_SUCCESSFUL;
  }

  public boolean isNotFound() {
    return status == SecurityEventHookRetryStatus.NOT_FOUND;
  }
}
