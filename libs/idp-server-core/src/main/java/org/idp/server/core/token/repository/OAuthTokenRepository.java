package org.idp.server.core.token.repository;

import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.type.oauth.AccessTokenEntity;
import org.idp.server.core.type.oauth.RefreshTokenEntity;

public interface OAuthTokenRepository {

  void register(Tenant tenant, OAuthToken oAuthToken);

  OAuthToken find(Tenant tenant, AccessTokenEntity accessTokenEntity);

  OAuthToken find(Tenant tenant, RefreshTokenEntity refreshTokenEntity);

  void delete(Tenant tenant, OAuthToken oAuthToken);
}
