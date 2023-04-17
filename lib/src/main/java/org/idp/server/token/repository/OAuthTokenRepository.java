package org.idp.server.token.repository;

import org.idp.server.token.OAuthToken;
import org.idp.server.type.oauth.AccessToken;
import org.idp.server.type.oauth.RefreshToken;
import org.idp.server.type.oauth.TokenIssuer;

public interface OAuthTokenRepository {

  void register(OAuthToken oAuthToken);

  OAuthToken find(TokenIssuer tokenIssuer, AccessToken accessToken);

  OAuthToken find(TokenIssuer tokenIssuer, RefreshToken refreshToken);

  void delete(OAuthToken oAuthToken);
}
