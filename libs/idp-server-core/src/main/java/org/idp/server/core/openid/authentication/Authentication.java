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

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import org.idp.server.platform.json.JsonReadable;

public class Authentication implements Serializable, JsonReadable {
  LocalDateTime time;
  List<String> methods = new ArrayList<>();
  String acr = "";
  // Store interaction results for MFA policy evaluation
  HashMap<String, Object> interactionResultsMap = new HashMap<>();
  boolean completed = false;

  public Authentication() {}

  public Authentication setTime(LocalDateTime time) {
    this.time = time;
    return this;
  }

  public Authentication addMethods(List<String> methods) {
    List<String> newValues = new ArrayList<>(this.methods);
    newValues.addAll(methods);
    this.methods = newValues;
    return this;
  }

  public Authentication addAcr(String acr) {
    this.acr = acr;
    return this;
  }

  public LocalDateTime time() {
    return time;
  }

  public boolean hasAuthenticationTime() {
    return Objects.nonNull(time);
  }

  public List<String> methods() {
    return methods;
  }

  public boolean hasMethod() {
    return !methods.isEmpty();
  }

  public String acr() {
    return acr;
  }

  public boolean hasAcrValues() {
    return acr != null && !acr.isEmpty();
  }

  public boolean exists() {

    return hasAuthenticationTime();
  }

  // MFA policy support methods
  public Authentication setInteractionResults(HashMap<String, Object> interactionResultsMap) {
    this.interactionResultsMap =
        interactionResultsMap != null ? interactionResultsMap : new HashMap<>();
    return this;
  }

  public Map<String, Object> interactionResultsMap() {
    return interactionResultsMap;
  }

  public boolean hasInteractionResults() {
    return interactionResultsMap != null && !interactionResultsMap.isEmpty();
  }

  public Authentication didComplete() {
    this.completed = true;
    return this;
  }

  public boolean isCompleted() {
    return completed;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("time", time);
    map.put("methods", methods);
    map.put("acr", acr);
    return map;
  }
}
