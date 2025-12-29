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

package org.idp.server.core.extension.ciba.handler;

import java.util.UUID;
import org.idp.server.core.extension.ciba.clientnotification.ClientNotificationRequest;
import org.idp.server.core.extension.ciba.clientnotification.ClientNotificationRequestBodyBuilder;
import org.idp.server.core.extension.ciba.gateway.ClientNotificationGateway;
import org.idp.server.core.extension.ciba.grant.CibaGrant;
import org.idp.server.core.extension.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.grant_management.AuthorizationGranted;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedIdentifier;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.openid.identity.id_token.IdTokenCreator;
import org.idp.server.core.openid.identity.id_token.IdTokenCustomClaims;
import org.idp.server.core.openid.identity.id_token.IdTokenCustomClaimsBuilder;
import org.idp.server.core.openid.identity.id_token.RequestedClaimsPayload;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.oauth.ExpiresIn;
import org.idp.server.core.openid.oauth.type.oauth.TokenType;
import org.idp.server.core.openid.oauth.type.oidc.IdToken;
import org.idp.server.core.openid.token.*;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.OAuthTokenBuilder;
import org.idp.server.core.openid.token.OAuthTokenIdentifier;
import org.idp.server.core.openid.token.repository.OAuthTokenCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class ClientNotificationService implements RefreshTokenCreatable {

  BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository;
  AuthorizationGrantedRepository authorizationGrantedRepository;
  OAuthTokenCommandRepository oAuthTokenCommandRepository;
  ClientNotificationGateway clientNotificationGateway;
  IdTokenCreator idTokenCreator;
  AccessTokenCreator accessTokenCreator;

  public ClientNotificationService(
      BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository,
      OAuthTokenCommandRepository oAuthTokenCommandRepository,
      ClientNotificationGateway clientNotificationGateway) {
    this.backchannelAuthenticationRequestRepository = backchannelAuthenticationRequestRepository;
    this.authorizationGrantedRepository = authorizationGrantedRepository;
    this.oAuthTokenCommandRepository = oAuthTokenCommandRepository;
    this.clientNotificationGateway = clientNotificationGateway;
    this.idTokenCreator = IdTokenCreator.getInstance();
    this.accessTokenCreator = AccessTokenCreator.getInstance();
  }

  public void notify(
      Tenant tenant,
      BackchannelAuthenticationRequest backchannelAuthenticationRequest,
      CibaGrant cibaGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    ClientNotificationRequestBodyBuilder builder =
        new ClientNotificationRequestBodyBuilder().add(cibaGrant.authReqId());

    if (backchannelAuthenticationRequest.isPingMode()) {
      ClientNotificationRequest clientNotificationRequest =
          new ClientNotificationRequest(
              clientConfiguration.backchannelClientNotificationEndpoint(),
              builder.build(),
              backchannelAuthenticationRequest.clientNotificationToken().value());
      clientNotificationGateway.notify(clientNotificationRequest);
    }

    if (backchannelAuthenticationRequest.isPushMode()) {
      AccessToken accessToken =
          accessTokenCreator.create(
              cibaGrant.authorizationGrant(),
              authorizationServerConfiguration,
              clientConfiguration,
              new ClientCredentials());
      RefreshToken refreshToken =
          createRefreshToken(authorizationServerConfiguration, clientConfiguration);

      IdTokenCustomClaims idTokenCustomClaims = new IdTokenCustomClaimsBuilder().build();
      IdToken idToken =
          idTokenCreator.createIdToken(
              cibaGrant.user(),
              new Authentication(),
              cibaGrant.authorizationGrant(),
              idTokenCustomClaims,
              new RequestedClaimsPayload(),
              authorizationServerConfiguration,
              clientConfiguration);
      builder
          .add(accessToken.accessTokenEntity())
          .add(TokenType.Bearer)
          .add(new ExpiresIn(authorizationServerConfiguration.accessTokenDuration()))
          .add(refreshToken.refreshTokenEntity())
          .add(idToken);

      ClientNotificationRequest clientNotificationRequest =
          new ClientNotificationRequest(
              clientConfiguration.backchannelClientNotificationEndpoint(),
              builder.build(),
              backchannelAuthenticationRequest.clientNotificationToken().value());
      clientNotificationGateway.notify(clientNotificationRequest);

      OAuthTokenIdentifier identifier = new OAuthTokenIdentifier(UUID.randomUUID().toString());

      registerOrUpdate(tenant, cibaGrant);

      OAuthToken oAuthToken =
          new OAuthTokenBuilder(identifier).add(accessToken).add(refreshToken).add(idToken).build();
      oAuthTokenCommandRepository.register(tenant, oAuthToken);
    }
  }

  public void notifyError(
      BackchannelAuthenticationRequest backchannelAuthenticationRequest,
      CibaGrant cibaGrant,
      ClientConfiguration clientConfiguration,
      String error,
      String errorDescription) {

    ClientNotificationRequestBodyBuilder builder =
        new ClientNotificationRequestBodyBuilder()
            .add(cibaGrant.authReqId())
            .addError(error)
            .addErrorDescription(errorDescription);

    ClientNotificationRequest clientNotificationRequest =
        new ClientNotificationRequest(
            clientConfiguration.backchannelClientNotificationEndpoint(),
            builder.build(),
            backchannelAuthenticationRequest.clientNotificationToken().value());
    clientNotificationGateway.notify(clientNotificationRequest);
  }

  private void registerOrUpdate(Tenant tenant, CibaGrant cibaGrant) {
    AuthorizationGranted latest =
        authorizationGrantedRepository.find(
            tenant, cibaGrant.requestedClientId(), cibaGrant.user());

    if (latest.exists()) {
      AuthorizationGranted merge = latest.merge(cibaGrant.authorizationGrant());

      authorizationGrantedRepository.update(tenant, merge);
      return;
    }
    AuthorizationGrantedIdentifier authorizationGrantedIdentifier =
        new AuthorizationGrantedIdentifier(UUID.randomUUID().toString());
    AuthorizationGranted authorizationGranted =
        new AuthorizationGranted(authorizationGrantedIdentifier, cibaGrant.authorizationGrant());
    authorizationGrantedRepository.register(tenant, authorizationGranted);
  }
}
