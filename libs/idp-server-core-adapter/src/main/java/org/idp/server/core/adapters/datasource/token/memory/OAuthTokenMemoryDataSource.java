package org.idp.server.core.adapters.datasource.token.memory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.core.type.oauth.AccessTokenEntity;
import org.idp.server.core.type.oauth.RefreshTokenEntity;

public class OAuthTokenMemoryDataSource implements OAuthTokenRepository {

  Map<String, OAuthToken> accessTokens;
  Map<String, OAuthToken> refreshTokens;

  public OAuthTokenMemoryDataSource() {
    this.accessTokens = new HashMap<>();
    this.refreshTokens = new HashMap<>();
  }

  @Override
  public void register(OAuthToken oAuthToken) {
    registerWithAccessTokenKey(oAuthToken);
    if (oAuthToken.hasRefreshToken()) {
      registerWithRefreshTokenKey(oAuthToken);
    }
  }

  void registerWithAccessTokenKey(OAuthToken oAuthToken) {
    String key = accessTokenKey(oAuthToken.tenantIdentifier(), oAuthToken.accessTokenEntity());
    accessTokens.put(key, oAuthToken);
  }

  void registerWithRefreshTokenKey(OAuthToken oAuthToken) {
    String key = refreshTokenKey(oAuthToken.tenantIdentifier(), oAuthToken.refreshTokenEntity());
    refreshTokens.put(key, oAuthToken);
  }

  @Override
  public OAuthToken find(Tenant tenant, AccessTokenEntity accessTokenEntity) {
    String key = accessTokenKey(tenant.identifier(), accessTokenEntity);
    OAuthToken oAuthToken = accessTokens.get(key);
    if (Objects.isNull(oAuthToken)) {
      return new OAuthToken();
    }
    return oAuthToken;
  }

  @Override
  public OAuthToken find(Tenant tenant, RefreshTokenEntity refreshTokenEntity) {
    String key = refreshTokenKey(tenant.identifier(), refreshTokenEntity);
    OAuthToken oAuthToken = refreshTokens.get(key);
    if (Objects.isNull(oAuthToken)) {
      return new OAuthToken();
    }
    return oAuthToken;
  }

  @Override
  public void delete(OAuthToken oAuthToken) {
    deleteWithAccessTokenKey(oAuthToken);
    deleteWithRefreshTokenKey(oAuthToken);
  }

  void deleteWithAccessTokenKey(OAuthToken oAuthToken) {
    String key = accessTokenKey(oAuthToken.tenantIdentifier(), oAuthToken.accessTokenEntity());
    accessTokens.remove(key);
  }

  void deleteWithRefreshTokenKey(OAuthToken oAuthToken) {
    String key = refreshTokenKey(oAuthToken.tenantIdentifier(), oAuthToken.refreshTokenEntity());
    refreshTokens.remove(key);
  }

  String accessTokenKey(TenantIdentifier tenantIdentifier, AccessTokenEntity accessTokenEntity) {
    return String.format("%s%s", tenantIdentifier.value(), accessTokenEntity.value());
  }

  String refreshTokenKey(TenantIdentifier tenantIdentifier, RefreshTokenEntity refreshTokenEntity) {
    return String.format("%s%s", tenantIdentifier.value(), refreshTokenEntity.value());
  }
}
