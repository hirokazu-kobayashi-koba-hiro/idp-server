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
import java.util.*;
import java.util.stream.Collectors;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.json.JsonReadable;

public class AuthenticationInteractionResults implements JsonReadable {

  Map<String, AuthenticationInteractionResult> values;

  public AuthenticationInteractionResults() {
    this.values = new HashMap<>();
  }

  public AuthenticationInteractionResults(Map<String, AuthenticationInteractionResult> values) {
    this.values = values;
  }

  /**
   * Creates AuthenticationInteractionResults from a Map structure. Used for reconstructing from
   * OPSession storage.
   */
  public static AuthenticationInteractionResults fromMap(Map<String, Map<String, Object>> mapData) {
    Map<String, AuthenticationInteractionResult> results = new HashMap<>();
    for (Map.Entry<String, Map<String, Object>> entry : mapData.entrySet()) {
      results.put(entry.getKey(), toResult(entry.getValue()));
    }
    return new AuthenticationInteractionResults(results);
  }

  /**
   * Rebuilds one result, including the per-interaction breakdown when present (#1771).
   *
   * <p>Rows written before the breakdown existed simply have no {@code interactions} key and come
   * back with an empty map.
   */
  private static AuthenticationInteractionResult toResult(Map<String, Object> data) {
    return new AuthenticationInteractionResult(
        (String) data.get("operation_type"),
        (String) data.get("method"),
        getIntValue(data.get("call_count")),
        getIntValue(data.get("success_count")),
        getIntValue(data.get("failure_count")),
        parseInteractionTime(data.get("interaction_time")),
        toNestedResults(data.get("interactions")));
  }

  private static Map<String, AuthenticationInteractionResult> toNestedResults(Object value) {
    Map<String, AuthenticationInteractionResult> nested = new HashMap<>();
    if (!(value instanceof Map<?, ?> raw)) {
      return nested;
    }
    raw.forEach(
        (name, data) -> {
          if (name instanceof String key && data instanceof Map<?, ?> dataMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) dataMap;
            nested.put(key, toResult(typed));
          }
        });
    return nested;
  }

  private static int getIntValue(Object value) {
    if (value == null) return 0;
    if (value instanceof Integer) return (Integer) value;
    if (value instanceof Long) return ((Long) value).intValue();
    if (value instanceof Number) return ((Number) value).intValue();
    return 0;
  }

  private static LocalDateTime parseInteractionTime(Object value) {
    if (value == null) return null;
    if (value instanceof LocalDateTime) return (LocalDateTime) value;
    if (value instanceof String) {
      try {
        return LocalDateTime.parse((String) value);
      } catch (Exception e) {
        return null;
      }
    }
    return null;
  }

  public boolean containsSuccessful(String type) {
    if (!values.containsKey(type)) {
      return false;
    }
    AuthenticationInteractionResult result = values.get(type);
    return result.successCount() > 0;
  }

  public boolean contains(String type) {
    return values.containsKey(type);
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }

  public Map<String, AuthenticationInteractionResult> toMap() {
    return values;
  }

  public Map<String, Object> toMapAsObject() {
    return values.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toMap()));
  }

  /** Returns interaction results as Map for OPSession storage. */
  public Map<String, Map<String, Object>> toStorageMap() {
    return values.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toMap()));
  }

  public AuthenticationInteractionResult get(String interactionType) {
    return values.get(interactionType);
  }

  /**
   * @return these results with {@code interactionRequestResult} counted in, under its type and,
   *     when it names one, its interaction (#1771)
   */
  public AuthenticationInteractionResults updatedWith(
      AuthenticationInteractionRequestResult interactionRequestResult) {
    Map<String, AuthenticationInteractionResult> resultMap = new HashMap<>(values);

    if (contains(interactionRequestResult.interactionTypeName())) {

      AuthenticationInteractionResult foundResult =
          get(interactionRequestResult.interactionTypeName());
      AuthenticationInteractionResult updatedInteraction =
          foundResult.updateWith(interactionRequestResult);
      resultMap.remove(interactionRequestResult.interactionTypeName());
      resultMap.put(interactionRequestResult.interactionTypeName(), updatedInteraction);

    } else {

      // #1771: a named interaction gets its own entry under the type from the very first call, so
      // the breakdown is not missing for whichever interaction happened to run first.
      String operationType = interactionRequestResult.operationType().name();
      String method = interactionRequestResult.method();
      int successCount = interactionRequestResult.isSuccess() ? 1 : 0;
      int failureCount = interactionRequestResult.isSuccess() ? 0 : 1;
      LocalDateTime interactionTime = SystemDateTime.now();
      Map<String, AuthenticationInteractionResult> interactions = new HashMap<>();
      if (interactionRequestResult.hasInteractionName()) {
        interactions.put(
            interactionRequestResult.interactionName(),
            AuthenticationInteractionResult.initialResultFor(interactionRequestResult));
      }
      AuthenticationInteractionResult result =
          new AuthenticationInteractionResult(
              operationType, method, 1, successCount, failureCount, interactionTime, interactions);
      resultMap.put(interactionRequestResult.interactionTypeName(), result);
    }

    return new AuthenticationInteractionResults(resultMap);
  }

  /**
   * Issue #1907: these results without the verifications ({@link OperationType#VERIFICATION}). A
   * verification checks one authorization request — what it asked for, the account as it was then —
   * so a session reused for another request must not carry it over.
   */
  public AuthenticationInteractionResults withoutVerifications() {
    Map<String, AuthenticationInteractionResult> kept = new HashMap<>();
    values.forEach(
        (type, result) -> {
          if (!result.operationType().isVerification()) {
            kept.put(type, result);
          }
        });
    return new AuthenticationInteractionResults(kept);
  }

  public boolean containsAnySuccess() {
    for (AuthenticationInteractionResult result : values.values()) {
      if (result.isAuthentication() && result.successCount() > 0) {
        return true;
      }
    }
    return false;
  }

  public boolean containsDenyInteraction() {
    for (Map.Entry<String, AuthenticationInteractionResult> result : values.entrySet()) {
      AuthenticationInteractionResult interactionResult = result.getValue();
      if (interactionResult.isDeny() && interactionResult.successCount() > 0) {
        return true;
      }
    }
    return false;
  }

  public List<String> authenticationMethods() {
    List<String> methods = new ArrayList<>();
    for (Map.Entry<String, AuthenticationInteractionResult> result : values.entrySet()) {
      AuthenticationInteractionResult interactionResult = result.getValue();
      if (interactionResult.isAuthentication() && interactionResult.successCount() > 0) {
        methods.add(interactionResult.method());
      }
    }

    return methods;
  }

  public LocalDateTime authenticationTime() {
    return values.entrySet().stream()
        .map(Map.Entry::getValue)
        .filter(result -> result.isAuthentication() && result.successCount() > 0)
        .map(AuthenticationInteractionResult::interactionTime)
        .max(Comparator.naturalOrder())
        .orElse(null);
  }
}
