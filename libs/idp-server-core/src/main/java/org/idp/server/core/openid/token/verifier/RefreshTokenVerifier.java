/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.openid.token.verifier;

import java.time.LocalDateTime;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.TokenRequestContext;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;
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
    if (oAuthToken.isExpiredRefreshToken(now)) {
      throw new TokenBadRequestException(
          "invalid_grant",
          String.format("refresh token is expired (%s)", context.refreshToken().value()));
    }
  }
}
