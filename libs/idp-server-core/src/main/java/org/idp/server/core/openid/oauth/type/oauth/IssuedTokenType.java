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

/**
 * IssuedTokenType
 *
 * <p>RFC 8693 Section 2.2.1 - issued_token_type in token exchange response.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-2.2.1">RFC 8693 Section
 *     2.2.1</a>
 */
public class IssuedTokenType {

  public static final String ACCESS_TOKEN = "urn:ietf:params:oauth:token-type:access_token";

  String value;

  public IssuedTokenType() {}

  public IssuedTokenType(String value) {
    this.value = value;
  }

  public static IssuedTokenType accessToken() {
    return new IssuedTokenType(ACCESS_TOKEN);
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return value != null && !value.isEmpty();
  }
}
