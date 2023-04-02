package org.idp.server.core.oauth.token;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.type.AccessToken;
import org.idp.server.core.type.ExpiresIn;
import org.idp.server.core.type.RefreshToken;
import org.idp.server.core.type.TokenType;

public class TokenResponseBuilder {
  AccessToken accessToken;
  TokenType tokenType;
  ExpiresIn expiresIn;
  RefreshToken refreshToken;
  Map<String, Object> values = new HashMap<>();
}
