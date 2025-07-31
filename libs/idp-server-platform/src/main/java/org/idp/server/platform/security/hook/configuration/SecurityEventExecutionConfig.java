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

package org.idp.server.platform.security.hook.configuration;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

public class SecurityEventExecutionConfig implements JsonReadable {
  String type;
  SecurityEventHttpRequestConfig httpRequest = new SecurityEventHttpRequestConfig();
  SecurityEventMockConfig mock = new SecurityEventMockConfig();
  Map<String, Object> details = new HashMap<>();

  public SecurityEventExecutionConfig() {}

  public String type() {
    return type;
  }

  public SecurityEventHttpRequestConfig httpRequest() {
    if (httpRequest == null) {
      return new SecurityEventHttpRequestConfig();
    }
    return httpRequest;
  }

  public boolean hasHttpRequest() {
    return httpRequest != null && httpRequest.exists();
  }

  public SecurityEventMockConfig mock() {
    if (mock == null) {
      return new SecurityEventMockConfig();
    }
    return mock;
  }

  public boolean hasMock() {
    return mock != null;
  }

  public Map<String, Object> details() {
    return details;
  }

  public boolean hasDetails() {
    return details != null && !details.isEmpty();
  }

  public boolean exists() {
    return type != null && !type.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("type", type);
    if (hasHttpRequest()) map.put("httpRequest", httpRequest.toMap());
    if (hasMock()) map.put("mock", mock.toMap());
    if (hasDetails()) map.put("details", details);
    return map;
  }
}
