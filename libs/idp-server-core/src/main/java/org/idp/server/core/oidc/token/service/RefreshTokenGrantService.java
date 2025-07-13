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
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.token.*;
import org.idp.server.core.oidc.token.repository.OAuthTokenCommandRepository;
import org.idp.server.core.oidc.token.repository.OAuthTokenQueryRepository;
import org.idp.server.core.oidc.token.validator.RefreshTokenGrantValidator;
import org.idp.server.core.oidc.token.verifier.RefreshTokenVerifier;
import org.idp.server.core.oidc.type.oauth.GrantType;
import org.idp.server.core.oidc.type.oauth.RefreshTokenEntity;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class RefreshTokenGrantService implements OAuthTokenCreationService, RefreshTokenCreatable {

  OAuthTokenCommandRepository oAuthTokenCommandRepository;
  OAuthTokenQueryRepository oAuthTokenQueryRepository;
  AccessTokenCreator accessTokenCreator;

  public RefreshTokenGrantService(
      OAuthTokenCommandRepository oAuthTokenCommandRepository,
      OAuthTokenQueryRepository oAuthTokenQueryRepository) {
    this.oAuthTokenCommandRepository = oAuthTokenCommandRepository;
    this.oAuthTokenQueryRepository = oAuthTokenQueryRepository;
    this.accessTokenCreator = AccessTokenCreator.getInstance();
  }

  @Override
  public GrantType grantType() {
    return GrantType.refresh_token;
  }

  @Override
  public OAuthToken create(TokenRequestContext context, ClientCredentials clientCredentials) {
    RefreshTokenGrantValidator validator = new RefreshTokenGrantValidator(context);
    validator.validate();

    Tenant tenant = context.tenant();
    RefreshTokenEntity refreshTokenEntity = context.refreshToken();
    AuthorizationServerConfiguration authorizationServerConfiguration =
        context.serverConfiguration();
    ClientConfiguration clientConfiguration = context.clientConfiguration();
    OAuthToken oAuthToken = oAuthTokenQueryRepository.find(context.tenant(), refreshTokenEntity);

    RefreshTokenVerifier verifier = new RefreshTokenVerifier(context, oAuthToken);
    verifier.verify();
    AuthorizationGrant authorizationGrant = oAuthToken.authorizationGrant();

    AccessToken accessToken =
        accessTokenCreator.refresh(
            oAuthToken.accessToken(),
            authorizationGrant,
            authorizationServerConfiguration,
            clientConfiguration,
            clientCredentials);

    RefreshToken refreshToken =
        refresh(oAuthToken.refreshToken(), authorizationServerConfiguration, clientConfiguration);

    OAuthTokenBuilder oAuthTokenBuilder =
        new OAuthTokenBuilder(new OAuthTokenIdentifier(UUID.randomUUID().toString()))
            .add(accessToken)
            .add(refreshToken);

    oAuthTokenCommandRepository.delete(tenant, oAuthToken);

    OAuthToken refresh = oAuthTokenBuilder.build();
    oAuthTokenCommandRepository.register(tenant, refresh);

    return refresh;
  }
}
