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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.http.HttpRequestExecutionConfig;
import org.idp.server.platform.http.HttpRequestMockConfig;
import org.idp.server.platform.json.JsonReadable;

public class SecurityEventExecutionConfig implements JsonReadable {
  String function;
  HttpRequestExecutionConfig httpRequest = new HttpRequestExecutionConfig();
  List<HttpRequestExecutionConfig> httpRequests = new ArrayList<>();
  HttpRequestMockConfig mock = new HttpRequestMockConfig();
  Map<String, Object> details = new HashMap<>();

  public SecurityEventExecutionConfig() {}

  public String function() {
    return function;
  }

  public HttpRequestExecutionConfig httpRequest() {
    if (httpRequest == null) {
      return new HttpRequestExecutionConfig();
    }
    return httpRequest;
  }

  public boolean hasHttpRequest() {
    return httpRequest != null && httpRequest.exists();
  }

  public List<HttpRequestExecutionConfig> httpRequests() {
    if (httpRequests == null) {
      return new ArrayList<>();
    }
    return httpRequests;
  }

  public List<Map<String, Object>> httpRequestsAsMap() {
    if (httpRequests == null) {
      return new ArrayList<>();
    }
    return httpRequests.stream().map(HttpRequestExecutionConfig::toMap).toList();
  }

  public boolean hasHttpRequests() {
    return httpRequests != null && !httpRequests.isEmpty();
  }

  public HttpRequestMockConfig mock() {
    if (mock == null) {
      return new HttpRequestMockConfig();
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
    return function != null && !function.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("function", function);
    if (hasHttpRequest()) map.put("http_request", httpRequest.toMap());
    if (hasHttpRequests()) map.put("http_requests", httpRequestsAsMap());
    if (hasMock()) map.put("mock", mock.toMap());
    if (hasDetails()) map.put("details", details);
    return map;
  }
}
