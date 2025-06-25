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

package org.idp.server.federation.sso.oidc;

import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonNodeWrapper;

public class OidcUserinfoResult {

  int statusCode;
  Map<String, List<String>> headers;
  JsonNodeWrapper body;

  OidcUserinfoResult() {}

  public OidcUserinfoResult(
      int statusCode, Map<String, List<String>> headers, JsonNodeWrapper body) {
    this.statusCode = statusCode;
    this.headers = headers;
    this.body = body;
  }

  public int statusCode() {
    return statusCode;
  }

  public Map<String, List<String>> headers() {
    return headers;
  }

  public Map<String, Object> bodyAsMap() {
    return body.toMap();
  }

  public JsonNodeWrapper body() {
    return body;
  }

  public String bodyToJsonString() {
    return body.toJson();
  }

  public boolean isError() {
    return statusCode >= 400;
  }
}
