package org.idp.server.core.oauth.token;

import org.idp.server.core.type.CreatedAt;
import org.idp.server.core.type.ExpiredAt;

public class OAuthToken {
  TokenResponse tokenResponse;
  AccessTokenPayload accessTokenPayload;
  CreatedAt createdAt;
  ExpiredAt expiredAt;

  public OAuthToken() {}
}
