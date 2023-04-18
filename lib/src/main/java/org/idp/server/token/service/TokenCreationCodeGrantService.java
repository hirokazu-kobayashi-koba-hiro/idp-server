package org.idp.server.token.service;

import java.util.UUID;
import org.idp.server.clientauthenticator.ClientSecretPostAuthenticator;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.oauth.grant.AuthorizationGranted;
import org.idp.server.oauth.identity.IdTokenCreatable;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.token.AccessTokenCreatable;
import org.idp.server.oauth.token.AccessTokenPayload;
import org.idp.server.token.*;
import org.idp.server.token.validator.TokenRequestCodeGrantValidator;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.IdToken;

public class TokenCreationCodeGrantService
    implements OAuthTokenCreationService,
        AccessTokenCreatable,
        RefreshTokenCreatable,
        IdTokenCreatable {

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
    if (authorizationRequest.isOidcProfile()) {
      AuthorizationCode authorizationCode = authorizationCodeGrant.authorizationCode();
      AuthorizationGranted authorizationGranted = authorizationCodeGrant.authorizationGranted();
      User user = authorizationGranted.user();
      IdToken idToken =
          createIdToken(
              authorizationRequest,
              authorizationCode,
              accessToken,
              user,
              new Authentication(),
              serverConfiguration,
              clientConfiguration);
      tokenResponseBuilder.add(idToken);
    }
    TokenResponse tokenResponse = tokenResponseBuilder.build();
    OAuthTokenIdentifier identifier = new OAuthTokenIdentifier(UUID.randomUUID().toString());
    return new OAuthToken(identifier, tokenResponse, accessTokenPayload);
  }
}
