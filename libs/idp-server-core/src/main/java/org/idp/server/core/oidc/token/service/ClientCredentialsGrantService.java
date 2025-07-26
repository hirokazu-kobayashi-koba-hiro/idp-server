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

package org.idp.server.core.oidc.token.service;

import java.util.UUID;
import org.idp.server.core.oidc.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.grant.AuthorizationGrantBuilder;
import org.idp.server.core.oidc.token.*;
import org.idp.server.core.oidc.token.repository.OAuthTokenCommandRepository;
import org.idp.server.core.oidc.token.validator.ClientCredentialsGrantValidator;
import org.idp.server.core.oidc.token.verifier.ClientCredentialsGrantVerifier;
import org.idp.server.core.oidc.type.extension.CustomProperties;
import org.idp.server.core.oidc.type.oauth.GrantType;
import org.idp.server.core.oidc.type.oauth.Scopes;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class ClientCredentialsGrantService implements OAuthTokenCreationService {
  OAuthTokenCommandRepository oAuthTokenCommandRepository;
  AccessTokenCreator accessTokenCreator;

  public ClientCredentialsGrantService(OAuthTokenCommandRepository oAuthTokenCommandRepository) {
    this.oAuthTokenCommandRepository = oAuthTokenCommandRepository;
    this.accessTokenCreator = AccessTokenCreator.getInstance();
  }

  @Override
  public GrantType grantType() {
    return GrantType.client_credentials;
  }

  @Override
  public OAuthToken create(TokenRequestContext context, ClientCredentials clientCredentials) {
    ClientCredentialsGrantValidator validator = new ClientCredentialsGrantValidator(context);
    validator.validate();

    Tenant tenant = context.tenant();
    AuthorizationServerConfiguration authorizationServerConfiguration =
        context.serverConfiguration();
    ClientConfiguration clientConfiguration = context.clientConfiguration();

    Scopes scopes =
        new Scopes(clientConfiguration.filteredScope(context.scopes().toStringValues()));
    ClientCredentialsGrantVerifier verifier = new ClientCredentialsGrantVerifier(scopes);
    verifier.verify();

    CustomProperties customProperties = context.customProperties();
    AuthorizationGrant authorizationGrant =
        new AuthorizationGrantBuilder(
                context.tenantIdentifier(),
                context.requestedClientId(),
                GrantType.client_credentials,
                scopes)
            .add(customProperties)
            .add(clientConfiguration.clientAttributes())
            .build();

    AccessToken accessToken =
        accessTokenCreator.create(
            authorizationGrant,
            authorizationServerConfiguration,
            clientConfiguration,
            clientCredentials);

    OAuthToken oAuthToken =
        new OAuthTokenBuilder(new OAuthTokenIdentifier(UUID.randomUUID().toString()))
            .add(accessToken)
            .build();

    oAuthTokenCommandRepository.register(tenant, oAuthToken);
    return oAuthToken;
  }
}
