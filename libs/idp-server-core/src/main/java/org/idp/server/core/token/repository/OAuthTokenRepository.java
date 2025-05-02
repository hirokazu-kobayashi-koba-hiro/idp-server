package org.idp.server.core.token.repository;

import org.idp.server.basic.type.oauth.AccessTokenEntity;
import org.idp.server.basic.type.oauth.RefreshTokenEntity;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.token.OAuthToken;

public interface OAuthTokenRepository {

  void register(Tenant tenant, OAuthToken oAuthToken);

  OAuthToken find(Tenant tenant, AccessTokenEntity accessTokenEntity);

  OAuthToken find(Tenant tenant, RefreshTokenEntity refreshTokenEntity);

  void delete(Tenant tenant, OAuthToken oAuthToken);
}
