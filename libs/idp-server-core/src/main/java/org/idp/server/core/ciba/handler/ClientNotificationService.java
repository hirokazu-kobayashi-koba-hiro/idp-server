package org.idp.server.core.ciba.handler;

import java.util.UUID;
import org.idp.server.core.ciba.clientnotification.ClientNotificationRequest;
import org.idp.server.core.ciba.clientnotification.ClientNotificationRequestBodyBuilder;
import org.idp.server.core.ciba.gateway.ClientNotificationGateway;
import org.idp.server.core.ciba.grant.CibaGrant;
import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.grantmangment.AuthorizationGranted;
import org.idp.server.core.grantmangment.AuthorizationGrantedIdentifier;
import org.idp.server.core.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.core.identity.IdTokenCreatable;
import org.idp.server.core.identity.IdTokenCustomClaims;
import org.idp.server.core.identity.IdTokenCustomClaimsBuilder;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.clientcredentials.ClientCredentials;
import org.idp.server.core.oauth.token.*;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.OAuthTokenBuilder;
import org.idp.server.core.token.OAuthTokenIdentifier;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.core.type.oauth.ExpiresIn;
import org.idp.server.core.type.oauth.TokenType;
import org.idp.server.core.type.oidc.IdToken;

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
      ServerConfiguration serverConfiguration,
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
              serverConfiguration,
              clientConfiguration,
              new ClientCredentials());
      RefreshToken refreshToken = createRefreshToken(serverConfiguration, clientConfiguration);

      IdTokenCustomClaims idTokenCustomClaims = new IdTokenCustomClaimsBuilder().build();
      IdToken idToken =
          createIdToken(
              cibaGrant.user(),
              new Authentication(),
              cibaGrant.authorizationGrant(),
              idTokenCustomClaims,
              serverConfiguration,
              clientConfiguration);
      builder
          .add(accessToken.accessTokenEntity())
          .add(TokenType.Bearer)
          .add(new ExpiresIn(serverConfiguration.accessTokenDuration()))
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
