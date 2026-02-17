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

package org.idp.server.core.openid.oauth.verifier.extension;

import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.openid.oauth.verifier.AuthorizationRequestExtensionVerifier;
import org.idp.server.platform.date.SystemDateTime;

/**
 * PushedAuthorizationRequestVerifier
 *
 * <p>Verifies pushed authorization requests (PAR) at the authorization endpoint.
 *
 * <p>RFC 9126 Section 2.2 - The authorization server MUST validate the request_uri:
 *
 * <ul>
 *   <li>The request_uri MUST NOT be expired (lifetime is typically short, e.g., 60-90 seconds)
 *   <li>The request_uri MUST be bound to the client that created it; another client MUST NOT be
 *       able to use a request_uri that was created by a different client
 * </ul>
 */
public class PushedAuthorizationRequestVerifier implements AuthorizationRequestExtensionVerifier {

  @Override
  public boolean shouldVerify(OAuthRequestContext context) {
    return context.isPushedRequest();
  }

  @Override
  public void verify(OAuthRequestContext context) {
    verifyExpiration(context);
    verifyClientBinding(context);
  }

  /**
   * RFC 9126 Section 2.2:
   *
   * <p>"the request_uri value MUST be bound to the client that posted the authorization request"
   *
   * <p>"the authorization server MUST verify that [...] the request_uri has not expired"
   */
  private void verifyExpiration(OAuthRequestContext context) {
    if (context.authorizationRequest().isExpired(SystemDateTime.now())) {
      throw new OAuthBadRequestException(
          "invalid_request_uri", "pushed authorization request_uri has expired", context.tenant());
    }
  }

  /**
   * RFC 9126 Section 2.2:
   *
   * <p>"the request_uri value MUST be bound to the client that posted the authorization request"
   *
   * <p>This prevents a malicious client from using a request_uri that was issued to another client,
   * which could lead to authorization code injection or session fixation attacks.
   */
  private void verifyClientBinding(OAuthRequestContext context) {
    if (!context
        .authorizationRequest()
        .requestedClientId()
        .equals(context.parameters().clientId())) {
      throw new OAuthBadRequestException(
          "invalid_request_uri",
          "pushed authorization request_uri was issued to a different client",
          context.tenant());
    }
  }
}
