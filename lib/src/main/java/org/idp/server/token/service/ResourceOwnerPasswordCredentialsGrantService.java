package org.idp.server.token.service;

import java.util.UUID;
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
import org.idp.server.token.validator.ResourceOwnerPasswordGrantValidator;
import org.idp.server.token.verifier.ResourceOwnerPasswordGrantVerifier;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.extension.GrantFlow;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.ExpiresIn;
import org.idp.server.type.oauth.Scopes;
import org.idp.server.type.oauth.TokenType;
import org.idp.server.type.oidc.IdToken;

/**
 * ResourceOwnerPasswordCredentialsGrantService
 *
 * <p>The authorization server MUST:
 *
 * <p>o require client authentication for confidential clients or for any client that was issued
 * client credentials (or with other authentication requirements),
 *
 * <p>o authenticate the client if client authentication is included, and
 *
 * <p>o validate the resource owner password credentials using its existing password validation
 * algorithm.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.3.2">4.3.2. Access Token
 *     Request</a>
 */
public class ResourceOwnerPasswordCredentialsGrantService
    implements OAuthTokenCreationService,
        AccessTokenCreatable,
        IdTokenCreatable,
        RefreshTokenCreatable {
  OAuthTokenRepository oAuthTokenRepository;

  public ResourceOwnerPasswordCredentialsGrantService(OAuthTokenRepository oAuthTokenRepository) {
    this.oAuthTokenRepository = oAuthTokenRepository;
  }

  @Override
  public OAuthToken create(TokenRequestContext context) {
    ResourceOwnerPasswordGrantValidator validator =
        new ResourceOwnerPasswordGrantValidator(context);
    validator.validate();

    ServerConfiguration serverConfiguration = context.serverConfiguration();
    ClientConfiguration clientConfiguration = context.clientConfiguration();

    PasswordCredentialsGrantDelegate delegate = context.passwordCredentialsGrantDelegate();
    User user =
        delegate.findAndAuthenticate(context.tokenIssuer(), context.username(), context.password());
    Scopes scopes =
        new Scopes(clientConfiguration.filteredScope(context.scopes().toStringValues()));
    ResourceOwnerPasswordGrantVerifier verifier =
        new ResourceOwnerPasswordGrantVerifier(user, scopes);
    verifier.verify();

    ClientId clientId = clientConfiguration.clientId();

    ClaimsPayload claimsPayload = new ClaimsPayload();
    CustomProperties customProperties = context.customProperties();
    AuthorizationGrant authorizationGrant =
        new AuthorizationGrant(
            user, new Authentication(), clientId, scopes, claimsPayload, customProperties);

    AccessToken accessToken =
        createAccessToken(authorizationGrant, serverConfiguration, clientConfiguration);
    TokenResponseBuilder tokenResponseBuilder =
        new TokenResponseBuilder()
            .add(accessToken.accessTokenValue())
            .add(new ExpiresIn(serverConfiguration.accessTokenDuration()))
            .add(scopes)
            .add(TokenType.Bearer);
    if (authorizationGrant.hasOpenidScope()) {
      IdTokenCustomClaims idTokenCustomClaims = new IdTokenCustomClaimsBuilder().build();
      IdToken idToken =
          createIdToken(
              authorizationGrant.user(),
              new Authentication(),
              GrantFlow.resource_owner_password,
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
