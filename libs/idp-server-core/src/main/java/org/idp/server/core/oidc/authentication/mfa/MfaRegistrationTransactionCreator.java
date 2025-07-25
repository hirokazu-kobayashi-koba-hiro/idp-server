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

package org.idp.server.core.oidc.authentication.mfa;

import java.time.LocalDateTime;
import java.util.UUID;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.configuration.authentication.AuthenticationPolicy;
import org.idp.server.core.oidc.configuration.client.ClientAttributes;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.device.AuthenticationDevice;
import org.idp.server.core.oidc.identity.io.MfaRegistrationRequest;
import org.idp.server.core.oidc.rar.AuthorizationDetails;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.core.oidc.type.AuthFlow;
import org.idp.server.core.oidc.type.ciba.BindingMessage;
import org.idp.server.core.oidc.type.oauth.RequestedClientId;
import org.idp.server.core.oidc.type.oauth.Scopes;
import org.idp.server.core.oidc.type.oidc.AcrValues;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantAttributes;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class MfaRegistrationTransactionCreator {

  public static AuthenticationTransaction create(
      Tenant tenant,
      User user,
      OAuthToken oAuthToken,
      AuthFlow authFlow,
      MfaRegistrationRequest mfaRegistrationRequest,
      AuthenticationPolicy authenticationPolicy) {

    AuthenticationTransactionIdentifier identifier =
        new AuthenticationTransactionIdentifier(UUID.randomUUID().toString());
    AuthorizationIdentifier authorizationIdentifier = new AuthorizationIdentifier();

    AuthenticationRequest authenticationRequest =
        toAuthenticationRequest(tenant, user, oAuthToken, authFlow);
    AuthenticationTransactionAttributes attributes =
        new AuthenticationTransactionAttributes(mfaRegistrationRequest.toMap());

    return new AuthenticationTransaction(
        identifier,
        authorizationIdentifier,
        authenticationRequest,
        authenticationPolicy,
        attributes);
  }

  private static AuthenticationRequest toAuthenticationRequest(
      Tenant tenant, User user, OAuthToken oAuthToken, AuthFlow authFlow) {

    TenantIdentifier tenantIdentifier = tenant.identifier();
    TenantAttributes tenantAttributes = tenant.attributes();
    AuthenticationDevice authenticationDevice = user.findPrimaryAuthenticationDevice();

    RequestedClientId requestedClientId = oAuthToken.requestedClientId();
    ClientAttributes clientAttributes = oAuthToken.clientAttributes();
    AuthenticationContext context =
        new AuthenticationContext(
            new AcrValues(), new Scopes(), new BindingMessage(), new AuthorizationDetails());
    LocalDateTime createdAt = SystemDateTime.now();
    LocalDateTime expiredAt = createdAt.plusSeconds(300);

    return new AuthenticationRequest(
        authFlow,
        tenantIdentifier,
        tenantAttributes,
        requestedClientId,
        clientAttributes,
        user,
        authenticationDevice,
        context,
        createdAt,
        expiredAt);
  }
}
