package org.idp.server.handler.token.datasource.memory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.type.oauth.AccessTokenValue;
import org.idp.server.type.oauth.RefreshTokenValue;
import org.idp.server.type.oauth.TokenIssuer;

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
  public OAuthToken find(TokenIssuer tokenIssuer, AccessTokenValue accessTokenValue) {
    String key = accessTokenKey(tokenIssuer, accessTokenValue);
    OAuthToken oAuthToken = accessTokens.get(key);
    if (Objects.isNull(oAuthToken)) {
      return new OAuthToken();
    }
    return oAuthToken;
  }

  @Override
  public OAuthToken find(TokenIssuer tokenIssuer, RefreshTokenValue refreshTokenValue) {
    String key = refreshTokenKey(tokenIssuer, refreshTokenValue);
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

  String accessTokenKey(TokenIssuer tokenIssuer, AccessTokenValue accessTokenValue) {
    return String.format("%s%s", tokenIssuer.value(), accessTokenValue.value());
  }

  String refreshTokenKey(TokenIssuer tokenIssuer, RefreshTokenValue refreshTokenValue) {
    return String.format("%s%s", tokenIssuer.value(), refreshTokenValue.value());
  }
}
