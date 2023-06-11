package org.idp.server.token.service;

import java.util.UUID;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.grant.AuthorizationGrant;
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

  public RefreshTokenGrantService(OAuthTokenRepository oAuthTokenRepository) {
    this.oAuthTokenRepository = oAuthTokenRepository;
  }

  @Override
  public OAuthToken create(TokenRequestContext context) {
    RefreshTokenGrantValidator validator = new RefreshTokenGrantValidator(context);
    validator.validate();

    RefreshTokenValue refreshTokenValue = context.refreshToken();
    ServerConfiguration serverConfiguration = context.serverConfiguration();
    ClientConfiguration clientConfiguration = context.clientConfiguration();
    OAuthToken oAuthToken =
        oAuthTokenRepository.find(serverConfiguration.tokenIssuer(), refreshTokenValue);

    RefreshTokenVerifier verifier = new RefreshTokenVerifier(context, oAuthToken);
    verifier.verify();
    AuthorizationGrant authorizationGrant = oAuthToken.authorizationGrant();
    AccessToken accessToken =
        createAccessToken(authorizationGrant, serverConfiguration, clientConfiguration);
    RefreshToken refreshToken = createRefreshToken(serverConfiguration, clientConfiguration);
    OAuthTokenIdentifier identifier = new OAuthTokenIdentifier(UUID.randomUUID().toString());

    TokenResponseBuilder tokenResponseBuilder =
        new TokenResponseBuilder()
            .add(accessToken.accessTokenValue())
            .add(refreshToken.refreshTokenValue())
            .add(TokenType.Bearer)
            .add(new ExpiresIn(serverConfiguration.accessTokenDuration()))
            .add(oAuthToken.authorizationGrant().scopes());
    if (authorizationGrant.hasAuthorizationDetails()) {
      tokenResponseBuilder.add(authorizationGrant.authorizationDetails());
    }
    TokenResponse tokenResponse = tokenResponseBuilder.build();

    oAuthTokenRepository.delete(oAuthToken);

    OAuthToken refresh =
        new OAuthToken(
            identifier, tokenResponse, accessToken, refreshToken, oAuthToken.authorizationGrant());
    oAuthTokenRepository.register(refresh);

    return refresh;
  }
}
