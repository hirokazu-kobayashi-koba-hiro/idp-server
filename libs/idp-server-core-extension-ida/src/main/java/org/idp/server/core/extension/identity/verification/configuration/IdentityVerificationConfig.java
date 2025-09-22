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

package org.idp.server.core.extension.identity.verification.configuration;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.mapper.ConditionSpec;

public class IdentityVerificationConfig implements JsonReadable {
  String type;
  Map<String, Object> details;
  ConditionSpec condition;

  public IdentityVerificationConfig() {}

  public IdentityVerificationConfig(String type, Map<String, Object> details) {
    this.type = type;
    this.details = details;
  }

  public IdentityVerificationConfig(
      String type, Map<String, Object> details, ConditionSpec condition) {
    this.type = type;
    this.details = details;
    this.condition = condition;
  }

  public String type() {
    return type;
  }

  public Map<String, Object> details() {
    return details;
  }

  public ConditionSpec condition() {
    return condition;
  }

  public boolean hasCondition() {
    return condition != null;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("type", type);
    map.put("details", details);
    if (hasCondition()) {
      map.put("condition", condition.toMap());
    }
    return map;
  }
}
