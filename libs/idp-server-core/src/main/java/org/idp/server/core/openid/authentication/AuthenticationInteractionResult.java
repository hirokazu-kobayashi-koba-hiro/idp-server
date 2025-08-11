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

package org.idp.server.core.openid.authentication;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.federation.FederationInteractionResult;
import org.idp.server.platform.date.SystemDateTime;

public class AuthenticationInteractionResult {

  String operationType;
  String method;
  int callCount;
  int successCount;
  int failureCount;
  LocalDateTime interactionTime;

  public AuthenticationInteractionResult() {}

  public AuthenticationInteractionResult(
      String operationType,
      String method,
      int callCount,
      int successCount,
      int failureCount,
      LocalDateTime interactionTime) {
    this.operationType = operationType;
    this.method = method;
    this.callCount = callCount;
    this.successCount = successCount;
    this.failureCount = failureCount;
    this.interactionTime = interactionTime;
  }

  public int callCount() {
    return callCount;
  }

  public int successCount() {
    return successCount;
  }

  public int failureCount() {
    return failureCount;
  }

  public AuthenticationInteractionResult updateWith(
      AuthenticationInteractionRequestResult interactionRequestResult) {
    int increaseSuccessCount = interactionRequestResult.isSuccess() ? 1 : 0;
    int increaseFailureCount = interactionRequestResult.isSuccess() ? 0 : 1;

    return new AuthenticationInteractionResult(
        operationType,
        method,
        callCount + 1,
        successCount + increaseSuccessCount,
        failureCount + increaseFailureCount,
        SystemDateTime.now());
  }

  public AuthenticationInteractionResult updateWith(
      FederationInteractionResult interactionRequestResult) {
    int increaseSuccessCount = interactionRequestResult.isSuccess() ? 1 : 0;
    int increaseFailureCount = interactionRequestResult.isSuccess() ? 0 : 1;

    return new AuthenticationInteractionResult(
        operationType,
        method,
        callCount + 1,
        successCount + increaseSuccessCount,
        failureCount + increaseFailureCount,
        SystemDateTime.now());
  }

  public OperationType operationType() {
    return OperationType.of(operationType);
  }

  public boolean isAuthentication() {
    return operationType().isAuthentication();
  }

  public boolean isDeny() {
    return operationType().isDeny();
  }

  public String method() {
    return method;
  }

  public LocalDateTime interactionTime() {
    return interactionTime;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("operation_type", operationType);
    map.put("method", method);
    map.put("call_count", callCount);
    map.put("success_count", successCount);
    map.put("failure_count", failureCount);
    map.put("interaction_time", interactionTime.toString());
    return map;
  }
}
