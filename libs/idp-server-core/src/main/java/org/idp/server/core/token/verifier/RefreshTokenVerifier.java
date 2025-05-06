package org.idp.server.core.token.verifier;

import java.time.LocalDateTime;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.TokenRequestContext;
import org.idp.server.core.token.exception.TokenBadRequestException;

public class RefreshTokenVerifier {
  TokenRequestContext context;
  OAuthToken oAuthToken;

  public RefreshTokenVerifier(TokenRequestContext context, OAuthToken oAuthToken) {
    this.context = context;
    this.oAuthToken = oAuthToken;
  }

  public void verify() {
    throwINotFoundToken();
    throwExceptionIfExpiredToken();
  }

  void throwINotFoundToken() {
    if (!oAuthToken.exists()) {
      throw new TokenBadRequestException("invalid_grant", String.format("refresh token does not exists (%s)", context.refreshToken().value()));
    }
    if (!oAuthToken.authorizationGrant().isGranted(context.clientIdentifier())) {
      throw new TokenBadRequestException("invalid_grant", String.format("refresh token does not exists (%s)", context.refreshToken().value()));
    }
  }

  void throwExceptionIfExpiredToken() {
    LocalDateTime now = SystemDateTime.now();
    if (oAuthToken.isExpire(now)) {
      throw new TokenBadRequestException("invalid_grant", String.format("refresh token is expired (%s)", context.refreshToken().value()));
    }
  }
}
