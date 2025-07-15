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

package org.idp.server.core.oidc;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientAttributes;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.grant.GrantIdTokenClaims;
import org.idp.server.core.oidc.grant.GrantUserinfoClaims;
import org.idp.server.core.oidc.grant.consent.ConsentClaim;
import org.idp.server.core.oidc.grant.consent.ConsentClaims;
import org.idp.server.core.oidc.id_token.RequestedClaimsPayload;
import org.idp.server.core.oidc.id_token.RequestedIdTokenClaims;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.rar.AuthorizationDetails;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.response.ResponseModeDecidable;
import org.idp.server.core.oidc.type.extension.CustomProperties;
import org.idp.server.core.oidc.type.extension.DeniedScopes;
import org.idp.server.core.oidc.type.extension.ExpiresAt;
import org.idp.server.core.oidc.type.oauth.*;
import org.idp.server.core.oidc.type.oidc.ResponseMode;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/** OAuthAuthorizeContext */
public class OAuthAuthorizeContext implements ResponseModeDecidable {
  AuthorizationRequest authorizationRequest;
  User user;
  Authentication authentication;
  CustomProperties customProperties;
  DeniedScopes deniedScopes;
  AuthorizationServerConfiguration authorizationServerConfiguration;
  ClientConfiguration clientConfiguration;

  public OAuthAuthorizeContext() {}

  public OAuthAuthorizeContext(
      AuthorizationRequest authorizationRequest,
      User user,
      Authentication authentication,
      CustomProperties customProperties,
      DeniedScopes deniedScopes,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    this.authorizationRequest = authorizationRequest;
    this.user = user;
    this.authentication = authentication;
    this.customProperties = customProperties;
    this.deniedScopes = deniedScopes;
    this.clientConfiguration = clientConfiguration;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
  }

  public AuthorizationRequest authorizationRequest() {
    return authorizationRequest;
  }

  public User user() {
    return user;
  }

  public Authentication authentication() {
    return authentication;
  }

  public RequestedClaimsPayload requestedClaimsPayload() {
    return authorizationRequest.requestedClaimsPayload();
  }

  public AuthorizationGrant authorize() {

    TenantIdentifier tenantIdentifier = authorizationRequest.tenantIdentifier();
    RequestedClientId requestedClientId = authorizationRequest.requestedClientId();
    ClientAttributes clientAttributes = clientConfiguration.clientAttributes();

    Scopes scopes = authorizationRequest.scopes();
    Scopes removeScopes = scopes.removeScopes(deniedScopes);
    ResponseType responseType = authorizationRequest.responseType();
    List<String> supportedClaims = authorizationServerConfiguration.claimsSupported();
    RequestedClaimsPayload requestedClaimsPayload = authorizationRequest.requestedClaimsPayload();
    boolean idTokenStrictMode = serverConfiguration().isIdTokenStrictMode();

    GrantIdTokenClaims grantIdTokenClaims =
        GrantIdTokenClaims.create(
            removeScopes,
            responseType,
            supportedClaims,
            requestedClaimsPayload.idToken(),
            idTokenStrictMode);
    GrantUserinfoClaims grantUserinfoClaims =
        GrantUserinfoClaims.create(scopes, supportedClaims, requestedClaimsPayload.userinfo());
    AuthorizationDetails authorizationDetails = authorizationRequest.authorizationDetails();
    ConsentClaims consentClaims = createConsentClaims();
    GrantType grantType = GrantType.authorization_code;

    return new AuthorizationGrant(
        tenantIdentifier,
        user,
        authentication,
        requestedClientId,
        clientAttributes,
        grantType,
        scopes,
        grantIdTokenClaims,
        grantUserinfoClaims,
        customProperties,
        authorizationDetails,
        consentClaims);
  }

  private ConsentClaims createConsentClaims() {
    Map<String, List<ConsentClaim>> contents = new HashMap<>();
    LocalDateTime now = SystemDateTime.now();

    if (clientConfiguration.hasTosUri()) {
      contents.put(
          "terms", List.of(new ConsentClaim("tos_uri", clientConfiguration.tosUri(), now)));
    }

    if (clientConfiguration.hasPolicyUri()) {
      contents.put(
          "privacy", List.of(new ConsentClaim("policy_uri", clientConfiguration.policyUri(), now)));
    }

    return new ConsentClaims(contents);
  }

  public CustomProperties customProperties() {
    return customProperties;
  }

  public AuthorizationServerConfiguration serverConfiguration() {
    return authorizationServerConfiguration;
  }

  public ClientConfiguration clientConfiguration() {
    return clientConfiguration;
  }

  public TokenIssuer tokenIssuer() {
    return authorizationServerConfiguration.tokenIssuer();
  }

  public ResponseType responseType() {
    return authorizationRequest.responseType();
  }

  public ResponseMode responseMode() {
    return authorizationRequest.responseMode();
  }

  public boolean isJwtMode() {
    return isJwtMode(authorizationRequest.profile(), responseType(), responseMode());
  }

  public ExpiresAt authorizationCodeGrantExpiresDateTime() {
    LocalDateTime localDateTime = SystemDateTime.now();
    int duration = authorizationServerConfiguration.authorizationCodeValidDuration();
    return new ExpiresAt(localDateTime.plusMinutes(duration));
  }

  public RequestedIdTokenClaims idTokenClaims() {
    return authorizationRequest.requestedClaimsPayload().idToken();
  }

  public boolean hasState() {
    return authorizationRequest.hasState();
  }
}
