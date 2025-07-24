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

public class IdentityVerificationConditionConfig implements JsonReadable {

  List<List<IdentityVerificationCondition>> anyOf = new ArrayList<>();

  public boolean hasAnyOf() {
    return anyOf != null && !anyOf.isEmpty();
  }

  public boolean exists() {
    return hasAnyOf();
  }

  public List<List<IdentityVerificationCondition>> anyOf() {
    return anyOf;
  }

  public List<List<Map<String, Object>>> anyOfListAsMap() {
    List<List<Map<String, Object>>> result = new ArrayList<>();
    for (List<IdentityVerificationCondition> list : anyOf) {
      List<Map<String, Object>> mapList =
          new ArrayList<>(list.stream().map(IdentityVerificationCondition::toMap).toList());
      result.add(mapList);
    }
    return result;
  }

  public Map<String, Object> toMap() {
    HashMap<String, Object> map = new HashMap<>();
    if (hasAnyOf()) map.put("any_of", anyOfListAsMap());
    return map;
  }
}
