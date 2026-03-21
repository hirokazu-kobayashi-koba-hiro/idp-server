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

/**
 * SubjectTokenType
 *
 * <p>RFC 8693 Section 3 - Token Type Identifiers for subject_token_type.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-3">RFC 8693 Section 3</a>
 */
public enum SubjectTokenType {
  access_token("urn:ietf:params:oauth:token-type:access_token"),
  id_token("urn:ietf:params:oauth:token-type:id_token"),
  jwt("urn:ietf:params:oauth:token-type:jwt"),
  undefined("");

  String value;

  SubjectTokenType(String value) {
    this.value = value;
  }

  public static SubjectTokenType of(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      return undefined;
    }
    for (SubjectTokenType type : SubjectTokenType.values()) {
      if (type.value.equals(value)) {
        return type;
      }
    }
    return undefined;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return this != undefined;
  }

  public boolean isAlwaysJwt() {
    return this == id_token || this == jwt;
  }

  public boolean isAccessToken() {
    return this == access_token;
  }
}
