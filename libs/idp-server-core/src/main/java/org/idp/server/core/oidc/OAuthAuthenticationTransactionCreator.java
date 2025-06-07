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
import java.util.UUID;
import org.idp.server.basic.type.AuthFlow;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.configuration.authentication.AuthenticationPolicy;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.io.OAuthRequestResponse;
import org.idp.server.core.oidc.rar.AuthorizationDetails;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class OAuthAuthenticationTransactionCreator {

  public static AuthenticationTransaction create(
      Tenant tenant, OAuthRequestResponse requestResponse) {

    AuthenticationTransactionIdentifier identifier =
        new AuthenticationTransactionIdentifier(UUID.randomUUID().toString());
    AuthorizationIdentifier authorizationIdentifier =
        new AuthorizationIdentifier(requestResponse.authorizationRequestIdentifier().value());

    AuthenticationRequest authenticationRequest = toAuthenticationRequest(tenant, requestResponse);
    AuthenticationPolicy authenticationPolicy = requestResponse.findSatisfiedAuthenticationPolicy();
    AuthenticationTransactionAttributes attributes = new AuthenticationTransactionAttributes();

    return new AuthenticationTransaction(
        identifier,
        authorizationIdentifier,
        authenticationRequest,
        authenticationPolicy,
        attributes);
  }

  private static AuthenticationRequest toAuthenticationRequest(
      Tenant tenant, OAuthRequestResponse requestResponse) {

    AuthorizationRequest authorizationRequest = requestResponse.authorizationRequest();
    AuthFlow authFlow = AuthFlow.OAUTH;
    TenantIdentifier tenantIdentifier = tenant.identifier();

    RequestedClientId requestedClientId = authorizationRequest.retrieveClientId();
    User user = User.notFound();
    AuthorizationDetails authorizationDetails =
        requestResponse.authorizationRequest().authorizationDetails();
    AuthenticationContext context =
        new AuthenticationContext(
            authorizationRequest.acrValues(), authorizationRequest.scopes(), authorizationDetails);
    LocalDateTime createdAt = SystemDateTime.now();
    LocalDateTime expiredAt =
        createdAt.plusSeconds(requestResponse.oauthAuthorizationRequestExpiresIn());
    return new AuthenticationRequest(
        authFlow, tenantIdentifier, requestedClientId, user, context, createdAt, expiredAt);
  }
}
