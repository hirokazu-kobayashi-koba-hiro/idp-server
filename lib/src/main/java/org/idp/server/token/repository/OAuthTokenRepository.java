package org.idp.server.token.repository;

import org.idp.server.token.OAuthToken;
import org.idp.server.type.oauth.AccessTokenValue;
import org.idp.server.type.oauth.RefreshTokenValue;
import org.idp.server.type.oauth.TokenIssuer;

public interface OAuthTokenRepository {

  void register(OAuthToken oAuthToken);

  OAuthToken find(TokenIssuer tokenIssuer, AccessTokenValue accessTokenValue);

  OAuthToken find(TokenIssuer tokenIssuer, RefreshTokenValue refreshTokenValue);

  void delete(OAuthToken oAuthToken);
}
