package org.idp.server.core.handler.ciba;

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
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.clientcredentials.ClientCredentials;
import org.idp.server.core.oauth.identity.IdTokenClaims;
import org.idp.server.core.oauth.identity.IdTokenCreatable;
import org.idp.server.core.oauth.identity.IdTokenCustomClaims;
import org.idp.server.core.oauth.identity.IdTokenCustomClaimsBuilder;
import org.idp.server.core.oauth.token.*;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.OAuthTokenBuilder;
import org.idp.server.core.token.OAuthTokenIdentifier;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.core.type.extension.GrantFlow;
import org.idp.server.core.type.oauth.ExpiresIn;
import org.idp.server.core.type.oauth.TokenType;
import org.idp.server.core.type.oidc.IdToken;

// FIXME consider
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
      CibaGrant cibaGrant,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {

    BackchannelAuthenticationRequest backchannelAuthenticationRequest =
        backchannelAuthenticationRequestRepository.find(
            cibaGrant.backchannelAuthenticationRequestIdentifier());
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
              GrantFlow.ciba,
              cibaGrant.scopes(),
              new IdTokenClaims(),
              idTokenCustomClaims,
              serverConfiguration,
              clientConfiguration);
      builder
          .add(accessToken.accessTokenValue())
          .add(TokenType.Bearer)
          .add(new ExpiresIn(serverConfiguration.accessTokenDuration()))
          .add(refreshToken.refreshTokenValue())
          .add(idToken);

      ClientNotificationRequest clientNotificationRequest =
          new ClientNotificationRequest(
              clientConfiguration.backchannelClientNotificationEndpoint(),
              builder.build(),
              backchannelAuthenticationRequest.clientNotificationToken().value());
      clientNotificationGateway.notify(clientNotificationRequest);

      OAuthTokenIdentifier identifier = new OAuthTokenIdentifier(UUID.randomUUID().toString());

      AuthorizationGrantedIdentifier authorizationGrantedIdentifier =
          new AuthorizationGrantedIdentifier(UUID.randomUUID().toString());
      AuthorizationGranted authorizationGranted =
          new AuthorizationGranted(authorizationGrantedIdentifier, cibaGrant.authorizationGrant());
      authorizationGrantedRepository.register(authorizationGranted);

      OAuthToken oAuthToken =
          new OAuthTokenBuilder(identifier).add(accessToken).add(refreshToken).add(idToken).build();
      oAuthTokenRepository.register(oAuthToken);
    }
  }
}
