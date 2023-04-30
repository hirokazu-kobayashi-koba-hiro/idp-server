package org.idp.server.token.verifier;

import java.time.LocalDateTime;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.TokenRequestContext;
import org.idp.server.token.exception.TokenInvalidGrantException;

public class RefreshTokenVerifier {

  public void verify(TokenRequestContext context, OAuthToken oAuthToken) {
    throwINotFoundToken(context, oAuthToken);
    throwIfExpiredToken(context, oAuthToken);
  }

  void throwINotFoundToken(TokenRequestContext context, OAuthToken oAuthToken) {
    if (!oAuthToken.exists()) {
      throw new TokenInvalidGrantException(
          "invalid_grant",
          String.format("refresh token does not exists (%s)", context.refreshToken().value()));
    }
  }

  void throwIfExpiredToken(TokenRequestContext context, OAuthToken oAuthToken) {
    LocalDateTime now = SystemDateTime.now();
    if (oAuthToken.isExpire(now)) {
      throw new TokenInvalidGrantException(
          "invalid_grant",
          String.format("refresh token is expired (%s)", context.refreshToken().value()));
    }
  }
}
