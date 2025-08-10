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
import org.idp.server.platform.json.JsonReadable;

public class HttpRequestMockConfig implements JsonReadable {
  int statusCode;
  List<Map<String, String>> responseHeaders;
  Map<String, Object> responseBody;

  public HttpRequestMockConfig() {}

  public int statusCode() {
    return statusCode;
  }

  public List<Map<String, String>> responseHeaders() {
    return responseHeaders;
  }

  public Map<String, Object> responseBody() {
    return responseBody;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("status_code", statusCode);
    map.put("response_headers", responseHeaders);
    map.put("response_body", responseBody);
    return map;
  }
}
