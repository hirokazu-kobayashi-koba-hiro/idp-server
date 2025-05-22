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


package org.idp.server.basic.type.oauth;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ResponseType
 *
 * <p>All but the code Response Type value, which is defined by OAuth 2.0 [RFC6749], are defined in
 * the OAuth 2.0 Multiple Response Type Encoding Practices [OAuth.Responses] specification. NOTE:
 * While OAuth 2.0 also defines the token Response Type value for the Implicit Flow, OpenID Connect
 * does not use this Response Type, since no ID Token would be returned.
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#Authentication">3.
 *     Authentication</a>
 */
public enum ResponseType {
  code(Set.of("code"), "code"),
  token(Set.of("token"), "token"),
  id_token(Set.of("id_token"), "id_token"),
  code_token(Set.of("code", "token"), "code token"),
  code_token_id_token(Set.of("code", "token", "id_token"), "code token id_token"),
  code_id_token(Set.of("code", "id_token"), "code id_token"),
  token_id_token(Set.of("token", "id_token"), "token id_token"),
  none(Set.of("none"), "none"),
  vp_token(Set.of("vp_token"), "vp_token"),
  vp_token_id_token(Set.of("vp_token", "id_token"), "vp_token id_token"),
  undefined(Set.of(), ""),
  unknown(Set.of(), "");

  Set<String> values;
  String value;

  ResponseType(Set<String> values, String value) {
    this.values = values;
    this.value = value;
  }

  public static ResponseType of(String input) {
    if (Objects.isNull(input) || input.isEmpty()) {
      return undefined;
    }
    Set<String> inputValues = Arrays.stream(input.split(" ")).collect(Collectors.toSet());
    for (ResponseType responseType : ResponseType.values()) {
      if (responseType.values.size() == inputValues.size()
          && responseType.values.containsAll(inputValues)) {
        return responseType;
      }
    }
    return unknown;
  }

  public boolean isAuthorizationCodeFlow() {
    return this == code;
  }

  public boolean isOAuthImplicitFlow() {
    return this == token;
  }

  public boolean isOidcImplicitFlow() {
    return this == id_token || this == token_id_token;
  }

  public boolean isOidcIdTokenOnlyImplicitFlow() {
    return this == id_token;
  }

  public boolean isHybridFlow() {
    return this == code_token || this == code_id_token || this == code_token_id_token;
  }

  public boolean isOidcHybridFlow() {
    return this == code_id_token || this == code_token_id_token;
  }

  public boolean isUndefined() {
    return this == undefined;
  }

  public boolean isUnknown() {
    return this == unknown;
  }

  public boolean isCodeIdToken() {
    return this == code_id_token;
  }

  public String value() {
    return value;
  }

  public boolean isCode() {
    return this == code;
  }
}
