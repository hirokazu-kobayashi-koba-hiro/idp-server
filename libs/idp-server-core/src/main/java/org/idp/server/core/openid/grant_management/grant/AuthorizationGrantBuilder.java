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

package org.idp.server.core.openid.grant_management.grant;

import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.grant_management.grant.consent.ConsentClaims;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.client.ClientAttributes;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetails;
import org.idp.server.core.openid.oauth.type.extension.CustomProperties;
import org.idp.server.core.openid.oauth.type.oauth.GrantType;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class AuthorizationGrantBuilder {

  TenantIdentifier tenantIdentifier;
  User user = new User();
  Authentication authentication = new Authentication();
  RequestedClientId requestedClientId;
  ClientAttributes clientAttributes = new ClientAttributes();
  GrantType grantType;
  Scopes scopes;
  GrantIdTokenClaims grantIdTokenClaims = new GrantIdTokenClaims();
  GrantUserinfoClaims grantUserinfoClaims = new GrantUserinfoClaims();
  CustomProperties customProperties = new CustomProperties();
  AuthorizationDetails authorizationDetails = new AuthorizationDetails();
  ConsentClaims consentClaims = new ConsentClaims();

  public AuthorizationGrantBuilder(
      TenantIdentifier tenantIdentifier,
      RequestedClientId requestedClientId,
      GrantType grantType,
      Scopes scopes) {
    this.tenantIdentifier = tenantIdentifier;
    this.requestedClientId = requestedClientId;
    this.grantType = grantType;
    this.scopes = scopes;
  }

  public AuthorizationGrantBuilder add(User user) {
    this.user = user;
    return this;
  }

  public AuthorizationGrantBuilder add(Authentication authentication) {
    this.authentication = authentication;
    return this;
  }

  public AuthorizationGrantBuilder add(ClientAttributes clientAttributes) {
    this.clientAttributes = clientAttributes;
    return this;
  }

  public AuthorizationGrantBuilder add(GrantIdTokenClaims grantIdTokenClaims) {
    this.grantIdTokenClaims = grantIdTokenClaims;
    return this;
  }

  public AuthorizationGrantBuilder add(GrantUserinfoClaims grantUserinfoClaims) {
    this.grantUserinfoClaims = grantUserinfoClaims;
    return this;
  }

  public AuthorizationGrantBuilder add(CustomProperties customProperties) {
    this.customProperties = customProperties;
    return this;
  }

  public AuthorizationGrantBuilder add(AuthorizationDetails authorizationDetails) {
    this.authorizationDetails = authorizationDetails;
    return this;
  }

  public AuthorizationGrantBuilder add(ConsentClaims consentClaims) {
    this.consentClaims = consentClaims;
    return this;
  }

  public AuthorizationGrant build() {
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
}
