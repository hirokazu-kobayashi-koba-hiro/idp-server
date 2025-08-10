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

package org.idp.server.platform.http;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.idp.server.platform.json.JsonNodeWrapper;

public class HttpRequestResult {
  int statusCode;
  Map<String, List<String>> headers;
  JsonNodeWrapper body;

  public HttpRequestResult() {}

  public HttpRequestResult(
      int statusCode, Map<String, List<String>> headers, JsonNodeWrapper body) {
    this.statusCode = statusCode;
    this.headers = headers;
    this.body = body;
  }

  public int statusCode() {
    return statusCode;
  }

  public boolean isSuccess() {
    return statusCode < 400;
  }

  public Map<String, List<String>> headers() {
    return headers;
  }

  public Map<String, String> headersAsSingleValueMap() {
    return headers.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getFirst()));
  }

  public JsonNodeWrapper body() {
    return body;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("status_code", statusCode);
    map.put("response_headers", headers);
    map.put("response_body", body.toMap());
    return map;
  }

  public boolean isClientError() {
    return statusCode >= 400 && statusCode < 500;
  }

  public boolean isServerError() {
    return statusCode >= 500;
  }
}
