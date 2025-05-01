package org.idp.server.core.token.service;

import java.util.UUID;
import org.idp.server.core.oidc.configuration.ClientConfiguration;
import org.idp.server.core.oidc.configuration.ServerConfiguration;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.token.*;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.token.*;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.core.token.validator.RefreshTokenGrantValidator;
import org.idp.server.core.token.verifier.RefreshTokenVerifier;
import org.idp.server.basic.type.oauth.RefreshTokenEntity;

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

    Tenant tenant = context.tenant();
    RefreshTokenEntity refreshTokenEntity = context.refreshToken();
    ServerConfiguration serverConfiguration = context.serverConfiguration();
    ClientConfiguration clientConfiguration = context.clientConfiguration();
    OAuthToken oAuthToken = oAuthTokenRepository.find(context.tenant(), refreshTokenEntity);

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

    oAuthTokenRepository.delete(tenant, oAuthToken);

    OAuthToken refresh = oAuthTokenBuilder.build();
    oAuthTokenRepository.register(tenant, refresh);

    return refresh;
  }
}
