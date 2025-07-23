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

public enum HttpRequestAuthType {
  OAUTH2("oauth2"),
  HMAC_SHA256("hmac_sha256"),
  NONE("none"),
  UNKNOWN("unknown");

  String type;

  HttpRequestAuthType(String type) {
    this.type = type;
  }

  public String type() {
    return type;
  }

  public static HttpRequestAuthType of(String type) {
    if (type == null) {
      return NONE;
    }

    for (HttpRequestAuthType authType : HttpRequestAuthType.values()) {
      if (authType.type.equals(type)) {
        return authType;
      }
    }

    return UNKNOWN;
  }

  public boolean isOauth2() {
    return this == HttpRequestAuthType.OAUTH2;
  }

  public boolean isHmacSha256() {
    return this == HttpRequestAuthType.HMAC_SHA256;
  }
}
