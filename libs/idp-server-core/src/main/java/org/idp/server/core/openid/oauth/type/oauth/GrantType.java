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

package org.idp.server.core.openid.oauth.type.oauth;

import java.util.Objects;

public enum GrantType {
  authorization_code("authorization_code"),
  implicit("implicit"),
  password("password"),
  client_credentials("client_credentials"),
  refresh_token("refresh_token"),
  ciba("urn:openid:params:grant-type:ciba"),
  jwt_bearer("urn:ietf:params:oauth:grant-type:jwt-bearer"),
  unknown(""),
  undefined("");

  String value;

  GrantType(String value) {
    this.value = value;
  }

  public static GrantType of(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      return undefined;
    }
    for (GrantType grantType : GrantType.values()) {
      if (grantType.value.equals(value) || grantType.name().equals(value)) {
        return grantType;
      }
    }
    return unknown;
  }

  public String value() {
    return value;
  }

  public boolean isClientCredentials() {
    return this == client_credentials;
  }

  public boolean isJwtBearer() {
    return this == jwt_bearer;
  }
}
