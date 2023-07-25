package org.idp.server.token.repository;

import org.idp.server.token.OAuthToken;
import org.idp.server.type.oauth.AccessTokenEntity;
import org.idp.server.type.oauth.RefreshTokenEntity;
import org.idp.server.type.oauth.TokenIssuer;

public interface OAuthTokenRepository {

  void register(OAuthToken oAuthToken);

  OAuthToken find(TokenIssuer tokenIssuer, AccessTokenEntity accessTokenEntity);

  OAuthToken find(TokenIssuer tokenIssuer, RefreshTokenEntity refreshTokenEntity);

  void delete(OAuthToken oAuthToken);
}
