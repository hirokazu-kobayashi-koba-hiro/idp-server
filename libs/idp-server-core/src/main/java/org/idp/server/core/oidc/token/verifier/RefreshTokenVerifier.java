/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.token.verifier;

import java.time.LocalDateTime;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.core.oidc.token.TokenRequestContext;
import org.idp.server.core.oidc.token.exception.TokenBadRequestException;
import org.idp.server.platform.date.SystemDateTime;

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
      throw new TokenBadRequestException(
          "invalid_grant",
          String.format("refresh token does not exists (%s)", context.refreshToken().value()));
    }
    if (!oAuthToken.authorizationGrant().isGranted(context.clientIdentifier())) {
      throw new TokenBadRequestException(
          "invalid_grant",
          String.format("refresh token does not exists (%s)", context.refreshToken().value()));
    }
  }

  void throwExceptionIfExpiredToken() {
    LocalDateTime now = SystemDateTime.now();
    if (oAuthToken.isExpire(now)) {
      throw new TokenBadRequestException(
          "invalid_grant",
          String.format("refresh token is expired (%s)", context.refreshToken().value()));
    }
  }
}
