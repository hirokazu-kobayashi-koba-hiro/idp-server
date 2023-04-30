package org.idp.server.token.service;

import java.util.UUID;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.token.*;
import org.idp.server.token.*;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.token.validator.RefreshTokenGrantValidator;
import org.idp.server.token.verifier.RefreshTokenVerifier;
import org.idp.server.type.oauth.ExpiresIn;
import org.idp.server.type.oauth.RefreshTokenValue;
import org.idp.server.type.oauth.TokenType;

public class RefreshTokenGrantService
    implements OAuthTokenCreationService, AccessTokenCreatable, RefreshTokenCreatable {

  OAuthTokenRepository oAuthTokenRepository;
  RefreshTokenGrantValidator validator;
  RefreshTokenVerifier verifier;

  public RefreshTokenGrantService(OAuthTokenRepository oAuthTokenRepository) {
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.validator = new RefreshTokenGrantValidator();
    this.verifier = new RefreshTokenVerifier();
  }

  @Override
  public OAuthToken create(TokenRequestContext context) {
    validator.validate(context);
    RefreshTokenValue refreshTokenValue = context.refreshToken();
    ServerConfiguration serverConfiguration = context.serverConfiguration();
    ClientConfiguration clientConfiguration = context.clientConfiguration();
    OAuthToken oAuthToken =
        oAuthTokenRepository.find(serverConfiguration.tokenIssuer(), refreshTokenValue);
    verifier.verify(context, oAuthToken);
    AccessTokenPayload accessTokenPayload =
        createAccessTokenPayload(
            oAuthToken.authorizationGrant(), serverConfiguration, clientConfiguration);
    AccessToken accessToken =
        createAccessToken(accessTokenPayload, serverConfiguration, clientConfiguration);
    RefreshToken refreshToken = createRefreshToken(serverConfiguration, clientConfiguration);
    OAuthTokenIdentifier identifier = new OAuthTokenIdentifier(UUID.randomUUID().toString());

    TokenResponseBuilder tokenResponseBuilder =
        new TokenResponseBuilder()
            .add(accessToken.accessTokenValue())
            .add(refreshToken.refreshTokenValue())
            .add(TokenType.Bearer)
            .add(new ExpiresIn(serverConfiguration.accessTokenDuration()));
    TokenResponse tokenResponse = tokenResponseBuilder.build();

    return new OAuthToken(
        identifier, tokenResponse, accessToken, refreshToken, oAuthToken.authorizationGrant());
  }
}
