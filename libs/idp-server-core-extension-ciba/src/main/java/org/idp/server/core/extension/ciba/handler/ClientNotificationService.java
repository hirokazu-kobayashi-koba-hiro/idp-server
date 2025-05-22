/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.ciba.handler;

import java.util.UUID;
import org.idp.server.basic.type.oauth.ExpiresIn;
import org.idp.server.basic.type.oauth.TokenType;
import org.idp.server.basic.type.oidc.IdToken;
import org.idp.server.core.extension.ciba.clientnotification.ClientNotificationRequest;
import org.idp.server.core.extension.ciba.clientnotification.ClientNotificationRequestBodyBuilder;
import org.idp.server.core.extension.ciba.gateway.ClientNotificationGateway;
import org.idp.server.core.extension.ciba.grant.CibaGrant;
import org.idp.server.core.extension.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.grant_management.AuthorizationGranted;
import org.idp.server.core.oidc.grant_management.AuthorizationGrantedIdentifier;
import org.idp.server.core.oidc.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.oidc.id_token.IdTokenCreatable;
import org.idp.server.core.oidc.id_token.IdTokenCustomClaims;
import org.idp.server.core.oidc.id_token.IdTokenCustomClaimsBuilder;
import org.idp.server.core.oidc.id_token.RequestedClaimsPayload;
import org.idp.server.core.oidc.token.*;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.core.oidc.token.OAuthTokenBuilder;
import org.idp.server.core.oidc.token.OAuthTokenIdentifier;
import org.idp.server.core.oidc.token.repository.OAuthTokenRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

// FIXME consider. this is bad code.
public class ClientNotificationService
    implements AccessTokenCreatable, IdTokenCreatable, RefreshTokenCreatable {

  BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository;
  AuthorizationGrantedRepository authorizationGrantedRepository;
  OAuthTokenRepository oAuthTokenRepository;
  ClientNotificationGateway clientNotificationGateway;

  public ClientNotificationService(
      BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository,
      OAuthTokenRepository oAuthTokenRepository,
      ClientNotificationGateway clientNotificationGateway) {
    this.backchannelAuthenticationRequestRepository = backchannelAuthenticationRequestRepository;
    this.authorizationGrantedRepository = authorizationGrantedRepository;
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.clientNotificationGateway = clientNotificationGateway;
  }

  public void notify(
      Tenant tenant,
      CibaGrant cibaGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    BackchannelAuthenticationRequest backchannelAuthenticationRequest =
        backchannelAuthenticationRequestRepository.find(
            tenant, cibaGrant.backchannelAuthenticationRequestIdentifier());
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
          createAccessToken(
              cibaGrant.authorizationGrant(),
              authorizationServerConfiguration,
              clientConfiguration,
              new ClientCredentials());
      RefreshToken refreshToken =
          createRefreshToken(authorizationServerConfiguration, clientConfiguration);

      IdTokenCustomClaims idTokenCustomClaims = new IdTokenCustomClaimsBuilder().build();
      IdToken idToken =
          createIdToken(
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
      oAuthTokenRepository.register(tenant, oAuthToken);
    }
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
