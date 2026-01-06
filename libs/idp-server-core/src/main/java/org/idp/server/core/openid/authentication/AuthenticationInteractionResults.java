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
      String key = entry.getKey();
      Map<String, Object> data = entry.getValue();
      AuthenticationInteractionResult result =
          new AuthenticationInteractionResult(
              (String) data.get("operation_type"),
              (String) data.get("method"),
              getIntValue(data.get("call_count")),
              getIntValue(data.get("success_count")),
              getIntValue(data.get("failure_count")),
              parseInteractionTime(data.get("interaction_time")));
      results.put(key, result);
    }
    return new AuthenticationInteractionResults(results);
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
