package org.idp.server.handler.token.datasource;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.type.oauth.AccessToken;
import org.idp.server.type.oauth.RefreshToken;
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
    registerWithRefreshTokenKey(oAuthToken);
  }

  void registerWithAccessTokenKey(OAuthToken oAuthToken) {
    String key = accessTokenKey(oAuthToken.tokenIssuer(), oAuthToken.accessToken());
    accessTokens.put(key, oAuthToken);
  }

  void registerWithRefreshTokenKey(OAuthToken oAuthToken) {
    String key = refreshTokenKey(oAuthToken.tokenIssuer(), oAuthToken.refreshToken());
    refreshTokens.put(key, oAuthToken);
  }

  @Override
  public OAuthToken find(TokenIssuer tokenIssuer, AccessToken accessToken) {
    String key = accessTokenKey(tokenIssuer, accessToken);
    OAuthToken oAuthToken = accessTokens.get(key);
    if (Objects.isNull(oAuthToken)) {
      return new OAuthToken();
    }
    return oAuthToken;
  }

  @Override
  public OAuthToken find(TokenIssuer tokenIssuer, RefreshToken refreshToken) {
    String key = refreshTokenKey(tokenIssuer, refreshToken);
    OAuthToken oAuthToken = refreshTokens.get(key);
    if (Objects.isNull(oAuthToken)) {
      return new OAuthToken();
    }
    return oAuthToken;
  }

  String accessTokenKey(TokenIssuer tokenIssuer, AccessToken accessToken) {
    return String.format("%s%s", tokenIssuer.value(), accessToken.value());
  }

  String refreshTokenKey(TokenIssuer tokenIssuer, RefreshToken refreshToken) {
    return String.format("%s%s", tokenIssuer.value(), refreshToken.value());
  }
}
