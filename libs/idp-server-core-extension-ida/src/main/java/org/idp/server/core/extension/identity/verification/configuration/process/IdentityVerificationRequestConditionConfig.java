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

package org.idp.server.core.extension.identity.verification.configuration.process;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

public class IdentityVerificationRequestConditionConfig implements JsonReadable {
  List<IdentityVerificationRequestResultCondition> anyOf = new ArrayList<>();
  List<IdentityVerificationRequestResultCondition> allOf = new ArrayList<>();

  public IdentityVerificationRequestConditionConfig() {}

  public IdentityVerificationRequestConditionConfig(
      List<IdentityVerificationRequestResultCondition> anyOf,
      List<IdentityVerificationRequestResultCondition> allOf) {
    this.anyOf = anyOf;
    this.allOf = allOf;
  }

  public List<IdentityVerificationRequestResultCondition> anyOf() {
    if (anyOf == null) {
      return new ArrayList<>();
    }
    return anyOf;
  }

  public List<IdentityVerificationRequestResultCondition> allOf() {
    if (allOf == null) {
      return new ArrayList<>();
    }
    return allOf;
  }

  public List<Map<String, Object>> anyOfAsMap() {
    return anyOf.stream().map(IdentityVerificationRequestResultCondition::toMap).toList();
  }

  public List<Map<String, Object>> allOfAsMap() {
    return allOf.stream().map(IdentityVerificationRequestResultCondition::toMap).toList();
  }

  public boolean hasAnyOf() {
    return anyOf != null && !anyOf.isEmpty();
  }

  public boolean hasAllOf() {
    return allOf != null && !allOf.isEmpty();
  }

  public boolean exists() {
    return hasAnyOf() || hasAllOf();
  }

  public Map<String, Object> toMap() {
    HashMap<String, Object> map = new HashMap<>();
    if (hasAnyOf()) map.put("any_of", anyOfAsMap());
    if (hasAllOf()) map.put("all_of", allOfAsMap());
    return map;
  }
}
