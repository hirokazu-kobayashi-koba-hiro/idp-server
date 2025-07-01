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
import org.idp.server.basic.type.extension.CustomProperties;
import org.idp.server.basic.type.oauth.GrantType;
import org.idp.server.basic.type.oauth.Scopes;
import org.idp.server.basic.type.oidc.IdToken;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.grant.AuthorizationGrantBuilder;
import org.idp.server.core.oidc.id_token.*;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.token.*;
import org.idp.server.core.oidc.token.repository.OAuthTokenCommandRepository;
import org.idp.server.core.oidc.token.validator.ResourceOwnerPasswordGrantValidator;
import org.idp.server.core.oidc.token.verifier.ResourceOwnerPasswordGrantVerifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * ResourceOwnerPasswordCredentialsGrantService
 *
 * <p>The authorization server MUST:
 *
 * <p>o require client authentication for confidential clients or for any client that was issued
 * client credentials (or with other authentication requirements),
 *
 * <p>o authenticate the client if client authentication is included, and
 *
 * <p>o validate the resource owner password credentials using its existing password validation
 * algorithm.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.3.2">4.3.2. Access Token
 *     Request</a>
 */
public class ResourceOwnerPasswordCredentialsGrantService
    implements OAuthTokenCreationService, RefreshTokenCreatable {

  OAuthTokenCommandRepository oAuthTokenCommandRepository;
  IdTokenCreator idTokenCreator;
  AccessTokenCreator accessTokenCreator;

  public ResourceOwnerPasswordCredentialsGrantService(
      OAuthTokenCommandRepository oAuthTokenCommandRepository) {
    this.oAuthTokenCommandRepository = oAuthTokenCommandRepository;
    this.idTokenCreator = IdTokenCreator.getInstance();
    this.accessTokenCreator = AccessTokenCreator.getInstance();
  }

  @Override
  public GrantType grantType() {
    return GrantType.password;
  }

  @Override
  public OAuthToken create(TokenRequestContext context, ClientCredentials clientCredentials) {
    ResourceOwnerPasswordGrantValidator validator =
        new ResourceOwnerPasswordGrantValidator(context);
    validator.validate();

    Tenant tenant = context.tenant();
    AuthorizationServerConfiguration authorizationServerConfiguration =
        context.serverConfiguration();
    ClientConfiguration clientConfiguration = context.clientConfiguration();

    PasswordCredentialsGrantDelegate delegate = context.passwordCredentialsGrantDelegate();
    User user =
        delegate.findAndAuthenticate(context.tenant(), context.username(), context.password());
    Scopes scopes =
        new Scopes(clientConfiguration.filteredScope(context.scopes().toStringValues()));
    ResourceOwnerPasswordGrantVerifier verifier =
        new ResourceOwnerPasswordGrantVerifier(user, scopes);
    verifier.verify();

    CustomProperties customProperties = context.customProperties();
    AuthorizationGrant authorizationGrant =
        new AuthorizationGrantBuilder(
                context.tenantIdentifier(), context.requestedClientId(), GrantType.password, scopes)
            .add(user)
            .add(clientConfiguration.client())
            .add(customProperties)
            .build();

    AccessToken accessToken =
        accessTokenCreator.create(
            authorizationGrant,
            authorizationServerConfiguration,
            clientConfiguration,
            clientCredentials);
    RefreshToken refreshToken =
        createRefreshToken(authorizationServerConfiguration, clientConfiguration);
    OAuthTokenBuilder oAuthTokenBuilder =
        new OAuthTokenBuilder(new OAuthTokenIdentifier(UUID.randomUUID().toString()))
            .add(accessToken)
            .add(refreshToken);

    if (authorizationGrant.hasOpenidScope()) {
      IdTokenCustomClaims idTokenCustomClaims = new IdTokenCustomClaimsBuilder().build();
      IdToken idToken =
          idTokenCreator.createIdToken(
              authorizationGrant.user(),
              new Authentication(),
              authorizationGrant,
              idTokenCustomClaims,
              new RequestedClaimsPayload(),
              authorizationServerConfiguration,
              clientConfiguration);
      oAuthTokenBuilder.add(idToken);
    }

    OAuthToken oAuthToken = oAuthTokenBuilder.build();
    oAuthTokenCommandRepository.register(tenant, oAuthToken);
    return oAuthToken;
  }
}
