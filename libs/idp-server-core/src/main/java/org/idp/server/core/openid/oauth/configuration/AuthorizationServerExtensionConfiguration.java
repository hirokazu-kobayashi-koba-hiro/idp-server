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

package org.idp.server.core.openid.oauth.configuration;

import java.util.*;
import org.idp.server.core.openid.authentication.AuthenticationInteractionType;
import org.idp.server.core.openid.oauth.configuration.authentication.AuthenticationPolicy;
import org.idp.server.platform.json.JsonReadable;

public class AuthorizationServerExtensionConfiguration implements JsonReadable {

  List<String> fapiBaselineScopes = new ArrayList<>();
  List<String> fapiAdvanceScopes = new ArrayList<>();
  List<String> requiredIdentityVerificationScopes = new ArrayList<>();

  /** opaque: identifier type JWT: consisting type */
  String accessTokenType = "opaque";

  int authorizationCodeValidDuration = 600;
  String tokenSignedKeyId = "";
  String idTokenSignedKeyId = "";
  long accessTokenDuration = 1800;
  long refreshTokenDuration = 3600;
  String accessTokenStrategy = "FIXED";
  boolean rotateRefreshToken = true;
  long idTokenDuration = 3600;
  boolean idTokenStrictMode = false;
  long defaultMaxAge = 86400;
  long authorizationResponseDuration = 60;
  int backchannelAuthenticationRequestExpiresIn = 300;
  int backchannelAuthenticationPollingInterval = 5;
  boolean requiredBackchannelAuthUserCode = false;
  String backchannelAuthUserCodeType = "password";
  String defaultCibaAuthenticationInteractionType = "authentication-device-notification";
  int oauthAuthorizationRequestExpiresIn = 1800;
  List<AuthenticationPolicy> authenticationPolicies = new ArrayList<>();
  boolean customClaimsScopeMapping = false;
  boolean accessTokenSelectiveUserCustomProperties = false;
  boolean accessTokenVerifiedClaims = false;
  boolean accessTokenSelectiveVerifiedClaims = false;

  public AuthorizationServerExtensionConfiguration() {}

  public boolean hasFapiBaselineScope(Set<String> scopes) {
    return scopes.stream().anyMatch(scope -> fapiBaselineScopes.contains(scope));
  }

  public boolean hasFapiAdvanceScope(Set<String> scopes) {
    return scopes.stream().anyMatch(scope -> fapiAdvanceScopes.contains(scope));
  }

  public boolean hasRequiredIdentityVerificationScope(Set<String> scopes) {
    return scopes.stream().anyMatch(scope -> requiredIdentityVerificationScopes.contains(scope));
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

  public AccessTokenStrategy accessTokenStrategy() {
    return AccessTokenStrategy.of(accessTokenStrategy);
  }

  public boolean isFixedAccessTokenStrategy() {
    return accessTokenStrategy().isFixed();
  }

  public boolean isExtendsAccessTokenStrategy() {
    return accessTokenStrategy().isExtends();
  }

  public boolean isRotateRefreshToken() {
    return rotateRefreshToken;
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

  public int backchannelAuthenticationRequestExpiresIn() {
    return backchannelAuthenticationRequestExpiresIn;
  }

  public int backchannelAuthenticationPollingInterval() {
    return backchannelAuthenticationPollingInterval;
  }

  public boolean requiredBackchannelAuthUserCode() {
    return requiredBackchannelAuthUserCode;
  }

  public String backchannelAuthUserCodeType() {
    return backchannelAuthUserCodeType;
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

  public List<String> requiredIdentityVerificationScopes() {
    return requiredIdentityVerificationScopes;
  }

  public boolean idTokenStrictMode() {
    return idTokenStrictMode;
  }

  public List<AuthenticationPolicy> authenticationPolicies() {
    return authenticationPolicies;
  }

  public boolean enabledCustomClaimsScopeMapping() {
    return customClaimsScopeMapping;
  }

  public boolean enabledAccessTokenSelectiveUserCustomProperties() {
    return accessTokenSelectiveUserCustomProperties;
  }

  public boolean enabledAccessTokenVerifiedClaims() {
    return accessTokenVerifiedClaims;
  }

  public boolean enabledAccessTokenSelectiveVerifiedClaims() {
    return accessTokenSelectiveVerifiedClaims;
  }

  public AuthenticationInteractionType defaultCibaAuthenticationInteractionType() {
    return new AuthenticationInteractionType(defaultCibaAuthenticationInteractionType);
  }
}
