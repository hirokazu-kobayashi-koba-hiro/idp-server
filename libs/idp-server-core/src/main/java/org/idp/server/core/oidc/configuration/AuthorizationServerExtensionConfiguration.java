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

package org.idp.server.core.oidc.configuration;

import java.util.*;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.core.oidc.configuration.authentication.AuthenticationPolicy;

public class AuthorizationServerExtensionConfiguration implements JsonReadable {

  List<String> fapiBaselineScopes = new ArrayList<>();
  List<String> fapiAdvanceScopes = new ArrayList<>();

  /** opaque: identifier type JWT: consisting type */
  String accessTokenType = "opaque";

  int authorizationCodeValidDuration = 600;
  String tokenSignedKeyId = "";
  String idTokenSignedKeyId = "";
  long accessTokenDuration = 1800;
  long refreshTokenDuration = 3600;
  long idTokenDuration = 3600;
  boolean idTokenStrictMode = false;
  long defaultMaxAge = 86400;
  long authorizationResponseDuration = 60;
  int backchannelAuthRequestExpiresIn = 300;
  int backchannelAuthPollingInterval = 5;
  int oauthAuthorizationRequestExpiresIn = 1800;
  List<AuthenticationPolicy> authenticationPolicies = new ArrayList<>();

  public AuthorizationServerExtensionConfiguration() {}

  public boolean hasFapiBaselineScope(Set<String> scopes) {
    return scopes.stream().anyMatch(scope -> fapiBaselineScopes.contains(scope));
  }

  public boolean hasFapiAdvanceScope(Set<String> scopes) {
    return scopes.stream().anyMatch(scope -> fapiAdvanceScopes.contains(scope));
  }

  public String accessTokenType() {
    return accessTokenType;
  }

  public boolean isIdentifierAccessTokenType() {
    return accessTokenType.equals("opaque");
  }

  public int authorizationCodeValidDuration() {
    return authorizationCodeValidDuration;
  }

  public String tokenSignedKeyId() {
    return tokenSignedKeyId;
  }

  public String idTokenSignedKeyId() {
    return idTokenSignedKeyId;
  }

  public long accessTokenDuration() {
    return accessTokenDuration;
  }

  public long refreshTokenDuration() {
    return refreshTokenDuration;
  }

  public long idTokenDuration() {
    return idTokenDuration;
  }

  public boolean isIdTokenStrictMode() {
    return idTokenStrictMode;
  }

  public long defaultMaxAge() {
    return defaultMaxAge;
  }

  public long authorizationResponseDuration() {
    return authorizationResponseDuration;
  }

  public int backchannelAuthRequestExpiresIn() {
    return backchannelAuthRequestExpiresIn;
  }

  public int backchannelAuthPollingInterval() {
    return backchannelAuthPollingInterval;
  }

  public int oauthAuthorizationRequestExpiresIn() {
    return oauthAuthorizationRequestExpiresIn;
  }

  public List<String> fapiBaselineScopes() {
    return fapiBaselineScopes;
  }

  public List<String> fapiAdvanceScopes() {
    return fapiAdvanceScopes;
  }

  public boolean idTokenStrictMode() {
    return idTokenStrictMode;
  }

  public List<AuthenticationPolicy> authenticationPolicies() {
    return authenticationPolicies;
  }
}
