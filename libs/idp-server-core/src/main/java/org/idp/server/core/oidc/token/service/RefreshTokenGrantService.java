package org.idp.server.core.oidc.token.service;

import java.util.UUID;
import org.idp.server.basic.type.oauth.GrantType;
import org.idp.server.basic.type.oauth.RefreshTokenEntity;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.token.*;
import org.idp.server.core.oidc.token.repository.OAuthTokenRepository;
import org.idp.server.core.oidc.token.validator.RefreshTokenGrantValidator;
import org.idp.server.core.oidc.token.verifier.RefreshTokenVerifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class RefreshTokenGrantService
    implements OAuthTokenCreationService, AccessTokenCreatable, RefreshTokenCreatable {

  OAuthTokenRepository oAuthTokenRepository;

  public RefreshTokenGrantService(OAuthTokenRepository oAuthTokenRepository) {
    this.oAuthTokenRepository = oAuthTokenRepository;
  }

  @Override
  public GrantType grantType() {
    return GrantType.refresh_token;
  }

  @Override
  public OAuthToken create(TokenRequestContext context, ClientCredentials clientCredentials) {
    RefreshTokenGrantValidator validator = new RefreshTokenGrantValidator(context);
    validator.validate();

    Tenant tenant = context.tenant();
    RefreshTokenEntity refreshTokenEntity = context.refreshToken();
    AuthorizationServerConfiguration authorizationServerConfiguration =
        context.serverConfiguration();
    ClientConfiguration clientConfiguration = context.clientConfiguration();
    OAuthToken oAuthToken = oAuthTokenRepository.find(context.tenant(), refreshTokenEntity);

    RefreshTokenVerifier verifier = new RefreshTokenVerifier(context, oAuthToken);
    verifier.verify();
    AuthorizationGrant authorizationGrant = oAuthToken.authorizationGrant();
    AccessToken accessToken =
        createAccessToken(
            authorizationGrant,
            authorizationServerConfiguration,
            clientConfiguration,
            clientCredentials);
    RefreshToken refreshToken =
        createRefreshToken(authorizationServerConfiguration, clientConfiguration);
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
