package org.idp.server.token.verifier;

import java.time.LocalDateTime;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.TokenRequestContext;
import org.idp.server.token.exception.TokenBadRequestException;

public class RefreshTokenVerifier {
  TokenRequestContext context;
  OAuthToken oAuthToken;

  public RefreshTokenVerifier(TokenRequestContext context, OAuthToken oAuthToken) {
    this.context = context;
    this.oAuthToken = oAuthToken;
  }

  public void verify() {
    throwINotFoundToken();
    throwIfExpiredToken();
  }

  void throwINotFoundToken() {
    if (!oAuthToken.exists()) {
      throw new TokenBadRequestException(
          "invalid_grant",
          String.format("refresh token does not exists (%s)", context.refreshToken().value()));
    }
  }

  void throwIfExpiredToken() {
    LocalDateTime now = SystemDateTime.now();
    if (oAuthToken.isExpire(now)) {
      throw new TokenBadRequestException(
          "invalid_grant",
          String.format("refresh token is expired (%s)", context.refreshToken().value()));
    }
  }
}
