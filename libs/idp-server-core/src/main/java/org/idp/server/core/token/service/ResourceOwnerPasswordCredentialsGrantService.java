package org.idp.server.core.token.service;

import java.util.UUID;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.clientcredentials.ClientCredentials;
import org.idp.server.core.oauth.grant.AuthorizationGrant;
import org.idp.server.core.oauth.grant.AuthorizationGrantBuilder;
import org.idp.server.core.oauth.identity.*;
import org.idp.server.core.oauth.token.AccessToken;
import org.idp.server.core.oauth.token.AccessTokenCreatable;
import org.idp.server.core.oauth.token.RefreshToken;
import org.idp.server.core.oauth.token.RefreshTokenCreatable;
import org.idp.server.core.token.*;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.core.token.validator.ResourceOwnerPasswordGrantValidator;
import org.idp.server.core.token.verifier.ResourceOwnerPasswordGrantVerifier;
import org.idp.server.core.type.extension.CustomProperties;
import org.idp.server.core.type.oauth.Scopes;
import org.idp.server.core.type.oidc.IdToken;

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
        delegate.findAndAuthenticate(context.tenant(), context.username(), context.password());
    Scopes scopes =
        new Scopes(clientConfiguration.filteredScope(context.scopes().toStringValues()));
    ResourceOwnerPasswordGrantVerifier verifier =
        new ResourceOwnerPasswordGrantVerifier(user, scopes);
    verifier.verify();

    CustomProperties customProperties = context.customProperties();
    AuthorizationGrant authorizationGrant =
        new AuthorizationGrantBuilder(
                context.tenantIdentifier(), context.requestedClientId(), scopes)
            .add(user)
            .add(clientConfiguration.client())
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
              authorizationGrant,
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
