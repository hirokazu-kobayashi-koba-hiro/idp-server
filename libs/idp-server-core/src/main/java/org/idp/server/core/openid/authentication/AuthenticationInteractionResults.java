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

  public AuthenticationInteractionResult get(String interactionType) {
    return values.get(interactionType);
  }

  public boolean containsAnySuccess() {
    for (AuthenticationInteractionResult result : values.values()) {
      if (result.successCount() > 0) {
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
