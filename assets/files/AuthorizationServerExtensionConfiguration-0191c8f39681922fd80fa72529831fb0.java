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
  String refreshTokenStrategy = "FIXED";
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

  public RefreshTokenStrategy refreshTokenStrategy() {
    return RefreshTokenStrategy.of(refreshTokenStrategy);
  }

  public boolean isFixedRefreshTokenStrategy() {
    return refreshTokenStrategy().isFixed();
  }

  public boolean isExtendsRefreshTokenStrategy() {
    return refreshTokenStrategy().isExtends();
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

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("access_token_type", accessTokenType);
    map.put("authorization_code_valid_duration", authorizationCodeValidDuration);
    map.put("token_signed_key_id", tokenSignedKeyId);
    map.put("id_token_signed_key_id", idTokenSignedKeyId);
    map.put("access_token_duration", accessTokenDuration);
    map.put("refresh_token_duration", refreshTokenDuration);
    map.put("refresh_token_strategy", refreshTokenStrategy);
    map.put("rotate_refresh_token", rotateRefreshToken);
    map.put("id_token_duration", idTokenDuration);
    map.put("id_token_strict_mode", idTokenStrictMode);
    map.put("default_max_age", defaultMaxAge);
    map.put("authorization_response_duration", authorizationResponseDuration);
    map.put(
        "backchannel_authentication_request_expires_in", backchannelAuthenticationRequestExpiresIn);
    map.put(
        "backchannel_authentication_polling_interval", backchannelAuthenticationPollingInterval);
    map.put("required_backchannel_auth_user_code", requiredBackchannelAuthUserCode);
    map.put("backchannel_auth_user_code_type", backchannelAuthUserCodeType);
    map.put(
        "default_ciba_authentication_interaction_type", defaultCibaAuthenticationInteractionType);
    map.put("oauth_authorization_request_expires_in", oauthAuthorizationRequestExpiresIn);
    map.put("fapi_baseline_scopes", fapiBaselineScopes);
    map.put("fapi_advance_scopes", fapiAdvanceScopes);
    map.put("required_identity_verification_scopes", requiredIdentityVerificationScopes);
    map.put("custom_claims_scope_mapping", customClaimsScopeMapping);
    map.put(
        "access_token_selective_user_custom_properties", accessTokenSelectiveUserCustomProperties);
    map.put("access_token_verified_claims", accessTokenVerifiedClaims);
    map.put("access_token_selective_verified_claims", accessTokenSelectiveVerifiedClaims);
    return map;
  }
}
