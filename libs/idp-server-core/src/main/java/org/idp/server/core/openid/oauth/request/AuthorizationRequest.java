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

package org.idp.server.core.openid.oauth.request;

import org.idp.server.core.openid.identity.id_token.RequestedClaimsPayload;
import org.idp.server.core.openid.identity.id_token.RequestedIdTokenClaims;
import org.idp.server.core.openid.identity.id_token.RequestedUserinfoClaims;
import org.idp.server.core.openid.oauth.AuthorizationProfile;
import org.idp.server.core.openid.oauth.OAuthSessionKey;
import org.idp.server.core.openid.oauth.configuration.client.ClientAttributes;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetails;
import org.idp.server.core.openid.oauth.type.extension.ExpiresAt;
import org.idp.server.core.openid.oauth.type.oauth.*;
import org.idp.server.core.openid.oauth.type.oidc.*;
import org.idp.server.core.openid.oauth.type.pkce.CodeChallenge;
import org.idp.server.core.openid.oauth.type.pkce.CodeChallengeMethod;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/** AuthorizationRequest */
public class AuthorizationRequest {

  AuthorizationRequestIdentifier identifier = new AuthorizationRequestIdentifier();
  TenantIdentifier tenantIdentifier;
  AuthorizationProfile profile = AuthorizationProfile.UNDEFINED;
  Scopes scopes;
  ResponseType responseType;
  ClientAttributes clientAttributes;
  RequestedClientId requestedClientId;
  RedirectUri redirectUri;
  State state;
  ResponseMode responseMode;
  Nonce nonce;
  Display display;
  Prompts prompts;
  MaxAge maxAge;
  UiLocales uiLocales;
  IdTokenHint idTokenHint;
  LoginHint loginHint;
  AcrValues acrValues;
  ClaimsValue claimsValue;
  RequestObject requestObject;
  RequestUri requestUri;
  RequestedClaimsPayload requestedClaimsPayload;
  CodeChallenge codeChallenge;
  CodeChallengeMethod codeChallengeMethod;
  AuthorizationDetails authorizationDetails;
  CustomParams customParams;
  ExpiresIn expiresIn;
  ExpiresAt expiresAt;

  public AuthorizationRequest() {}

  AuthorizationRequest(
      AuthorizationRequestIdentifier identifier,
      TenantIdentifier tenantIdentifier,
      AuthorizationProfile profile,
      Scopes scopes,
      ResponseType responseType,
      RequestedClientId requestedClientId,
      ClientAttributes clientAttributes,
      RedirectUri redirectUri,
      State state,
      ResponseMode responseMode,
      Nonce nonce,
      Display display,
      Prompts prompts,
      MaxAge maxAge,
      UiLocales uiLocales,
      IdTokenHint idTokenHint,
      LoginHint loginHint,
      AcrValues acrValues,
      ClaimsValue claimsValue,
      RequestObject requestObject,
      RequestUri requestUri,
      RequestedClaimsPayload requestedClaimsPayload,
      CodeChallenge codeChallenge,
      CodeChallengeMethod codeChallengeMethod,
      AuthorizationDetails authorizationDetails,
      CustomParams customParams,
      ExpiresIn expiresIn,
      ExpiresAt expiresAt) {
    this.identifier = identifier;
    this.tenantIdentifier = tenantIdentifier;
    this.profile = profile;
    this.scopes = scopes;
    this.responseType = responseType;
    this.requestedClientId = requestedClientId;
    this.clientAttributes = clientAttributes;
    this.redirectUri = redirectUri;
    this.state = state;
    this.responseMode = responseMode;
    this.nonce = nonce;
    this.display = display;
    this.prompts = prompts;
    this.maxAge = maxAge;
    this.uiLocales = uiLocales;
    this.idTokenHint = idTokenHint;
    this.loginHint = loginHint;
    this.acrValues = acrValues;
    this.claimsValue = claimsValue;
    this.requestObject = requestObject;
    this.requestUri = requestUri;
    this.requestedClaimsPayload = requestedClaimsPayload;
    this.codeChallenge = codeChallenge;
    this.codeChallengeMethod = codeChallengeMethod;
    this.authorizationDetails = authorizationDetails;
    this.customParams = customParams;
    this.expiresIn = expiresIn;
    this.expiresAt = expiresAt;
  }

  public AuthorizationRequestIdentifier identifier() {
    return identifier;
  }

  public TenantIdentifier tenantIdentifier() {
    return tenantIdentifier;
  }

  public AuthorizationProfile profile() {
    return profile;
  }

  public boolean hasProfile() {
    return profile.isDefined();
  }

  public Scopes scopes() {
    return scopes;
  }

  public boolean hasScope() {
    return scopes.exists();
  }

