package org.idp.server.core.token.repository;

import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.type.oauth.AccessTokenEntity;
import org.idp.server.core.type.oauth.RefreshTokenEntity;
import org.idp.server.core.type.oauth.TokenIssuer;

public interface OAuthTokenRepository {

  void register(OAuthToken oAuthToken);

  OAuthToken find(TokenIssuer tokenIssuer, AccessTokenEntity accessTokenEntity);

  OAuthToken find(TokenIssuer tokenIssuer, RefreshTokenEntity refreshTokenEntity);

  void delete(OAuthToken oAuthToken);
}
