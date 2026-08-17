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

  /**
   * Per-interaction breakdown, for types that cover more than one interaction (#1771).
   *
   * <p>Empty for every interactor that is one authentication factor. {@code
   * external-api-authentication} fills it, because its counts would otherwise be a single total
   * across interactions that call different external APIs — a policy could then be satisfied by
   * calling one of them repeatedly.
   *
   * <p>The totals above stay the sum, so conditions written before this existed keep working.
   */
  Map<String, AuthenticationInteractionResult> interactions = new HashMap<>();

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

  public AuthenticationInteractionResult(
      String operationType,
      String method,
      int callCount,
      int successCount,
      int failureCount,
      LocalDateTime interactionTime,
      Map<String, AuthenticationInteractionResult> interactions) {
    this(operationType, method, callCount, successCount, failureCount, interactionTime);
    this.interactions = interactions;
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
    return incrementTotals(interactionRequestResult)
        .withInteractions(updatedInteractions(interactionRequestResult));
  }

  /**
   * Advances this result's own counters, leaving the breakdown alone.
   *
   * <p>Separate from {@link #updateWith} because a breakdown entry must be advanced with this and
   * not with {@code updateWith}: the latter also rebuilds the breakdown, so a nested entry would
   * grow a breakdown of its own — one level deeper on every call to the same interaction.
   */
  private AuthenticationInteractionResult incrementTotals(
      AuthenticationInteractionRequestResult interactionRequestResult) {
    return new AuthenticationInteractionResult(
        operationType,
        method,
        callCount + 1,
        successCount + (interactionRequestResult.isSuccess() ? 1 : 0),
        failureCount + (interactionRequestResult.isSuccess() ? 0 : 1),
        SystemDateTime.now(),
        interactions);
  }

  private AuthenticationInteractionResult withInteractions(
      Map<String, AuthenticationInteractionResult> value) {
    return new AuthenticationInteractionResult(
        operationType, method, callCount, successCount, failureCount, interactionTime, value);
  }

  /**
   * Advances the named interaction's own counters alongside the totals (#1771).
   *
   * <p>A result without an interaction name leaves the breakdown untouched, so a type that never
   * names one keeps an empty map and serializes exactly as before.
   */
  private Map<String, AuthenticationInteractionResult> updatedInteractions(
      AuthenticationInteractionRequestResult interactionRequestResult) {
    if (!interactionRequestResult.hasInteractionName()) {
      return interactions;
    }

    String name = interactionRequestResult.interactionName();
    Map<String, AuthenticationInteractionResult> updated = new HashMap<>(interactions);
    AuthenticationInteractionResult existing = updated.get(name);

    if (existing != null) {
      // incrementTotals, not updateWith: the breakdown is one level deep by design, and updateWith
      // would give this entry a breakdown of its own.
      updated.put(name, existing.incrementTotals(interactionRequestResult));
      return updated;
    }

    updated.put(name, initialResultFor(interactionRequestResult));
    return updated;
  }

  /** First result for a named interaction: one call, counted as success or failure. */
  static AuthenticationInteractionResult initialResultFor(
      AuthenticationInteractionRequestResult interactionRequestResult) {
    return new AuthenticationInteractionResult(
        interactionRequestResult.operationType().name(),
        interactionRequestResult.method(),
        1,
        interactionRequestResult.isSuccess() ? 1 : 0,
        interactionRequestResult.isSuccess() ? 0 : 1,
        SystemDateTime.now());
  }

  public Map<String, AuthenticationInteractionResult> interactions() {
    return interactions;
  }

  public boolean hasInteractions() {
    return interactions != null && !interactions.isEmpty();
  }

  /**
   * Federation has no per-interaction breakdown — it records under its own type key and never names
   * an interaction — but the existing breakdown is carried so the two {@code updateWith} overloads
   * do not differ in whether they preserve it.
   */
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
        SystemDateTime.now(),
        interactions);
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
    if (hasInteractions()) {
      Map<String, Object> interactionsMap = new HashMap<>();
      interactions.forEach((name, result) -> interactionsMap.put(name, result.toMap()));
      map.put("interactions", interactionsMap);
    }
    return map;
  }
}