  public ResponseType responseType() {
    return responseType;
  }

  public boolean hasResponseType() {
    return !responseType.isUndefined();
  }

  public RequestedClientId requestedClientId() {
    return requestedClientId;
  }

  public boolean hasClientId() {
    return requestedClientId.exists();
  }

  public ClientAttributes clientAttributes() {
    return clientAttributes;
  }

  public String clientNameValue() {
    return clientAttributes.clientName().value();
  }

  public RedirectUri redirectUri() {
    return redirectUri;
  }

  public boolean hasRedirectUri() {
    return redirectUri.exists();
  }

  public State state() {
    return state;
  }

  public boolean hasState() {
    return state.exists();
  }

  public ResponseMode responseMode() {
    return responseMode;
  }

  public boolean hasResponseMode() {
    return responseMode.isDefined();
  }

  public Nonce nonce() {
    return nonce;
  }

  public boolean hasNonce() {
    return nonce.exists();
  }

  public Display display() {
    return display;
  }

  public boolean hasDisplay() {
    return display.isDefined();
  }

  public Prompts prompts() {
    return prompts;
  }

  public boolean hasPrompts() {
    return prompts.exists();
  }

  public MaxAge maxAge() {
    return maxAge;
  }

  public boolean hasMaxAge() {
    return maxAge.exists();
  }

  public UiLocales uiLocales() {
    return uiLocales;
  }

  public boolean hasUilocales() {
    return uiLocales.exists();
  }

  public IdTokenHint idTokenHint() {
    return idTokenHint;
  }

  public boolean hasIdTokenHint() {
    return idTokenHint.exists();
  }

  public LoginHint loginHint() {
    return loginHint;
  }

  public boolean hasLoginHint() {
    return loginHint.exists();
  }

  public AcrValues acrValues() {
    return acrValues;
  }

  public boolean hasAcrValues() {
    return acrValues.exists();
  }

  public ClaimsValue claims() {
    return claimsValue;
  }

  public boolean hasClaims() {
    return claimsValue.exists();
  }

  public RequestObject request() {
    return requestObject;
  }

  public boolean hasRequest() {
    return requestObject.exists();
  }

  public RequestUri requestUri() {
    return requestUri;
  }

  public boolean hasRequestUri() {
    return requestUri.exists();
  }

  public RequestedClaimsPayload requestedClaimsPayload() {
    return requestedClaimsPayload;
  }

  public RequestedIdTokenClaims requestedIdTokenClaims() {
    return requestedClaimsPayload.idToken();
  }

  public RequestedUserinfoClaims requestedUserinfoClaims() {
    return requestedClaimsPayload.userinfo();
  }

  public boolean hasClaimsPayload() {
    return requestedClaimsPayload.exists();
  }

  public CodeChallenge codeChallenge() {
    return codeChallenge;
  }

  public CustomParams customParams() {
    return customParams;
  }

  public ExpiresIn expiresIn() {
    return expiresIn;
  }

  public ExpiresAt expiredAt() {
    return expiresAt;
  }

  public boolean hasCodeChallenge() {
    return codeChallenge.exists();
  }

  public CodeChallengeMethod codeChallengeMethod() {
    return codeChallengeMethod;
  }

  public boolean hasCodeChallengeMethod() {
    return codeChallengeMethod.isDefined();
  }

  public AuthorizationDetails authorizationDetails() {
    return authorizationDetails;
  }

  public boolean hasAuthorizationDetails() {
    return authorizationDetails.exists();
  }

  public boolean isInvalidDisplay() {
    return display.isUnknown();
  }

  public boolean isInvalidPrompt() {
    return prompts.hasUnknown();
  }

  public boolean isInvalidMaxAge() {
    return !maxAge.isValid();
  }

  public boolean isFapiProfile() {
    return profile.isFapiBaseline() || profile.isFapiAdvance();
  }

  public boolean isOidcProfile() {
    return scopes.contains("openid");
  }

  public boolean exists() {
    return identifier.exists();
  }

  public boolean isPkceRequest() {
    return hasCodeChallenge();
  }

  public boolean isPkceWithS256() {
    return codeChallengeMethod.isS256();
  }

  public boolean isPromptNone() {
    return prompts().hasNone();
  }

  public boolean isPromptLogin() {
    return prompts().hasLogin();
  }

  public boolean isPromptCreate() {
    return prompts().hasCreate();
  }

  public boolean isVerifiableCredentialRequest() {
    return authorizationDetails.hasVerifiableCredential();
  }

  public boolean hasCustomParams() {
    return customParams.exists();
  }

  public OAuthSessionKey sessionKey() {
    return new OAuthSessionKey(tenantIdentifier.value(), requestedClientId.value());
  }
}
