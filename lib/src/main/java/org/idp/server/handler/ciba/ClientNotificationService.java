package org.idp.server.handler.ciba;

import java.util.UUID;
import org.idp.server.ciba.clientnotification.ClientNotificationRequest;
import org.idp.server.ciba.clientnotification.ClientNotificationRequestBodyBuilder;
import org.idp.server.ciba.gateway.ClientNotificationGateway;
import org.idp.server.ciba.grant.CibaGrant;
import org.idp.server.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.grantmangment.AuthorizationGranted;
import org.idp.server.grantmangment.AuthorizationGrantedIdentifier;
import org.idp.server.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.identity.IdTokenClaims;
import org.idp.server.oauth.identity.IdTokenCreatable;
import org.idp.server.oauth.identity.IdTokenCustomClaims;
import org.idp.server.oauth.identity.IdTokenCustomClaimsBuilder;
import org.idp.server.oauth.token.*;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.OAuthTokenBuilder;
import org.idp.server.token.OAuthTokenIdentifier;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.type.extension.GrantFlow;
import org.idp.server.type.oauth.ExpiresIn;
import org.idp.server.type.oauth.TokenType;
import org.idp.server.type.oidc.IdToken;

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
              cibaGrant.authorizationGrant(), serverConfiguration, clientConfiguration);
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
