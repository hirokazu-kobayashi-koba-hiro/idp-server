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

package org.idp.server.core.openid.authentication.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.http.HttpRequestExecutionConfig;
import org.idp.server.platform.http.HttpRequestMockConfig;
import org.idp.server.platform.json.JsonReadable;

public class AuthenticationExecutionConfig implements JsonReadable {
  String function;
  AuthenticationPreviousInteractionResolveConfig previousInteraction =
      new AuthenticationPreviousInteractionResolveConfig();
  HttpRequestExecutionConfig httpRequest = new HttpRequestExecutionConfig();
  List<HttpRequestExecutionConfig> httpRequests = new ArrayList<>();
  HttpRequestMockConfig mock = new HttpRequestMockConfig();
  Map<String, Object> details = new HashMap<>();
  AuthenticationExecutionStoreConfig httpRequestStore = new AuthenticationExecutionStoreConfig();
  AuthenticationExecutionStoreConfig httpRequestsStore = new AuthenticationExecutionStoreConfig();

  public AuthenticationExecutionConfig() {}

  public String function() {
    return function;
  }

  public AuthenticationPreviousInteractionResolveConfig previousInteraction() {
    if (previousInteraction == null) {
      return new AuthenticationPreviousInteractionResolveConfig();
    }
    return previousInteraction;
  }

  public boolean hasPreviousInteraction() {
    return previousInteraction != null && previousInteraction.exists();
  }

  public HttpRequestExecutionConfig httpRequest() {
    if (httpRequest == null) {
      return new HttpRequestExecutionConfig();
    }
    return httpRequest;
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

  public HttpRequestMockConfig mock() {
    if (mock == null) {
      return new HttpRequestMockConfig();
    }
    return mock;
  }

  public Map<String, Object> details() {
    if (details == null) {
      return new HashMap<>();
    }
    return details;
  }

  public AuthenticationExecutionStoreConfig httpRequestStore() {
    if (httpRequestStore == null) {
      return new AuthenticationExecutionStoreConfig();
    }
    return httpRequestStore;
  }

  public boolean hasHttpRequestStore() {
    return httpRequestStore != null && httpRequestStore.exists();
  }

  public AuthenticationExecutionStoreConfig httpRequestsStore() {
    if (httpRequestsStore == null) {
      return new AuthenticationExecutionStoreConfig();
    }
    return httpRequestsStore;
  }

  public boolean hasHttpRequestsStore() {
    return httpRequestsStore != null && httpRequestsStore.exists();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("function", function);
    map.put("http_request", httpRequest().toMap());
    map.put("http_requests", httpRequestsAsMap());
    map.put("mock", mock().toMap());
    map.put("details", details());
    map.put("http_request_store", httpRequestStore());
    map.put("http_requests_store", httpRequestsStore());
    return map;
  }
}
