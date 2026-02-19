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
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.token.AccessToken;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.TokenRequestContext;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;
import org.idp.server.platform.date.SystemDateTime;

public class RefreshTokenVerifier {

  TokenRequestContext context;
  OAuthToken oAuthToken;
  ClientCredentials clientCredentials;

  public RefreshTokenVerifier(
      TokenRequestContext context, OAuthToken oAuthToken, ClientCredentials clientCredentials) {
    this.context = context;
    this.oAuthToken = oAuthToken;
    this.clientCredentials = clientCredentials;
  }

  public void verify() {
    throwIfNotFoundToken();
    throwExceptionIfExpiredToken();
    throwExceptionIfSenderConstrainedButNoCertificate();
  }

  void throwIfNotFoundToken() {
    if (!oAuthToken.exists()) {
      throw new TokenBadRequestException("invalid_grant", "refresh token does not exists.");
    }
    if (!oAuthToken.authorizationGrant().isGranted(context.clientIdentifier())) {
      throw new TokenBadRequestException("invalid_grant", "refresh token does not exists.");
    }
  }

  void throwExceptionIfExpiredToken() {
    LocalDateTime now = SystemDateTime.now();
    if (oAuthToken.isExpiredRefreshToken(now)) {
      throw new TokenBadRequestException("invalid_grant", "refresh token is expired");
    }
  }

  /**
   * RFC 8705 - If the original access token was sender-constrained (certificate-bound), the client
   * certificate MUST be present in the refresh token request.
   *
   * <p>This check is based on the actual token state, not server/client configuration, because a
   * token that was issued with certificate binding must always require proof of possession
   * regardless of current configuration.
   */
  void throwExceptionIfSenderConstrainedButNoCertificate() {
    AccessToken originalAccessToken = oAuthToken.accessToken();
    if (originalAccessToken.isSenderConstrained() && !clientCredentials.hasClientCertification()) {
      throw new TokenBadRequestException(
          "invalid_grant",
          "original access token is sender-constrained, client certificate MUST be present in refresh token request");
    }
  }
}
