package org.idp.server.core.oidc.token.repository;

import org.idp.server.basic.type.oauth.AccessTokenEntity;
import org.idp.server.basic.type.oauth.RefreshTokenEntity;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface OAuthTokenRepository {

  void register(Tenant tenant, OAuthToken oAuthToken);

  OAuthToken find(Tenant tenant, AccessTokenEntity accessTokenEntity);

  OAuthToken find(Tenant tenant, RefreshTokenEntity refreshTokenEntity);

  void delete(Tenant tenant, OAuthToken oAuthToken);
}
