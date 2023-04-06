package org.idp.server.service;

import org.idp.server.core.authenticator.ClientSecretPostAuthenticator;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.repository.AuthorizationRequestRepository;
import org.idp.server.core.token.*;
import org.idp.server.core.token.validator.TokenRequestCodeGrantValidator;
import org.idp.server.core.type.*;

public class TokenCreationCodeGrantService
    implements OAuthTokenCreationService,
        AccessTokenPayloadCreatable,
        AccessTokenCreatable,
        RefreshTokenCreatable {

  AuthorizationRequestRepository authorizationRequestRepository;
  AuthorizationCodeGrantRepository authorizationCodeGrantRepository;
  TokenRequestCodeGrantValidator validator;

  public TokenCreationCodeGrantService(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.authorizationCodeGrantRepository = authorizationCodeGrantRepository;
    this.validator = new TokenRequestCodeGrantValidator();
  }

  @Override
  public OAuthToken create(TokenRequestContext tokenRequestContext) {
    validator.validate(tokenRequestContext);

    AuthorizationCode code = tokenRequestContext.code();
    AuthorizationCodeGrant authorizationCodeGrant = authorizationCodeGrantRepository.find(code);
    AuthorizationRequest authorizationRequest =
        authorizationRequestRepository.get(authorizationCodeGrant.authorizationRequestIdentifier());

    // FIXME consider various client authentication type
    ClientSecretPostAuthenticator authenticator = new ClientSecretPostAuthenticator();
    authenticator.authenticate(tokenRequestContext);
    ServerConfiguration serverConfiguration = tokenRequestContext.serverConfiguration();
    ClientConfiguration clientConfiguration = tokenRequestContext.clientConfiguration();
    AccessTokenPayload accessTokenPayload =
        createAccessTokenPayload(
            authorizationCodeGrant.authorizationGranted(),
            serverConfiguration,
            clientConfiguration);
    AccessToken accessToken =
        createAccessToken(accessTokenPayload, serverConfiguration, clientConfiguration);
    RefreshToken refreshToken = createRefreshToken();
    TokenResponseBuilder tokenResponseBuilder =
        new TokenResponseBuilder()
            .add(accessToken)
            .add(TokenType.Bearer)
            .add(new ExpiresIn(serverConfiguration.accessTokenDuration()))
            .add(refreshToken);
    TokenResponse tokenResponse = tokenResponseBuilder.build();
    return new OAuthToken(tokenResponse, accessTokenPayload);
  }
}
