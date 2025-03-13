package org.idp.server.core.adapters.datasource.token.memory;

import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.core.type.oauth.AccessTokenEntity;
import org.idp.server.core.type.oauth.RefreshTokenEntity;
import org.idp.server.core.type.oauth.TokenIssuer;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
    String key = accessTokenKey(oAuthToken.tokenIssuer(), oAuthToken.accessTokenValue());
    accessTokens.put(key, oAuthToken);
  }

  void registerWithRefreshTokenKey(OAuthToken oAuthToken) {
    String key = refreshTokenKey(oAuthToken.tokenIssuer(), oAuthToken.refreshTokenValue());
    refreshTokens.put(key, oAuthToken);
  }

  @Override
  public OAuthToken find(TokenIssuer tokenIssuer, AccessTokenEntity accessTokenEntity) {
    String key = accessTokenKey(tokenIssuer, accessTokenEntity);
    OAuthToken oAuthToken = accessTokens.get(key);
    if (Objects.isNull(oAuthToken)) {
      return new OAuthToken();
    }
    return oAuthToken;
  }

  @Override
  public OAuthToken find(TokenIssuer tokenIssuer, RefreshTokenEntity refreshTokenEntity) {
    String key = refreshTokenKey(tokenIssuer, refreshTokenEntity);
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
    String key = accessTokenKey(oAuthToken.tokenIssuer(), oAuthToken.accessTokenValue());
    accessTokens.remove(key);
  }

  void deleteWithRefreshTokenKey(OAuthToken oAuthToken) {
    String key = refreshTokenKey(oAuthToken.tokenIssuer(), oAuthToken.refreshTokenValue());
    refreshTokens.remove(key);
  }

  String accessTokenKey(TokenIssuer tokenIssuer, AccessTokenEntity accessTokenEntity) {
    return String.format("%s%s", tokenIssuer.value(), accessTokenEntity.value());
  }

  String refreshTokenKey(TokenIssuer tokenIssuer, RefreshTokenEntity refreshTokenEntity) {
    return String.format("%s%s", tokenIssuer.value(), refreshTokenEntity.value());
  }
}
