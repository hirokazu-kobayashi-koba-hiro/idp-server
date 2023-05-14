package org.idp.server.token.service;

import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.identity.*;
import org.idp.server.oauth.token.AccessToken;
import org.idp.server.oauth.token.AccessTokenCreatable;
import org.idp.server.oauth.token.RefreshToken;
import org.idp.server.oauth.token.RefreshTokenCreatable;
import org.idp.server.token.*;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.token.validator.ClientCredentialsGrantValidator;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.ExpiresIn;
import org.idp.server.type.oauth.Scopes;
import org.idp.server.type.oauth.TokenType;
import org.idp.server.type.oidc.IdToken;

import java.util.UUID;

public class ResourceOwnerPasswordCredentialsGrantService
    implements OAuthTokenCreationService, AccessTokenCreatable, IdTokenCreatable, RefreshTokenCreatable {
  OAuthTokenRepository oAuthTokenRepository;

  public ResourceOwnerPasswordCredentialsGrantService(OAuthTokenRepository oAuthTokenRepository) {
    this.oAuthTokenRepository = oAuthTokenRepository;
  }

  @Override
  public OAuthToken create(TokenRequestContext context) {
    ClientCredentialsGrantValidator validator = new ClientCredentialsGrantValidator(context);
    validator.validate();

    ServerConfiguration serverConfiguration = context.serverConfiguration();
    ClientConfiguration clientConfiguration = context.clientConfiguration();
    PasswordCredentialsGrantDelegate delegate = context.passwordCredentialsGrantDelegate();
    User user = delegate.findAndAuthenticate("", "");
    ClientId clientId = clientConfiguration.clientId();
    Scopes scopes = context.scopes();
    ClaimsPayload claimsPayload = new ClaimsPayload();
    CustomProperties customProperties = context.customProperties();
    AuthorizationGrant authorizationGrant =
        new AuthorizationGrant(user, clientId, scopes, claimsPayload, customProperties);

    AccessToken accessToken =
        createAccessToken(authorizationGrant, serverConfiguration, clientConfiguration);
    TokenResponseBuilder tokenResponseBuilder =
        new TokenResponseBuilder()
            .add(accessToken.accessTokenValue())
            .add(new ExpiresIn(serverConfiguration.accessTokenDuration()))
            .add(scopes)
            .add(TokenType.Bearer);
    if (authorizationGrant.hasOpenidScope()) {
      IdTokenCustomClaims idTokenCustomClaims =
              new IdTokenCustomClaimsBuilder()
                      .build();
      IdToken idToken =
              createIdToken(
                      authorizationGrant.user(),
                      new Authentication(),
                      scopes,
                      new IdTokenClaims(),
                      idTokenCustomClaims,
                      serverConfiguration,
                      clientConfiguration);
      tokenResponseBuilder.add(idToken);
    }
    TokenResponse tokenResponse = tokenResponseBuilder.build();


    OAuthTokenIdentifier identifier = new OAuthTokenIdentifier(UUID.randomUUID().toString());
    OAuthToken oAuthToken =
        new OAuthToken(
            identifier, tokenResponse, accessToken, new RefreshToken(), authorizationGrant);

    oAuthTokenRepository.register(oAuthToken);
    return oAuthToken;
  }
}
