package org.idp.server.token.service;

import java.util.UUID;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.clientcredentials.ClientCredentials;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.token.*;
import org.idp.server.token.*;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.token.validator.RefreshTokenGrantValidator;
import org.idp.server.token.verifier.RefreshTokenVerifier;
import org.idp.server.type.oauth.RefreshTokenEntity;

public class RefreshTokenGrantService
    implements OAuthTokenCreationService, AccessTokenCreatable, RefreshTokenCreatable {

  OAuthTokenRepository oAuthTokenRepository;

  public RefreshTokenGrantService(OAuthTokenRepository oAuthTokenRepository) {
    this.oAuthTokenRepository = oAuthTokenRepository;
  }

  @Override
  public OAuthToken create(TokenRequestContext context, ClientCredentials clientCredentials) {
    RefreshTokenGrantValidator validator = new RefreshTokenGrantValidator(context);
    validator.validate();

    RefreshTokenEntity refreshTokenEntity = context.refreshToken();
    ServerConfiguration serverConfiguration = context.serverConfiguration();
    ClientConfiguration clientConfiguration = context.clientConfiguration();
    OAuthToken oAuthToken =
        oAuthTokenRepository.find(serverConfiguration.tokenIssuer(), refreshTokenEntity);

    RefreshTokenVerifier verifier = new RefreshTokenVerifier(context, oAuthToken);
    verifier.verify();
    AuthorizationGrant authorizationGrant = oAuthToken.authorizationGrant();
    AccessToken accessToken =
        createAccessToken(
            authorizationGrant, serverConfiguration, clientConfiguration, clientCredentials);
    RefreshToken refreshToken = createRefreshToken(serverConfiguration, clientConfiguration);
    OAuthTokenBuilder oAuthTokenBuilder =
        new OAuthTokenBuilder(new OAuthTokenIdentifier(UUID.randomUUID().toString()))
            .add(accessToken)
            .add(refreshToken);

    oAuthTokenRepository.delete(oAuthToken);

    OAuthToken refresh = oAuthTokenBuilder.build();
    oAuthTokenRepository.register(refresh);

    return refresh;
  }
}
