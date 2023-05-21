package org.idp.server.token.service;

import java.util.UUID;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.identity.ClaimsPayload;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.token.AccessToken;
import org.idp.server.oauth.token.AccessTokenCreatable;
import org.idp.server.oauth.token.RefreshToken;
import org.idp.server.token.*;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.token.validator.ClientCredentialsGrantValidator;
import org.idp.server.token.verifier.ClientCredentialsGrantVerifier;
import org.idp.server.type.extension.CustomProperties;
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

    Scopes scopes =
        new Scopes(clientConfiguration.filteredScope(context.scopes().toStringValues()));
    ClientCredentialsGrantVerifier verifier = new ClientCredentialsGrantVerifier(scopes);
    verifier.verify();

    CustomProperties customProperties = context.customProperties();
    AuthorizationGrant authorizationGrant =
        new AuthorizationGrant(
            new User(),
            new Authentication(),
            clientConfiguration.clientId(),
            scopes,
            new ClaimsPayload(),
            customProperties);

    AccessToken accessToken =
        createAccessToken(authorizationGrant, serverConfiguration, clientConfiguration);
    TokenResponseBuilder tokenResponseBuilder =
        new TokenResponseBuilder()
            .add(accessToken.accessTokenValue())
            .add(new ExpiresIn(serverConfiguration.accessTokenDuration()))
            .add(TokenType.Bearer)
            .add(scopes);
    TokenResponse tokenResponse = tokenResponseBuilder.build();

    OAuthTokenIdentifier identifier = new OAuthTokenIdentifier(UUID.randomUUID().toString());
    OAuthToken oAuthToken =
        new OAuthToken(
            identifier, tokenResponse, accessToken, new RefreshToken(), authorizationGrant);

    oAuthTokenRepository.register(oAuthToken);
    return oAuthToken;
  }
}
