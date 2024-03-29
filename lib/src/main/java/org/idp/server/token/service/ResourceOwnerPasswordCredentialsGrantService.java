package org.idp.server.token.service;

import java.util.UUID;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.clientcredentials.ClientCredentials;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.grant.AuthorizationGrantBuilder;
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
import org.idp.server.type.oauth.Scopes;
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
  public OAuthToken create(TokenRequestContext context, ClientCredentials clientCredentials) {
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

    CustomProperties customProperties = context.customProperties();
    AuthorizationGrant authorizationGrant =
        new AuthorizationGrantBuilder(clientConfiguration.clientId(), scopes)
            .add(user)
            .add(customProperties)
            .build();

    AccessToken accessToken =
        createAccessToken(
            authorizationGrant, serverConfiguration, clientConfiguration, clientCredentials);
    RefreshToken refreshToken = createRefreshToken(serverConfiguration, clientConfiguration);
    OAuthTokenBuilder oAuthTokenBuilder =
        new OAuthTokenBuilder(new OAuthTokenIdentifier(UUID.randomUUID().toString()))
            .add(accessToken)
            .add(refreshToken);

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
      oAuthTokenBuilder.add(idToken);
    }
    OAuthToken oAuthToken = oAuthTokenBuilder.build();
    oAuthTokenRepository.register(oAuthToken);
    return oAuthToken;
  }
}
