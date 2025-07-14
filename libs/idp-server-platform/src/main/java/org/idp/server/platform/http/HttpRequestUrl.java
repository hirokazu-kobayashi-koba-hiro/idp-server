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

import java.util.Map;

public class HttpRequestUrl {
  String value;

  public HttpRequestUrl() {}

  public HttpRequestUrl(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public String withQueryParams(HttpQueryParams queryParams) {
    String paramString = queryParams.params();
    if (paramString == null || paramString.isEmpty()) {
      return value;
    }

    StringBuilder stringBuilder = new StringBuilder(value);
    if (!value.contains("?")) {
      stringBuilder.append("?");
    } else if (!value.endsWith("&") && !value.endsWith("?")) {
      stringBuilder.append("&");
    }

    stringBuilder.append(paramString);
    return stringBuilder.toString();
  }

  public HttpRequestUrl interpolate(Map<String, String> pathParams) {
    String interpolated = value;
    for (Map.Entry<String, String> entry : pathParams.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      interpolated = interpolated.replace("{{" + key + "}}", value);
    }
    return new HttpRequestUrl(interpolated);
  }
}
