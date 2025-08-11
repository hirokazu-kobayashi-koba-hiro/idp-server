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

package org.idp.server.core.openid.oauth.configuration.authentication;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

public class AuthenticationResultCondition implements JsonReadable {
  String type;
  int successCount;
  int failureCount;

  public AuthenticationResultCondition() {}

  public AuthenticationResultCondition(String type, int successCount, int failureCount) {
    this.type = type;
    this.successCount = successCount;
    this.failureCount = failureCount;
  }

  public String type() {
    return type;
  }

  public int successCount() {
    return successCount;
  }

  public int failureCount() {
    return failureCount;
  }

  public boolean isSatisfiedSuccess(int successCount) {
    return successCount >= this.successCount;
  }

  public boolean isSatisfiedFailure(int failureCount) {
    return failureCount >= this.failureCount;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("type", type);
    map.put("success_count", successCount);
    map.put("failure_count", failureCount);
    return map;
  }
}
