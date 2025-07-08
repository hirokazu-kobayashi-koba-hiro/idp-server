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

public class IdentityVerificationProcessResultCondition implements JsonReadable {
  String processName;
  String type;
  int count;

  public IdentityVerificationProcessResultCondition() {}

  public IdentityVerificationProcessResultCondition(String processName, String type, int count) {
    this.processName = processName;
    this.type = type;
    this.count = count;
  }

  public String processName() {
    return processName;
  }

  public String type() {
    return type;
  }

  public int count() {
    return count;
  }

  public boolean isSatisfiedCount(int count) {
    return count >= this.count;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("process_name", processName);
    map.put("type", type);
    map.put("count", count);
    return map;
  }

  public boolean isSuccessType() {
    return "success".equals(type);
  }

  public boolean isFailureType() {
    return "failure".equals(type);
  }
}
