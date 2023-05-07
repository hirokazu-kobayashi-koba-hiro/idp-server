package org.idp.server.token.service;

import java.util.UUID;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.identity.ClaimsPayload;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.token.AccessToken;
import org.idp.server.oauth.token.AccessTokenCreatable;
import org.idp.server.oauth.token.AccessTokenPayload;
import org.idp.server.oauth.token.RefreshToken;
import org.idp.server.token.*;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.token.validator.ClientCredentialsGrantValidator;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.ExpiresIn;
import org.idp.server.type.oauth.Scopes;
import org.idp.server.type.oauth.TokenType;

public class ClientCredentialsGrantService
    implements OAuthTokenCreationService, AccessTokenCreatable {
  OAuthTokenRepository oAuthTokenRepository;

  public ClientCredentialsGrantService(OAuthTokenRepository oAuthTokenRepository) {
    this.oAuthTokenRepository = oAuthTokenRepository;
  }

  @Override
  public OAuthToken create(TokenRequestContext context) {
    ClientCredentialsGrantValidator validator = new ClientCredentialsGrantValidator(context);
    validator.validate();

    ServerConfiguration serverConfiguration = context.serverConfiguration();
    ClientConfiguration clientConfiguration = context.clientConfiguration();
    User user = new User();
    ClientId clientId = clientConfiguration.clientId();
    Scopes scopes = context.scopes();
    ClaimsPayload claimsPayload = new ClaimsPayload();
    CustomProperties customProperties = context.customProperties();
    AuthorizationGrant authorizationGrant =
        new AuthorizationGrant(user, clientId, scopes, claimsPayload, customProperties);
    AccessTokenPayload accessTokenPayload =
        createAccessTokenPayload(authorizationGrant, serverConfiguration, clientConfiguration);
    AccessToken accessToken =
        createAccessToken(accessTokenPayload, serverConfiguration, clientConfiguration);
    TokenResponseBuilder tokenResponseBuilder =
        new TokenResponseBuilder()
            .add(accessToken.accessTokenValue())
            .add(new ExpiresIn(serverConfiguration.accessTokenDuration()))
            .add(TokenType.Bearer);
    TokenResponse tokenResponse = tokenResponseBuilder.build();

    OAuthTokenIdentifier identifier = new OAuthTokenIdentifier(UUID.randomUUID().toString());
    OAuthToken oAuthToken =
        new OAuthToken(
            identifier, tokenResponse, accessToken, new RefreshToken(), authorizationGrant);

    oAuthTokenRepository.register(oAuthToken);
    return oAuthToken;
  }
}
