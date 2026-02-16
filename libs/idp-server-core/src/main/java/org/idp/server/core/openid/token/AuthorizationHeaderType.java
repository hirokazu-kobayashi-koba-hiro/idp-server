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

package org.idp.server.core.openid.token;

import java.util.Objects;

public enum AuthorizationHeaderType {
  Basic("Basic "),
  Bearer("Bearer "),
  DPoP("DPoP "),
  Unknown(""),
  Undefined("");

  String value;

  AuthorizationHeaderType(String value) {
    this.value = value;
  }

  /**
   * Parses the Authorization header value to determine its type.
   *
   * <p>Per RFC 9110 Section 11.1, the authentication scheme name is case-insensitive. This method
   * performs a case-insensitive prefix match so that "Bearer", "bearer", and "BEARER" are all
   * recognized as the Bearer scheme.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9110#section-11.1">RFC 9110 Section 11.1</a>
   */
  public static AuthorizationHeaderType of(String authorizationHeader) {
    if (Objects.isNull(authorizationHeader) || authorizationHeader.isEmpty()) {
      return Unknown;
    }
    String lowerCaseHeader = authorizationHeader.toLowerCase();
    for (AuthorizationHeaderType type : AuthorizationHeaderType.values()) {
      if (!type.value.isEmpty() && lowerCaseHeader.startsWith(type.value.toLowerCase())) {
        return type;
      }
    }
    return Undefined;
  }

  public boolean isBasic() {
    return this == Basic;
  }

  public boolean isBearer() {
    return this == Bearer;
  }

  public boolean isDPoP() {
    return this == DPoP;
  }

  public String value() {
    return value;
  }

  public int length() {
    return value.length();
  }
}
